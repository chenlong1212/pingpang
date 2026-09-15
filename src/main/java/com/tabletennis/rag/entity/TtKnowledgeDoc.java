package com.tabletennis.rag.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "tt_knowledge_doc")
@Data
public class TtKnowledgeDoc {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String docId;
    private String docName;
    private String docType;
    private String docPath;
    private Integer status;
    private String failMsg;
    private Integer chunkCount;
    private String esIndexName;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @PrePersist
    void onCreate() {
        this.createTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updateTime = LocalDateTime.now();
    }
}
