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

    public Optional<TtPlayer> getById(Long id) {
        return playerRepository.findById(id);
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

    /** 编辑：按 id 更新已有选手 */
    @CacheEvict(cacheNames = "players", allEntries = true)
    public TtPlayer update(Long id, TtPlayer player) {
        player.setId(id);
        return playerRepository.save(player);
    }

    /** 删除选手，联动清除缓存 */
    @CacheEvict(cacheNames = "players", allEntries = true)
    public void delete(Long id) {
        playerRepository.deleteById(id);
    }
}
