package com.tabletennis.rag.repository;

import com.tabletennis.rag.entity.TtMatchRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TtMatchRecordRepository extends JpaRepository<TtMatchRecord, Long> {
    List<TtMatchRecord> findByOpponentName(String opponentName);
    List<TtMatchRecord> findByMatchType(String matchType);
}
