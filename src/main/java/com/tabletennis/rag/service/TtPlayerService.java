package com.tabletennis.rag.service;

import com.tabletennis.rag.entity.TtPlayer;
import com.tabletennis.rag.repository.TtPlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TtPlayerService {
    private final TtPlayerRepository playerRepository;

    public Optional<TtPlayer> getByName(String name) {
        return playerRepository.findByPlayerName(name);
    }

    /** 查询结果缓存 30 分钟，命中 Redis 不再查库 */
    @Cacheable(cacheNames = "players", key = "'all'")
    public List<TtPlayer> listAll() {
        return playerRepository.findAll();
    }

    public List<TtPlayer> listByPlayStyle(String playStyle) {
        return playerRepository.findByPlayStyleContaining(playStyle);
    }

    /** 新增/修改后清除缓存，保证数据一致性 */
    @CacheEvict(cacheNames = "players", allEntries = true)
    public TtPlayer save(TtPlayer player) {
        return playerRepository.save(player);
    }
}
