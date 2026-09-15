package com.tabletennis.rag.service;

import com.tabletennis.rag.entity.TtMatchRecord;
import com.tabletennis.rag.repository.TtMatchRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TtMatchRecordService {
    private final TtMatchRecordRepository matchRepository;

    public List<TtMatchRecord> getByOpponent(String opponentName) {
        return matchRepository.findByOpponentName(opponentName);
    }

    public List<TtMatchRecord> getByMatchType(String matchType) {
        return matchRepository.findByMatchType(matchType);
    }

    public List<TtMatchRecord> listAll() {
        return matchRepository.findAll();
    }

    public TtMatchRecord save(TtMatchRecord record) {
        return matchRepository.save(record);
    }
}
