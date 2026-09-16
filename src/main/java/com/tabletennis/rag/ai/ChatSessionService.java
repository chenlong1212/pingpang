package com.tabletennis.rag.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 会话管理服务：Redis Hash 维护会话列表（sessionId -> {标题, 创建/更新时间}）。
 * - 新建会话：生成 UUID sessionId
 * - 删除会话：移除元数据（历史消息由调用方联动删除）
 * - 标题自动生成：默认"新会话"，第一条问题发出后截取前 20 字作为标题
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionService {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String SESSIONS_KEY = "chat:sessions";

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionInfo {
        private String sessionId;
        private String title;
        private long createdAt;
        private long updatedAt;
    }

    /** 全部会话，按最近活跃倒序 */
    public List<SessionInfo> list() {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(SESSIONS_KEY);
        List<SessionInfo> result = new ArrayList<>();
        entries.forEach((k, v) -> {
            try {
                SessionInfo s = objectMapper.readValue(v.toString(), SessionInfo.class);
                s.setSessionId(k.toString());
                result.add(s);
            } catch (Exception e) {
                log.warn("解析会话元数据失败 {}: {}", k, e.getMessage());
            }
        });
        result.sort(Comparator.comparingLong(SessionInfo::getUpdatedAt).reversed());
        return result;
    }

    /** 新建会话 */
    public SessionInfo create(String title) {
        String sessionId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        SessionInfo info = new SessionInfo(sessionId,
                (title == null || title.isBlank()) ? "新会话" : title, now, now);
        save(info);
        return info;
    }

    /** 删除会话元数据 */
    public void delete(String sessionId) {
        redisTemplate.opsForHash().delete(SESSIONS_KEY, sessionId);
    }

    /** 会话活跃：更新 updatedAt；若仍是默认标题，用首问生成标题 */
    public void touch(String sessionId, String firstQuestion) {
        if (sessionId == null || sessionId.isBlank()) return;
        SessionInfo info = get(sessionId);
        if (info == null) {
            info = create(null);
            info.setSessionId(sessionId);
        }
        info.setUpdatedAt(System.currentTimeMillis());
        if ("新会话".equals(info.getTitle()) && firstQuestion != null && !firstQuestion.isBlank()) {
            String t = firstQuestion.length() > 20 ? firstQuestion.substring(0, 20) + "…" : firstQuestion;
            info.setTitle(t);
        }
        save(info);
    }

    private SessionInfo get(String sessionId) {
        Object v = redisTemplate.opsForHash().get(SESSIONS_KEY, sessionId);
        if (v == null) return null;
        try {
            return objectMapper.readValue(v.toString(), SessionInfo.class);
        } catch (Exception e) {
            return null;
        }
    }

    private void save(SessionInfo info) {
        try {
            redisTemplate.opsForHash().put(SESSIONS_KEY, info.getSessionId(),
                    objectMapper.writeValueAsString(info));
        } catch (Exception e) {
            log.warn("保存会话元数据失败: {}", e.getMessage());
        }
    }
}
