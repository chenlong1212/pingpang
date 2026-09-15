package com.tabletennis.rag.kafka;

import lombok.Data;

import java.io.Serializable;

/**
 * 文档流水线消息：
 * - upload：文件上传（文件已落盘，filePath 指向本地文件）
 * - manual：手动文本录入（content 直接携带文本）
 * retryCount 用于消费端重试计数，超过阈值进入死信队列
 */
@Data
public class DocUploadMessage implements Serializable {
    private String docId;
    private String fileName;   // 展示名（文件原名 / 文本标题）
    private String filePath;   // upload 模式：本地文件路径
    private String content;    // manual 模式：文本内容
    private String source;     // upload / manual
    private int retryCount;
}
