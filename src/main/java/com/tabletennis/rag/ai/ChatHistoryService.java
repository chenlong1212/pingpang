package com.tabletennis.rag.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

/**
 * 会话记忆服务：基于 Redis 保存多轮对话历史（按 sessionId 隔离）。
 * - 每条会话最多保留最近 10 轮（20 条消息），TTL 24 小时
 * - 支持 Agent 主对话加载 Spring AI Message 列表（真正带上文）
 * - 支持 Query Rewrite 加载纯文本上下文（指代消解：把"他"还原为具体对象）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatHistoryService {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String KEY_PREFIX = "chat:history:";
    private static final int MAX_MESSAGES = 20;
    private static final Duration TTL = Duration.ofHours(24);

    /** 追加一条消息，并只保留最近 MAX_MESSAGES 条 */
    public void append(String sessionId, String role, String content) {
        try {
            List<Map<String, String>> history = loadRaw(sessionId);
            Map<String, String> msg = new HashMap<>();
            msg.put("role", role);
            msg.put("content", content);
            history.add(msg);
            if (history.size() > MAX_MESSAGES) {
                history = new ArrayList<>(history.subList(history.size() - MAX_MESSAGES, history.size()));
            }
            redisTemplate.opsForValue().set(key(sessionId), objectMapper.writeValueAsString(history), TTL);
        } catch (Exception e) {
            log.warn("保存会话历史失败 sessionId={}: {}", sessionId, e.getMessage());
        }
    }

    /** 加载历史为 Spring AI Message 列表，供 Agent 主对话携带上下文 */
    public List<Message> loadMessages(String sessionId) {
        List<Message> result = new ArrayList<>();
        for (Map<String, String> m : loadRaw(sessionId)) {
            String content = m.getOrDefault("content", "");
            result.add("user".equals(m.get("role"))
                    ? new UserMessage(content)
                    : new AssistantMessage(content));
        }
        return result;
    }

    /** 加载历史为纯文本（供 Query Rewrite 指代消解） */
    public String loadText(String sessionId) {
        List<Map<String, String>> history = loadRaw(sessionId);
        StringBuilder sb = new StringBuilder();
        for (Map<String, String> m : history) {
            String role = "user".equals(m.get("role")) ? "用户" : "助手";
            sb.append(role).append("：").append(m.getOrDefault("content", "")).append("\n");
        }
        return sb.toString();
    }

    public void clear(String sessionId) {
        redisTemplate.delete(key(sessionId));
    }

    private List<Map<String, String>> loadRaw(String sessionId) {
        String json = redisTemplate.opsForValue().get(key(sessionId));
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, String>>>() {});
        } catch (Exception e) {
            log.warn("读取会话历史失败，重置 sessionId={}: {}", sessionId, e.getMessage());
            return new ArrayList<>();
        }
    }

    private String key(String sessionId) {
        return KEY_PREFIX + (sessionId == null || sessionId.isBlank() ? "default" : sessionId);
    }
}
