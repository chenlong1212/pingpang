package com.tabletennis.rag.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "tt_player")
@Data
public class TtPlayer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String playerName;
    private String handType;
    private String gripType;
    private String playStyle;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
