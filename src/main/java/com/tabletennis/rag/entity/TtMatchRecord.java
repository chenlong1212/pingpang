package com.tabletennis.rag.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tt_match_record")
@Data
public class TtMatchRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String opponentName;
    private LocalDate matchDate;
    private String matchType;
    private String matchScore;
    private String matchNote;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
