package com.tabletennis.rag.controller;

import com.tabletennis.rag.entity.TtKnowledgeDoc;
import com.tabletennis.rag.kafka.DocUploadMessage;
import com.tabletennis.rag.kafka.DocUploadProducer;
import com.tabletennis.rag.rag.HybridRAGService;
import com.tabletennis.rag.rag.KnowledgeChunk;
import com.tabletennis.rag.repository.TtKnowledgeDocRepository;
import com.tabletennis.rag.service.DocParserService;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 知识库文档上传，支持两种处理模式（前端可切换对比）：
 *
 * 1. 同步模式（sync=true）：接口内直接 解析→切片→向量化→索引，返回时已入库
 * 2. Kafka 异步模式（sync=false，默认）：
 *    落盘/文本 → 元数据(处理中) → 发 doc-upload topic
 *    → 消费端解析/切片/向量化/索引；失败重试 3 次，仍失败进 doc-upload-dlq 死信队列
 */
@Slf4j
@RestController
@RequestMapping("/api/doc")
@RequiredArgsConstructor
public class DocUploadController {
    private static final String UPLOAD_DIR = "data/uploads";
    private static final String ES_INDEX = "table_tennis_knowledge";

    private final DocUploadProducer producer;
    private final TtKnowledgeDocRepository docRepo;
    private final DocParserService docParserService;
    private final HybridRAGService hybridRAGService;
    private final ElasticsearchClient esClient;

