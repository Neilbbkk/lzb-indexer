package com.lzb.indexer.controller;

import com.lzb.indexer.scanner.BlockScanner;
import com.lzb.indexer.scanner.ScannerScheduler;
import com.lzb.indexer.service.TransferQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/indexer")
public class IndexerController {

    private final ScannerScheduler scheduler;
    private final TransferQueryService queryService;

    public IndexerController(ScannerScheduler scheduler, TransferQueryService queryService) {
        this.scheduler = scheduler;
        this.queryService = queryService;
    }

    @GetMapping("/status")
    public Map<String, Object> status(@RequestParam(required = false) String chain) {
        Map<String, Object> m = new HashMap<>();
        List<Map<String, Object>> chainStatuses = new ArrayList<>();

        for (BlockScanner s : scheduler.getScanners()) {
            if (chain != null && !s.getChainName().equalsIgnoreCase(chain)) continue;
            Map<String, Object> cs = new HashMap<>();
            cs.put("chain", s.getChainName());
            cs.put("running", s.isRunning());
            cs.put("latestScannedBlock", s.getLatestScannedBlock());
            cs.put("chainTip", s.getChainTip());
            cs.put("totalTransfers", queryService.getTotalTransfers(s.getChainName()));
            cs.put("status", s.isRunning() ? "scanning" : "idle");
            chainStatuses.add(cs);
        }

        m.put("chains", chainStatuses);
        m.put("scannerCount", chainStatuses.size());
        return m;
    }
}