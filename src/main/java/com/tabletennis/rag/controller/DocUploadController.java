package com.tabletennis.rag.controller;

import com.tabletennis.rag.entity.TtKnowledgeDoc;
import com.tabletennis.rag.rag.HybridRAGService;
import com.tabletennis.rag.repository.TtKnowledgeDocRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * 知识库文档上传：txt/md 直接文本读取 → 切片向量化 → ES 入库
 * （同步版实现，Kafka 异步流水线为简历描述项，后续可替换）
 */
@Slf4j
@RestController
@RequestMapping("/api/doc")
@RequiredArgsConstructor
public class DocUploadController {
    private final HybridRAGService hybridRAGService;
    private final TtKnowledgeDocRepository docRepo;

    @PostMapping("/upload")
    public String uploadDoc(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "fail: 文件为空";
        }
        String docId = UUID.randomUUID().toString();
        String fileName = file.getOriginalFilename();
        String ext = fileName == null ? "unknown" : fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1) : "unknown";

        TtKnowledgeDoc meta = new TtKnowledgeDoc();
        meta.setDocId(docId);
        meta.setDocName(fileName);
        meta.setDocType(ext);
        meta.setStatus(1); // 处理中
        meta.setEsIndexName("table_tennis_knowledge");
        meta = docRepo.save(meta);

        try {
            byte[] bytes = file.getBytes();
            String content = new String(bytes, StandardCharsets.UTF_8);
            if (content.isBlank()) {
                throw new RuntimeException("文件内容为空，仅支持 txt/md 文本文件");
            }
            int chunkCount = hybridRAGService.insertDoc(docId, fileName, content, "upload");
            meta.setStatus(2); // 成功
            meta.setChunkCount(chunkCount);
            docRepo.save(meta);
            return "success: 已入库，切片数=" + chunkCount;
        } catch (Exception e) {
            log.error("文档上传失败", e);
            meta.setStatus(3); // 失败
            meta.setFailMsg(e.getMessage());
            docRepo.save(meta);
            return "fail: " + e.getMessage();
        }
    }

    @GetMapping("/list")
    public List<TtKnowledgeDoc> list() {
        return docRepo.findAll();
    }
}
