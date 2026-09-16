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

    /** 分页查询：倒序（最新在前）+ 页码，每页 size 条，支持 keyword 模糊筛选对手 */
    @GetMapping("/page")
    public ResponseEntity<org.springframework.data.domain.Page<TtMatchRecord>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(matchService.searchPage(keyword, Math.max(page, 1), Math.min(size, 50)));
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

    /** 编辑比赛记录（按 id 更新） */
    @PutMapping("/{id}")
    public ResponseEntity<TtMatchRecord> update(@PathVariable Long id, @RequestBody TtMatchRecord record) {
        if (matchService.getById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(matchService.update(id, record));
    }

    /** 删除比赛记录 */
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        if (matchService.getById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        matchService.delete(id);
        return ResponseEntity.ok("success: 已删除");
    }
}
