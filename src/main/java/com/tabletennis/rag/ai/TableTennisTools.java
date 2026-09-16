package com.tabletennis.rag.ai;

import com.tabletennis.rag.entity.TtMatchRecord;
import com.tabletennis.rag.entity.TtPlayer;
import com.tabletennis.rag.rag.HybridRAGService;
import com.tabletennis.rag.rag.RetrieveDTO;
import com.tabletennis.rag.service.TtMatchRecordService;
import com.tabletennis.rag.service.TtPlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Agent 可调用的业务工具：选手档案 / 比赛记录 / 知识库RAG检索
 * LLM 根据用户意图自主选择调用，不硬编码路由。
 */
@Component
@RequiredArgsConstructor
public class TableTennisTools {

    private final TtPlayerService playerService;
    private final TtMatchRecordService matchService;
    private final HybridRAGService hybridRAGService;

    @Tool(description = "根据选手姓名，查询选手乒乓球档案，信息包含：持拍左右手（左手/右手）、握拍方式（直拍/横拍）、打法类型（反胶/长胶/生胶/推挡/防弧）、开球网积分")
    public TtPlayer queryPlayerInfo(@ToolParam(description = "选手姓名") String playerName) {
        Optional<TtPlayer> playerOpt = playerService.getByName(playerName);
        return playerOpt.orElse(null);
    }

    @Tool(description = "查询我和指定对手之间的乒乓球比赛记录，返回比赛日期、比赛类型（开球网/私下交流/大型比赛）、比分（如3-0、3-2）、备注")
    public List<TtMatchRecord> queryMatchRecord(@ToolParam(description = "对手姓名") String opponentName) {
        return matchService.getByOpponent(opponentName);
    }

    @Tool(description = "乒乓球领域知识库检索，用于查询乒乓球技术、战术、胶皮器材、训练方法等专业知识，当问题不是查选手档案、比赛记录时调用这个工具")
    public List<RetrieveDTO> queryKnowledge(@ToolParam(description = "用户问题") String question) {
        return hybridRAGService.hybridRetrieve(question);
    }
}