    @PostMapping("/upload")
    public String uploadDoc(@RequestParam("file") MultipartFile file,
                            @RequestParam(value = "sync", defaultValue = "false") boolean sync) {
        if (file == null || file.isEmpty()) {
            return "fail: 文件为空";
        }
        String docId = UUID.randomUUID().toString();
        String fileName = file.getOriginalFilename();
        String ext = fileName == null ? "unknown"
                : fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1) : "unknown";
        try {
            // 1. 文件落盘（异步模式由消费端解析，同步模式此处直接解析）
            Path targetDir = Path.of(UPLOAD_DIR, docId);
            Files.createDirectories(targetDir);
            Path targetFile = targetDir.resolve(sanitize(fileName));
            file.transferTo(targetFile);

            // 2. 元数据：处理中
            TtKnowledgeDoc meta = newMeta(docId, fileName, ext, targetFile.toString());
            meta.setStatus(1);
            docRepo.save(meta);

            if (sync) {
                // 同步模式：直接解析→切片→向量化→ES 索引
                String content = docParserService.parseFile(targetFile.toString());
                if (content == null || content.isBlank()) {
                    throw new RuntimeException("未能从文件中提取到文本内容");
                }
                int chunkCount = hybridRAGService.insertDoc(docId, fileName, content, "upload");
                meta.setStatus(2);
                meta.setChunkCount(chunkCount);
                docRepo.save(meta);
                return "success: 已同步入库，切片数=" + chunkCount;
            }

            // 3. Kafka 异步流水线
            DocUploadMessage message = new DocUploadMessage();
            message.setDocId(docId);
            message.setFileName(fileName);
            message.setFilePath(targetFile.toString());
            message.setSource("upload");
            producer.send(message);

            return "success: 已提交异步处理，切片与向量化后台进行中…";
        } catch (Exception e) {
            log.error("文件处理失败", e);
            docRepo.findByDocId(docId).ifPresent(m -> {
                m.setStatus(3);
                m.setFailMsg(e.getMessage());
                docRepo.save(m);
            });
            return "fail: " + e.getMessage();
        }
    }

    @PostMapping("/uploadText")
    public String uploadText(@RequestBody Map<String, String> body) {
        String title = body.get("title");
        String content = body.get("content");
        boolean sync = "true".equalsIgnoreCase(body.getOrDefault("sync", "false"));
        if (title == null || title.isBlank()) {
            return "fail: 请填写标题";
        }
        if (content == null || content.isBlank()) {
            return "fail: 内容不能为空";
        }
        String docId = UUID.randomUUID().toString();
        TtKnowledgeDoc meta = newMeta(docId, title, "text", null);
        meta.setStatus(1);
        docRepo.save(meta);

        if (sync) {
            // 同步模式：直接切片→向量化→ES 索引
            try {
                int chunkCount = hybridRAGService.insertDoc(docId, title, content, "manual");
                meta.setStatus(2);
                meta.setChunkCount(chunkCount);
                docRepo.save(meta);
                return "success: 已同步入库，切片数=" + chunkCount;
            } catch (Exception e) {
                log.error("文本处理失败", e);
                meta.setStatus(3);
                meta.setFailMsg(e.getMessage());
                docRepo.save(meta);
                return "fail: " + e.getMessage();
            }
        }

        // Kafka 异步流水线
        DocUploadMessage message = new DocUploadMessage();
        message.setDocId(docId);
        message.setFileName(title);
        message.setContent(content);
        message.setSource("manual");
        producer.send(message);

        return "success: 已提交异步处理，切片与向量化后台进行中…";
    }

    @GetMapping("/list")
    public List<TtKnowledgeDoc> list() {
        return docRepo.findAll();
    }

    /**
     * 删除文档：清理 ES 切片 + MySQL 元数据 + 本地落盘文件
     */
    @DeleteMapping("/{docId}")
    public String deleteDoc(@PathVariable String docId) {
        // 1. 删除 ES 中该文档的所有切片（delete_by_query）
        try {
            co.elastic.clients.elasticsearch.core.DeleteByQueryResponse resp = esClient.deleteByQuery(d -> d
                    .index("table_tennis_knowledge")
                    .query(q -> q.term(t -> t.field("docId").value(docId))));
            log.info("ES 删除切片 docId={} deleted={}", docId, resp.deleted());
        } catch (Exception e) {
            log.warn("ES 删除切片失败 docId={}: {}", docId, e.getMessage());
        }

        // 2. 删除 MySQL 元数据
        docRepo.findByDocId(docId).ifPresent(meta -> {
            // 3. 删除本地落盘文件（仅限项目自己的上传目录）
            if (meta.getDocPath() != null && meta.getDocPath().contains(UPLOAD_DIR)) {
                try {
                    Path parent = Path.of(meta.getDocPath()).getParent();
                    if (parent != null && parent.getFileName() != null
                            && parent.getFileName().toString().equals(docId)) {
                        deleteRecursively(parent);
                    }
                } catch (Exception e) {
                    log.warn("删除落盘文件失败 docId={}: {}", docId, e.getMessage());
                }
            }
            docRepo.delete(meta);
        });
        return "success: 文档已删除";
    }

    private void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("删除文件失败 {}", p);
                }
            });
        }
    }

    /**
     * 文档详情：元信息 + ES 中该文档的所有切片内容（按 chunkIndex 排序），用于前端可视化查看
     */
    @GetMapping("/detail")
    public Map<String, Object> detail(@RequestParam String docId) {
        Map<String, Object> result = new java.util.HashMap<>();
        docRepo.findByDocId(docId).ifPresent(meta -> {
            result.put("docId", meta.getDocId());
            result.put("docName", meta.getDocName());
            result.put("docType", meta.getDocType());
            result.put("status", meta.getStatus());
            result.put("chunkCount", meta.getChunkCount());
            result.put("failMsg", meta.getFailMsg());
        });
        List<Map<String, Object>> chunks = new java.util.ArrayList<>();
        try {
            co.elastic.clients.elasticsearch.core.SearchResponse<KnowledgeChunk> resp = esClient.search(s -> s
                            .index("table_tennis_knowledge")
                            .query(q -> q.term(t -> t.field("docId").value(docId)))
                            .sort(so -> so.field(f -> f.field("chunkIndex")))
                            .size(1000),
                    KnowledgeChunk.class);
            resp.hits().hits().forEach(hit -> {
                KnowledgeChunk c = hit.source();
                if (c == null) return;
                Map<String, Object> item = new java.util.HashMap<>();
                item.put("chunkIndex", c.getChunkIndex());
                item.put("title", c.getTitle());
                item.put("content", c.getContent());
                item.put("source", c.getSource());
                chunks.add(item);
            });
        } catch (Exception e) {
            log.warn("查询文档切片失败 docId={}: {}", docId, e.getMessage());
        }
        result.put("chunks", chunks);
        return result;
    }

    private TtKnowledgeDoc newMeta(String docId, String name, String type, String path) {
        TtKnowledgeDoc meta = new TtKnowledgeDoc();
        meta.setDocId(docId);
        meta.setDocName(name);
        meta.setDocType(type);
        meta.setDocPath(path);
        meta.setEsIndexName(ES_INDEX);
        return meta;
    }

    private String sanitize(String name) {
        return name == null ? "file" : name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
