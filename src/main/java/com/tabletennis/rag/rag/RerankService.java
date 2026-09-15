package com.tabletennis.rag.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reranker 重排：调用硅基流动 BAAI/bge-reranker-v2-m3（OpenAI 兼容 /v1/rerank）
 * 对 RRF 融合后的候选按相关性打分重新排序，进一步提升排序质量。
 * 调用失败时降级返回原序，不影响主链路。
 */
@Service
@Slf4j
public class RerankService {
    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public RerankService(@Value("${rerank.base-url:https://api.siliconflow.cn}") String baseUrl,
                         @Value("${rerank.api-key:}") String apiKey,
                         @Value("${rerank.model:BAAI/bge-reranker-v2-m3}") String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    @SuppressWarnings("unchecked")
    public List<RetrieveDTO> rerank(List<RetrieveDTO> chunks, String query) {
        if (chunks == null || chunks.size() < 2 || apiKey.isBlank()) {
            return chunks;
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("query", query);
            body.put("documents", chunks.stream().map(RetrieveDTO::getContent).toList());
            body.put("top_n", chunks.size());

            Map<String, Object> resp = restClient.post()
                    .uri("/v1/rerank")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (resp == null) {
                return chunks;
            }
            List<Map<String, Object>> results = (List<Map<String, Object>>) resp.get("results");
            if (results == null || results.isEmpty()) {
                return chunks;
            }
            // 按 relevance_score 降序重组候选，并把重排分数写回
            List<RetrieveDTO> sorted = new ArrayList<>();
            for (Map<String, Object> r : results) {
                int idx = ((Number) r.get("index")).intValue();
                double score = ((Number) r.get("relevance_score")).doubleValue();
                if (idx >= 0 && idx < chunks.size()) {
                    RetrieveDTO dto = chunks.get(idx);
                    dto.setScore(score);
                    sorted.add(dto);
                }
            }
            for (RetrieveDTO c : chunks) {
                if (!sorted.contains(c)) {
                    sorted.add(c);
                }
            }
            log.info("Reranker 重排完成，候选 {} → 保留 {}", chunks.size(), sorted.size());
            return sorted;
        } catch (Exception e) {
            log.warn("Reranker 调用失败，降级为 RRF 原序: {}", e.getMessage());
            return chunks;
        }
    }
}
