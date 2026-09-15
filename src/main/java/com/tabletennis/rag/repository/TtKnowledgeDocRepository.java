package com.tabletennis.rag.repository;

import com.tabletennis.rag.entity.TtKnowledgeDoc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TtKnowledgeDocRepository extends JpaRepository<TtKnowledgeDoc, Long> {
    List<TtKnowledgeDoc> findByStatus(Integer status);
    java.util.Optional<TtKnowledgeDoc> findByDocId(String docId);
}
