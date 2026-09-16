package com.tabletennis.rag.repository;

import com.tabletennis.rag.entity.TtMatchRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TtMatchRecordRepository extends JpaRepository<TtMatchRecord, Long> {
    List<TtMatchRecord> findByOpponentName(String opponentName);
    List<TtMatchRecord> findByMatchType(String matchType);

    /** 分页查询比赛记录：按日期倒序（最新在前），支持按对手姓名模糊筛选 */
    @Query("SELECT m FROM TtMatchRecord m WHERE (:keyword IS NULL OR :keyword = '' OR m.opponentName LIKE CONCAT('%', :keyword, '%')) ORDER BY m.matchDate DESC, m.id DESC")
    Page<TtMatchRecord> searchPage(@Param("keyword") String keyword, Pageable pageable);
}
