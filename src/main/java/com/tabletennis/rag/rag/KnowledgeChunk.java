package com.tabletennis.rag.rag;

import lombok.Data;
import java.util.List;

@Data
public class KnowledgeChunk {
    private String docId;
    private String title;
    private String content;
    private List<Float> vector;
    private String source;
    private Integer chunkIndex;
}
