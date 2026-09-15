package com.tabletennis.rag.rag;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Hybrid RAG 检索链路：
 * ES IK分词 BM25 稀疏检索 + Dense Vector 向量检索
 * → RRF 融合两路排序结果 → Reranker（bge-reranker-v2-m3）重排
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HybridRAGService {
    private final ElasticsearchClient esClient;
    private final EmbeddingModel embeddingModel;
    private final RerankService rerankService;

    private static final String INDEX_NAME = "table_tennis_knowledge";
    private static final int RRF_K = 60;
    private static final int TOP_N = 5;

    public List<RetrieveDTO> hybridRetrieve(String query) {
        // 1. 查询向量化（BGE-M3，1024维）
        float[] queryVec = embeddingModel.embed(query);
        List<Float> vec = new ArrayList<>(queryVec.length);
        for (float v : queryVec) {
            vec.add(v);
        }

        Map<String, KnowledgeChunk> chunkCache = new HashMap<>();
        // 2. 双路召回
        Map<String, Double> bm25Result = bm25Search(query, chunkCache);
        Map<String, Double> vectorResult = vectorSearch(vec, chunkCache);
        // 3. RRF 融合
        Map<String, Double> rrfScoreMap = rrfFuse(bm25Result, vectorResult);
        // 4. 转文档块
        List<RetrieveDTO> rawList = convertToChunk(rrfScoreMap, chunkCache);
        // 5. Reranker 重排（bge-reranker-v2-m3 按相关性打分，失败自动降级）
        return rerankService.rerank(rawList, query);
    }

    private Map<String, Double> bm25Search(String queryText, Map<String, KnowledgeChunk> cache) {
        Map<String, Double> resultMap = new HashMap<>();
        try {
            SearchResponse<KnowledgeChunk> response = esClient.search(s -> s
                    .index(INDEX_NAME)
                    .query(q -> q.match(m -> m.field("content").query(FieldValue.of(queryText))))
                    .size(TOP_N), KnowledgeChunk.class);
            List<Hit<KnowledgeChunk>> hits = response.hits().hits();
            for (int i = 0; i < hits.size(); i++) {
                KnowledgeChunk chunk = hits.get(i).source();
                if (chunk == null) continue;
                String key = chunk.getDocId() + "_" + chunk.getChunkIndex();
                resultMap.put(key, (double) i + 1);
                cache.putIfAbsent(key, chunk);
            }
        } catch (Exception e) {
            log.error("BM25 检索异常", e);
        }
        return resultMap;
    }

    private Map<String, Double> vectorSearch(List<Float> vec, Map<String, KnowledgeChunk> cache) {
        Map<String, Double> resultMap = new HashMap<>();
        try {
            // Dense Vector 近似 kNN 检索（余弦相似度）
            SearchResponse<KnowledgeChunk> response = esClient.search(s -> s
                    .index(INDEX_NAME)
                    .knn(k -> k
                            .field("vector")
                            .queryVector(vec)
                            .k(TOP_N))
                    .size(TOP_N), KnowledgeChunk.class);
            List<Hit<KnowledgeChunk>> hits = response.hits().hits();
            for (int i = 0; i < hits.size(); i++) {
                KnowledgeChunk chunk = hits.get(i).source();
                if (chunk == null) continue;
                String key = chunk.getDocId() + "_" + chunk.getChunkIndex();
                resultMap.put(key, (double) i + 1);
                cache.putIfAbsent(key, chunk);
            }
        } catch (Exception e) {
            log.error("向量检索异常", e);
        }
        return resultMap;
    }

    private Map<String, Double> rrfFuse(Map<String, Double> bm25Map, Map<String, Double> vecMap) {
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(bm25Map.keySet());
        allKeys.addAll(vecMap.keySet());
        Map<String, Double> rrfMap = new HashMap<>();
        for (String key : allKeys) {
            double score = 0.0;
            if (bm25Map.containsKey(key)) {
                score += 1.0 / (RRF_K + bm25Map.get(key));
            }
            if (vecMap.containsKey(key)) {
                score += 1.0 / (RRF_K + vecMap.get(key));
            }
            rrfMap.put(key, score);
        }
        return rrfMap.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (o, n) -> n, LinkedHashMap::new));
    }

    private List<RetrieveDTO> convertToChunk(Map<String, Double> rrfMap, Map<String, KnowledgeChunk> cache) {
        List<RetrieveDTO> list = new ArrayList<>();
        for (Map.Entry<String, Double> entry : rrfMap.entrySet()) {
            KnowledgeChunk chunk = cache.get(entry.getKey());
            if (chunk == null) continue;
            RetrieveDTO dto = new RetrieveDTO();
            dto.setDocId(chunk.getDocId());
            dto.setTitle(chunk.getTitle());
            dto.setContent(chunk.getContent());
            dto.setScore(entry.getValue());
            dto.setChunkIndex(chunk.getChunkIndex());
            list.add(dto);
        }
        return list;
    }

    /**
     * 文档入库：文本切分（固定长度+重叠窗口）→ BGE-M3 向量化 → 写入 ES
     */
    public int insertDoc(String docId, String title, String content, String source) {
        List<String> chunks = splitText(content, 400, 50);
        for (int i = 0; i < chunks.size(); i++) {
            String text = chunks.get(i);
            float[] vecArr = embeddingModel.embed(text);
            List<Float> vec = new ArrayList<>(vecArr.length);
            for (float v : vecArr) {
                vec.add(v);
            }
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setDocId(docId);
            chunk.setTitle(title);
            chunk.setContent(text);
            chunk.setVector(vec);
            chunk.setSource(source);
            chunk.setChunkIndex(i);
            try {
                final int idx = i;
                esClient.index(iReq -> iReq.index(INDEX_NAME).id(docId + "_" + idx).document(chunk));
            } catch (Exception e) {
                log.error("写入 ES 失败 docId={} chunk={}", docId, i, e);
            }
        }
        return chunks.size();
    }

    private List<String> splitText(String text, int chunkSize, int overlap) {
        List<String> res = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            res.add(text.substring(start, end));
            start = start + chunkSize - overlap;
        }
        return res;
    }
}
