package com.lzb.indexer.service;

import com.lzb.indexer.domain.entity.GmxPositionHistory;
import com.lzb.indexer.domain.entity.SwapEvent;
import com.lzb.indexer.domain.entity.TokenTransfer;
import com.lzb.indexer.domain.repository.GmxPositionHistoryRepository;
import com.lzb.indexer.domain.repository.SwapEventRepository;
import com.lzb.indexer.domain.repository.TokenTransferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Batch persistence for scanned events: dedupe by (tx_hash, log_index, chain_name),
 * then saveAll in chunks of 500. Each method runs in its own transaction via Spring proxy.
 */
@Service
public class ScanEventWriter {

    static final int BATCH_SIZE = 500;

    private final TokenTransferRepository transferRepo;
    private final SwapEventRepository swapEventRepo;
    private final GmxPositionHistoryRepository gmxHistoryRepo;

    public ScanEventWriter(TokenTransferRepository transferRepo,
                           SwapEventRepository swapEventRepo,
                           GmxPositionHistoryRepository gmxHistoryRepo) {
        this.transferRepo = transferRepo;
        this.swapEventRepo = swapEventRepo;
        this.gmxHistoryRepo = gmxHistoryRepo;
    }

    @Transactional
    public void saveTransfers(List<TokenTransfer> events) {
        List<TokenTransfer> fresh = new ArrayList<>();
        for (TokenTransfer e : events) {
            if (!transferRepo.existsByTxHashAndLogIndexAndChainName(
                    e.getTxHash(), e.getLogIndex(), e.getChainName())) {
                fresh.add(e);
            }
        }
        saveInChunks(fresh, transferRepo::saveAll);
    }

    @Transactional
    public void saveSwaps(List<SwapEvent> events) {
        List<SwapEvent> fresh = new ArrayList<>();
        for (SwapEvent e : events) {
            if (!swapEventRepo.existsByTxHashAndLogIndexAndChainName(
                    e.getTxHash(), e.getLogIndex(), e.getChainName())) {
                fresh.add(e);
            }
        }
        saveInChunks(fresh, swapEventRepo::saveAll);
    }

    @Transactional
    public void saveGmxHistory(List<GmxPositionHistory> events) {
        List<GmxPositionHistory> fresh = new ArrayList<>();
        for (GmxPositionHistory e : events) {
            if (!gmxHistoryRepo.existsByTxHashAndLogIndexAndChainName(
                    e.getTxHash(), e.getLogIndex(), e.getChainName())) {
                fresh.add(e);
            }
        }
        saveInChunks(fresh, gmxHistoryRepo::saveAll);
    }

    private <T> void saveInChunks(List<T> items, Consumer<List<T>> saver) {
        for (int i = 0; i < items.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, items.size());
            saver.accept(new ArrayList<>(items.subList(i, end)));
        }
    }
}
