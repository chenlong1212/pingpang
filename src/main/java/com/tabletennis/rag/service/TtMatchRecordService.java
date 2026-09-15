package com.tabletennis.rag.service;

import com.tabletennis.rag.entity.TtMatchRecord;
import com.tabletennis.rag.repository.TtMatchRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TtMatchRecordService {
    private final TtMatchRecordRepository matchRepository;

    public List<TtMatchRecord> getByOpponent(String opponentName) {
        return matchRepository.findByOpponentName(opponentName);
    }

    public List<TtMatchRecord> getByMatchType(String matchType) {
        return matchRepository.findByMatchType(matchType);
    }

    /** 查询结果缓存 30 分钟，命中 Redis 不再查库 */
    @Cacheable(cacheNames = "matches", key = "'all'")
    public List<TtMatchRecord> listAll() {
        return matchRepository.findAll();
    }

    /** 新增/修改后清除缓存，保证数据一致性 */
    @CacheEvict(cacheNames = "matches", allEntries = true)
    public TtMatchRecord save(TtMatchRecord record) {
        return matchRepository.save(record);
    }
}
