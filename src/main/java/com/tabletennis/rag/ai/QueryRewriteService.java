package com.tabletennis.rag.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * Query Rewrite + 意图识别：将口语化问题改写为标准检索 Query，
 * 并输出意图类型（player / match / knowledge），辅助 Agent 决策。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryRewriteService {
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public QueryRewriteResult rewriteAndDetectIntent(String rawQuestion) {
        String prompt = """
                你是乒乓球智能助手的query改写与意图识别模块。
                用户输入：{question}
                任务：
                1. 将口语化问题改写为适合检索的标准query；
                2. 判断意图类型只能是下面三者之一：
                   player：查询选手档案
                   match：查询比赛记录
                   knowledge：乒乓球技术知识问答
                只返回严格JSON，不要多余解释、markdown、注释。
                返回JSON格式：{"standardQuery":"改写后的问题","intent":"player|match|knowledge"}
                """.replace("{question}", rawQuestion);

        String resp = chatClient.prompt()
                .user(prompt)
                .call()
                .content();
        try {
            if (resp != null && resp.startsWith("```")) {
                resp = resp.replaceAll("```(json)?", "").trim();
            }
            return objectMapper.readValue(resp, QueryRewriteResult.class);
        } catch (Exception e) {
            log.error("Query改写解析失败，原始问题:{}", rawQuestion, e);
            QueryRewriteResult fallback = new QueryRewriteResult();
            fallback.setStandardQuery(rawQuestion);
            fallback.setIntent("knowledge");
            return fallback;
        }
    }

    @lombok.Data
    public static class QueryRewriteResult {
        private String standardQuery;
        private String intent;
    }
}
