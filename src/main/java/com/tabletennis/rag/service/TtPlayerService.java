package com.tabletennis.rag.service;

import com.tabletennis.rag.entity.TtPlayer;
import com.tabletennis.rag.repository.TtPlayerRepository;
import lombok.RequiredArgsConstructor;
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

    public List<TtPlayer> listAll() {
        return playerRepository.findAll();
    }

    public List<TtPlayer> listByPlayStyle(String playStyle) {
        return playerRepository.findByPlayStyleContaining(playStyle);
    }

    public TtPlayer save(TtPlayer player) {
        return playerRepository.save(player);
    }
}
