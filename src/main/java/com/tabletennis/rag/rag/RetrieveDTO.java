package com.tabletennis.rag.rag;

import lombok.Data;

@Data
public class RetrieveDTO {
    private String docId;
    private String title;
    private String content;
    private double score;
    private Integer chunkIndex;
}
