package com.tabletennis.rag.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * 文档上传生产者：上传/文本录入 → doc-upload topic（两阶段异步流水线第一段）
 * 消费失败超过阈值 → doc-upload-dlq 死信队列
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocUploadProducer {
    public static final String TOPIC_UPLOAD = "doc-upload";
    public static final String TOPIC_DLQ = "doc-upload-dlq";

    private final KafkaTemplate<String, DocUploadMessage> kafkaTemplate;

    public void send(DocUploadMessage message) {
        kafkaTemplate.send(TOPIC_UPLOAD, message.getDocId(), message).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("发送文档流水线消息失败 docId={}", message.getDocId(), ex);
            } else {
                log.info("文档流水线消息已发送 docId={} topic={} partition={} offset={}",
                        message.getDocId(), TOPIC_UPLOAD, result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    public void sendToDlq(DocUploadMessage message) {
        kafkaTemplate.send(TOPIC_DLQ, message.getDocId(), message).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("发送死信队列失败 docId={}", message.getDocId(), ex);
            } else {
                log.error("文档已进入死信队列 docId={} retryCount={}", message.getDocId(), message.getRetryCount());
            }
        });
    }
}
