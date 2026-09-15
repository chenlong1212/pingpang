package com.tabletennis.rag.controller;

import com.tabletennis.rag.ai.QueryRewriteService;
import com.tabletennis.rag.ai.TableTennisTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class AgentChatController {
    private final ChatClient chatClient;
    private final TableTennisTools tableTennisTools;
    private final QueryRewriteService queryRewriteService;

    @PostMapping("/ask")
    public String ask(@RequestBody String userQuestion) {
        log.info("用户提问：{}", userQuestion);

        // 1. Query 改写 + 意图识别
        QueryRewriteService.QueryRewriteResult rewriteResult = queryRewriteService.rewriteAndDetectIntent(userQuestion);
        String standardQuery = rewriteResult.getStandardQuery();
        String intent = rewriteResult.getIntent();
        log.info("改写后 query：{}，意图：{}", standardQuery, intent);

        // 2. Agent 自主选择 Tool 调用（不硬编码 if/else 路由）
        String systemPrompt = """
                你是乒乓球智能体助手（乒乓龙）。
                你拥有3个工具：查询选手档案、查询比赛记录、乒乓球知识库检索。
                根据用户问题自主判断调用哪个工具，不要编造不存在的资料；工具返回空结果时如实告知用户查无数据。
                - 选手档案：选手姓名、左右手、握拍、打法；
                - 比赛记录：我和对手的对战记录、日期、比赛类型、比分；
                - 知识库：乒乓球技术、战术、胶皮器材、训练方法等知识。
                调用工具拿到结果后，整理成自然中文回答用户；如果是知识库检索，请结合检索到的内容回答，并注明参考来源。
                """;

        return chatClient.prompt()
                .system(systemPrompt)
                .user(standardQuery)
                .tools(tableTennisTools)
                .call()
                .content();
    }
}
