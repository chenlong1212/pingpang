package com.tabletennis.rag.controller;

import com.tabletennis.rag.ai.ChatHistoryService;
import com.tabletennis.rag.ai.QueryRewriteService;
import com.tabletennis.rag.ai.TableTennisTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class AgentChatController {
    private final ChatClient chatClient;
    private final TableTennisTools tableTennisTools;
    private final QueryRewriteService queryRewriteService;
    private final ChatHistoryService chatHistoryService;

    /** 获取指定会话的历史消息，供前端刷新后恢复聊天界面 */
    @GetMapping("/history")
    public List<java.util.Map<String, String>> history(@RequestParam String sessionId) {
        return chatHistoryService.loadAll(sessionId);
    }

    /** 请求体：前端生成的会话 ID + 用户问题 */
    public record AskRequest(String sessionId, String question) {}

    @PostMapping("/ask")
    public String ask(@RequestBody AskRequest request) {
        String sessionId = request.sessionId();
        String userQuestion = request.question();
        log.info("用户提问(session={})：{}", sessionId, userQuestion);

        // 1. 加载历史上下文
        String historyText = chatHistoryService.loadText(sessionId);
        List<Message> historyMessages = chatHistoryService.loadMessages(sessionId);

        // 2. Query 改写 + 意图识别（带历史，支持"他"等指代消解）
        QueryRewriteService.QueryRewriteResult rewriteResult = queryRewriteService.rewriteAndDetectIntent(userQuestion, historyText);
        String standardQuery = rewriteResult.getStandardQuery();
        String intent = rewriteResult.getIntent();
        log.info("改写后 query：{}，意图：{}", standardQuery, intent);

        // 3. Agent 自主选择 Tool 调用（不硬编码 if/else 路由），携带历史实现多轮对话
        String systemPrompt = """
                你是乒乓球智能体助手（乒乓龙）。
                你拥有3个工具：查询选手档案、查询比赛记录、乒乓球知识库检索。
                根据用户问题自主判断调用哪个工具，不要编造不存在的资料；工具返回空结果时如实告知用户查无数据。
                - 选手档案：选手姓名、左右手、握拍、打法、开球网积分；
                - 比赛记录：我和对手的对战记录、日期、比赛类型、比分；
                - 知识库：乒乓球技术、战术、胶皮器材、训练方法、赛事参赛名单等知识。
                调用工具拿到结果后，整理成自然中文回答用户；如果是知识库检索，请结合检索到的内容回答，并注明参考来源。
                注意：用户可能在后续提问中用"他/她/这个人"等指代之前提到的人，请结合历史对话理解指代对象。
                """;

        String answer = chatClient.prompt()
                .system(systemPrompt)
                .messages(historyMessages)
                .user(standardQuery)
                .tools(tableTennisTools)
                .call()
                .content();

        // 4. 保存本轮对话到 Redis 会话历史
        chatHistoryService.append(sessionId, "user", userQuestion);
        chatHistoryService.append(sessionId, "assistant", answer);
        return answer;
    }
}
