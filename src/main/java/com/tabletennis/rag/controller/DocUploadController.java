package com.tabletennis.rag.controller;

import com.tabletennis.rag.entity.TtKnowledgeDoc;
import com.tabletennis.rag.kafka.DocUploadMessage;
import com.tabletennis.rag.kafka.DocUploadProducer;
import com.tabletennis.rag.repository.TtKnowledgeDocRepository;
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
 * 知识库文档上传（Kafka 两阶段异步流水线）：
 * - /upload     文件上传：落盘 → 元数据(处理中) → 发 doc-upload → 消费端解析/切片/向量化/索引
 * - /uploadText 文本录入：元数据(处理中) → 发 doc-upload → 消费端切片/向量化/索引
 * 消费失败自动重试 3 次，仍失败进入 doc-upload-dlq 死信队列并标记失败
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

    @PostMapping("/upload")
    public String uploadDoc(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "fail: 文件为空";
        }
        String docId = UUID.randomUUID().toString();
        String fileName = file.getOriginalFilename();
        String ext = fileName == null ? "unknown"
                : fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1) : "unknown";
        try {
            // 1. 文件落盘（消费端解析）
            Path targetDir = Path.of(UPLOAD_DIR, docId);
            Files.createDirectories(targetDir);
            Path targetFile = targetDir.resolve(sanitize(fileName));
            file.transferTo(targetFile);

            // 2. 元数据：处理中
            TtKnowledgeDoc meta = newMeta(docId, fileName, ext, targetFile.toString());
            meta.setStatus(1);
            docRepo.save(meta);

            // 3. 提交 Kafka 异步流水线
            DocUploadMessage message = new DocUploadMessage();
            message.setDocId(docId);
            message.setFileName(fileName);
            message.setFilePath(targetFile.toString());
            message.setSource("upload");
            producer.send(message);

            return "success: 已提交异步处理，切片与向量化后台进行中…";
        } catch (IOException e) {
            log.error("文件上传失败", e);
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
        TtKnowledgeDoc meta = newMeta(docId, title, "text", null);
        meta.setStatus(1);
        docRepo.save(meta);

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
