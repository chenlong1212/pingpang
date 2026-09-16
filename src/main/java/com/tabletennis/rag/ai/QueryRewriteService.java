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
        return rewriteAndDetectIntent(rawQuestion, "");
    }

    /** 带历史上下文的改写：支持"他/她/这个人"等指代消解 */
    public QueryRewriteResult rewriteAndDetectIntent(String rawQuestion, String historyText) {
        String prompt = """
                你是乒乓球智能助手的query改写与意图识别模块。
                历史对话上下文（用户与助手的对话记录，供指代消解）：
                {history}
                用户当前输入：{question}
                任务：
                1. 如果当前输入包含"他/她/这个人/上次说的"等指代，结合历史对话还原为具体的人名或对象；
                2. 将口语化问题改写为适合检索的标准query；
                3. 判断意图类型只能是下面三者之一：
                   player：查询选手档案
                   match：查询比赛记录
                   knowledge：乒乓球技术知识问答
                只返回严格JSON，不要多余解释、markdown、注释。
                返回JSON格式：{"standardQuery":"改写后的问题","intent":"player|match|knowledge"}
                """
                .replace("{question}", rawQuestion)
                .replace("{history}", historyText == null || historyText.isBlank() ? "（无历史对话）" : historyText);

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
