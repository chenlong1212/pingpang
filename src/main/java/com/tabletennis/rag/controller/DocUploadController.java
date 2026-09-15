package com.tabletennis.rag.controller;

import com.tabletennis.rag.entity.TtKnowledgeDoc;
import com.tabletennis.rag.rag.HybridRAGService;
import com.tabletennis.rag.repository.TtKnowledgeDocRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 知识库文档上传：
 * - /upload     文件上传（Tika 自动解析 txt/md/pdf/word 等文本类文档）→ 切片向量化 → ES 入库
 * - /uploadText 手动文本录入 → 切片向量化 → ES 入库
 * （同步版实现，Kafka 异步流水线为简历描述项，后续可替换）
 */
@Slf4j
@RestController
@RequestMapping("/api/doc")
@RequiredArgsConstructor
public class DocUploadController {
    private final HybridRAGService hybridRAGService;
    private final TtKnowledgeDocRepository docRepo;

    private final AutoDetectParser tikaParser = new AutoDetectParser();

    @PostMapping("/upload")
    public String uploadDoc(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "fail: 文件为空";
        }
        String docId = UUID.randomUUID().toString();
        String fileName = file.getOriginalFilename();
        String ext = fileName == null ? "unknown"
                : fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1) : "unknown";

        TtKnowledgeDoc meta = newMeta(docId, fileName, ext, "upload");
        try {
            // Tika 自动检测并解析：txt/md/pdf/docx 等文本类文档
            String content = parseToText(file);
            if (content == null || content.isBlank()) {
                throw new RuntimeException("未能从文件中提取到文本内容（扫描版 PDF 需 OCR，暂不支持）");
            }
            int chunkCount = hybridRAGService.insertDoc(docId, fileName, content, "upload");
            meta.setStatus(2);
            meta.setChunkCount(chunkCount);
            docRepo.save(meta);
            return "success: 已入库，切片数=" + chunkCount;
        } catch (Exception e) {
            log.error("文档上传失败", e);
            meta.setStatus(3);
            meta.setFailMsg(e.getMessage());
            docRepo.save(meta);
            return "fail: " + e.getMessage();
        }
    }

    @PostMapping("/uploadText")
    public String uploadText(@RequestBody Map<String, String> body) {
        String title = body.get("title");
        String content = body.get("content");
        if (title == null || title.isBlank()) {
            return "fail: 请填写标题";
        }
        if (content == null || content.isBlank()) {
            return "fail: 内容不能为空";
        }
        String docId = UUID.randomUUID().toString();
        TtKnowledgeDoc meta = newMeta(docId, title, "text", "manual");
        try {
            int chunkCount = hybridRAGService.insertDoc(docId, title, content, "manual");
            meta.setStatus(2);
            meta.setChunkCount(chunkCount);
            docRepo.save(meta);
            return "success: 已入库，切片数=" + chunkCount;
        } catch (Exception e) {
            log.error("文本入库失败", e);
            meta.setStatus(3);
            meta.setFailMsg(e.getMessage());
            docRepo.save(meta);
            return "fail: " + e.getMessage();
        }
    }

    @GetMapping("/list")
    public List<TtKnowledgeDoc> list() {
        return docRepo.findAll();
    }

    private TtKnowledgeDoc newMeta(String docId, String name, String type, String source) {
        TtKnowledgeDoc meta = new TtKnowledgeDoc();
        meta.setDocId(docId);
        meta.setDocName(name);
        meta.setDocType(type);
        meta.setStatus(1);
        meta.setEsIndexName("table_tennis_knowledge");
        return docRepo.save(meta);
    }

    private String parseToText(MultipartFile file) throws Exception {
        BodyContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, file.getOriginalFilename());
        try (var is = file.getInputStream()) {
            tikaParser.parse(is, handler, metadata, new ParseContext());
        }
        return handler.toString();
    }
}
