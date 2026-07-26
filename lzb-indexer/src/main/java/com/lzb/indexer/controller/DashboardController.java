package com.lzb.indexer.controller;

import com.lzb.indexer.domain.repository.*;
import com.lzb.indexer.scanner.BlockScanner;
import com.lzb.indexer.scanner.ScannerScheduler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * Dashboard 总览 API，聚合所有链的扫描状态 + 数据统计。
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final ScannerScheduler scheduler;
    private final TokenTransferRepository transferRepo;
    private final GmxPositionHistoryRepository gmxHistoryRepo;
    private final SwapEventRepository swapEventRepo;
    private final SyncErrorRepository syncErrorRepo;

    public DashboardController(ScannerScheduler scheduler,
                               TokenTransferRepository transferRepo,
                               GmxPositionHistoryRepository gmxHistoryRepo,
                               SwapEventRepository swapEventRepo,
                               SyncErrorRepository syncErrorRepo) {
        this.scheduler = scheduler;
        this.transferRepo = transferRepo;
        this.gmxHistoryRepo = gmxHistoryRepo;
        this.swapEventRepo = swapEventRepo;
        this.syncErrorRepo = syncErrorRepo;
    }

    /** 全量总览：扫描状态 + 各链数据统计 + 错误数 */
    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Map<String, Object> m = new LinkedHashMap<>();

        // 各链状态
        List<Map<String, Object>> chains = new ArrayList<>();
        for (BlockScanner s : scheduler.getScanners()) {
            Map<String, Object> cs = new LinkedHashMap<>();
            cs.put("chain", s.getChainName());
            cs.put("protocol", s.getProtocol());
            cs.put("running", s.isRunning());
            cs.put("latestScannedBlock", s.getLatestScannedBlock());
            cs.put("chainTip", s.getChainTip());
            long progress = s.getChainTip() > 0
                    ? (s.getLatestScannedBlock() * 100 / s.getChainTip()) : 0;
            cs.put("progressPercent", progress);
            chains.add(cs);
        }
        m.put("chains", chains);

        // 数据总量
        Map<String, Long> totals = new LinkedHashMap<>();
        totals.put("transfers", transferRepo.count());
        totals.put("gmxHistory", gmxHistoryRepo.count());
        totals.put("swaps", swapEventRepo.count());
        totals.put("errors", syncErrorRepo.count());
        m.put("totals", totals);

        m.put("scannerCount", chains.size());
        return m;
    }
}