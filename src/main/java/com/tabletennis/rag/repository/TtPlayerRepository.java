package com.tabletennis.rag.repository;

import com.tabletennis.rag.entity.TtPlayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TtPlayerRepository extends JpaRepository<TtPlayer, Long> {
    Optional<TtPlayer> findByPlayerName(String playerName);
    List<TtPlayer> findByPlayStyleContaining(String playStyle);
}
