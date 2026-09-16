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

    public java.util.Optional<TtMatchRecord> getById(Long id) {
        return matchRepository.findById(id);
    }

    /** 查询结果缓存 30 分钟，命中 Redis 不再查库 */
    @Cacheable(cacheNames = "matches", key = "'all'")
    public List<TtMatchRecord> listAll() {
        return matchRepository.findAll();
    }

    /** 分页查询：按日期倒序（最新在前），支持按对手姓名模糊筛选，每页固定条数（页码从 1 开始） */
    public org.springframework.data.domain.Page<TtMatchRecord> searchPage(String keyword, int page, int size) {
        // Spring Data 页码从 0 开始，接口语义从 1 开始，这里转换
        return matchRepository.searchPage(keyword, org.springframework.data.domain.PageRequest.of(Math.max(page - 1, 0), size));
    }

    /** 新增/修改后清除缓存，保证数据一致性 */
    @CacheEvict(cacheNames = "matches", allEntries = true)
    public TtMatchRecord save(TtMatchRecord record) {
        return matchRepository.save(record);
    }

    /** 编辑：按 id 更新已有比赛记录 */
    @CacheEvict(cacheNames = "matches", allEntries = true)
    public TtMatchRecord update(Long id, TtMatchRecord record) {
        record.setId(id);
        return matchRepository.save(record);
    }

    /** 删除比赛记录，联动清除缓存 */
    @CacheEvict(cacheNames = "matches", allEntries = true)
    public void delete(Long id) {
        matchRepository.deleteById(id);
    }
}
