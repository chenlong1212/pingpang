package com.tabletennis.rag.init;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorSimilarity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 应用启动时自动创建 ES 知识库索引（含 IK 分词 + 1024维 dense_vector）
 * 若 ES 未启动，仅记录告警，不阻塞应用启动。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EsIndexInitializer implements ApplicationRunner {

    public static final String INDEX_NAME = "table_tennis_knowledge";

    private final ElasticsearchClient esClient;

    @Override
    public void run(ApplicationArguments args) {
        try {
            boolean exists = esClient.indices().exists(e -> e.index(INDEX_NAME)).value();
            if (exists) {
                log.info("ES 索引 {} 已存在，跳过创建", INDEX_NAME);
                return;
            }
            esClient.indices().create(c -> c
                    .index(INDEX_NAME)
                    .settings(s -> s
                            .numberOfShards("1")
                            .numberOfReplicas("0")
                            .analysis(a -> a
                                    .analyzer("ik_analyzer", an -> an
                                            .custom(cu -> cu.tokenizer("ik_max_word")))))
                    .mappings(m -> m
                            .properties("docId", p -> p.keyword(k -> k))
                            .properties("title", p -> p.text(t -> t.analyzer("ik_analyzer")))
                            .properties("content", p -> p.text(t -> t.analyzer("ik_analyzer")))
                            .properties("vector", p -> p.denseVector(d -> d
                                    .dims(1024)
                                    .index(true)
                                    .similarity(DenseVectorSimilarity.Cosine)))
                            .properties("source", p -> p.keyword(k -> k))
                            .properties("chunkIndex", p -> p.integer(i -> i))));
            log.info("ES 索引 {} 创建成功（IK 分词 + 1024维 dense_vector）", INDEX_NAME);
        } catch (Exception e) {
            log.warn("ES 索引创建失败（请确认 docker 中 ES 已启动且已安装 IK 插件）：{}", e.getMessage());
        }
    }
}
