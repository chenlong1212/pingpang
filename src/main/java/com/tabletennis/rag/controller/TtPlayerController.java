package com.tabletennis.rag.controller;

import com.tabletennis.rag.entity.TtPlayer;
import com.tabletennis.rag.service.TtPlayerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/player")
@RequiredArgsConstructor
public class TtPlayerController {
    private final TtPlayerService playerService;

    @GetMapping("/list")
    public ResponseEntity<List<TtPlayer>> listAll() {
        return ResponseEntity.ok(playerService.listAll());
    }

    @GetMapping("/name/{name}")
    public ResponseEntity<TtPlayer> getByName(@PathVariable String name) {
        Optional<TtPlayer> playerOpt = playerService.getByName(name);
        return playerOpt.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/style/{style}")
    public ResponseEntity<List<TtPlayer>> getByStyle(@PathVariable String style) {
        return ResponseEntity.ok(playerService.listByPlayStyle(style));
    }

    @PostMapping("/save")
    public ResponseEntity<TtPlayer> save(@RequestBody TtPlayer player) {
        return ResponseEntity.ok(playerService.save(player));
    }

    /** 编辑选手（按 id 更新） */
    @PutMapping("/{id}")
    public ResponseEntity<TtPlayer> update(@PathVariable Long id, @RequestBody TtPlayer player) {
        Optional<TtPlayer> existing = playerService.getById(id);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(playerService.update(id, player));
    }

    /** 删除选手 */
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        if (playerService.getById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        playerService.delete(id);
        return ResponseEntity.ok("success: 已删除");
    }
}
