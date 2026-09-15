package com.tabletennis.rag.controller;

import com.tabletennis.rag.entity.TtMatchRecord;
import com.tabletennis.rag.service.TtMatchRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
public class TtMatchRecordController {
    private final TtMatchRecordService matchService;

    @GetMapping("/list")
    public ResponseEntity<List<TtMatchRecord>> listAll() {
        return ResponseEntity.ok(matchService.listAll());
    }

    @GetMapping("/opponent/{opponentName}")
    public ResponseEntity<List<TtMatchRecord>> getByOpponent(@PathVariable String opponentName) {
        return ResponseEntity.ok(matchService.getByOpponent(opponentName));
    }

    @GetMapping("/type/{matchType}")
    public ResponseEntity<List<TtMatchRecord>> getByMatchType(@PathVariable String matchType) {
        return ResponseEntity.ok(matchService.getByMatchType(matchType));
    }

    @PostMapping("/save")
    public ResponseEntity<TtMatchRecord> save(@RequestBody TtMatchRecord record) {
        return ResponseEntity.ok(matchService.save(record));
    }
}
