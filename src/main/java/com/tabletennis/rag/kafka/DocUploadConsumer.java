package com.tabletennis.rag.kafka;

import com.tabletennis.rag.entity.TtKnowledgeDoc;
import com.tabletennis.rag.rag.HybridRAGService;
import com.tabletennis.rag.repository.TtKnowledgeDocRepository;
import com.tabletennis.rag.service.DocParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

/**
 * 文档流水线消费者（两阶段异步流水线第二段）：
 * 解析文本 → 切片 → BGE-M3 向量化 → ES 索引 → 更新状态
 * 失败重试 3 次（指数退避），仍失败进入死信队列并标记失败
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocUploadConsumer {
    private static final int MAX_RETRY = 3;
    private static final long RETRY_BASE_DELAY_MS = 2000;

    private final HybridRAGService hybridRAGService;
    private final TtKnowledgeDocRepository docRepo;
    private final DocParserService docParserService;
    private final DocUploadProducer producer;

    @KafkaListener(topics = DocUploadProducer.TOPIC_UPLOAD,
            groupId = "${spring.kafka.consumer.group-id}")
    public void consume(DocUploadMessage message, Acknowledgment ack) {
        try {
            process(message);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("文档处理失败 docId={} retryCount={} 原因: {}", message.getDocId(), message.getRetryCount(), e.getMessage());
            if (message.getRetryCount() < MAX_RETRY) {
                // 重试：退避后重新投递，先 ack 当前消息防止重复消费
                message.setRetryCount(message.getRetryCount() + 1);
                ack.acknowledge();
                try {
                    Thread.sleep(RETRY_BASE_DELAY_MS * message.getRetryCount());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                producer.send(message);
            } else {
                // 超过重试阈值 → 死信队列
                ack.acknowledge();
                producer.sendToDlq(message);
                markFailed(message.getDocId(), e.getMessage());
            }
        }
    }

    private void process(DocUploadMessage message) throws Exception {
        // 文档可能已在消费前被删除：跳过处理，避免读取已删除的落盘文件
        if (docRepo.findByDocId(message.getDocId()).isEmpty()) {
            log.info("文档已删除，跳过处理 docId={}", message.getDocId());
            return;
        }
        String content;
        String displayName;
        if ("upload".equals(message.getSource())) {
            content = docParserService.parseFile(message.getFilePath());
            displayName = message.getFileName();
        } else {
            content = message.getContent();
            displayName = message.getFileName();
        }
        if (content == null || content.isBlank()) {
            throw new RuntimeException("提取到的文本内容为空");
        }
        int chunkCount = hybridRAGService.insertDoc(message.getDocId(), displayName, content, message.getSource());
        markSuccess(message.getDocId(), chunkCount);
        log.info("文档流水线处理完成 docId={} name={} chunks={}", message.getDocId(), displayName, chunkCount);
    }

    private void markSuccess(String docId, int chunkCount) {
        docRepo.findByDocId(docId).ifPresent(meta -> {
            meta.setStatus(2);
            meta.setChunkCount(chunkCount);
            meta.setFailMsg(null);
            docRepo.save(meta);
        });
    }

    private void markFailed(String docId, String failMsg) {
        docRepo.findByDocId(docId).ifPresent(meta -> {
            meta.setStatus(3);
            meta.setFailMsg(failMsg);
            docRepo.save(meta);
        });
    }
}
