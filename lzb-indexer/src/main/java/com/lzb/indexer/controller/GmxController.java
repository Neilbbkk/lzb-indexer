package com.lzb.indexer.controller;

import com.lzb.indexer.domain.entity.GmxPosition;
import com.lzb.indexer.domain.entity.GmxPositionHistory;
import com.lzb.indexer.domain.repository.GmxPositionHistoryRepository;
import com.lzb.indexer.service.GmxPositionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GMX V2 仓位查询 API。
 */
@RestController
@RequestMapping("/api/gmx")
public class GmxController {

    private final GmxPositionService positionService;
    private final GmxPositionHistoryRepository historyRepo;

    public GmxController(GmxPositionService positionService,
                         GmxPositionHistoryRepository historyRepo) {
        this.positionService = positionService;
        this.historyRepo = historyRepo;
    }

    /** 查询某地址的仓位 */
    @GetMapping("/positions/{address}")
    public List<GmxPosition> getPositions(
            @PathVariable String address,
            @RequestParam(defaultValue = "arbitrum") String chain) {
        return positionService.getPositionsByAccount(chain, address);
    }

    /** 查询所有开仓仓位 */
    @GetMapping("/positions/open")
    public List<GmxPosition> getOpenPositions(
            @RequestParam(defaultValue = "arbitrum") String chain) {
        return positionService.getPositionsByStatus(chain, GmxPosition.Status.OPEN);
    }

    /** GMX 统计 */
    @GetMapping("/stats")
    public Map<String, Object> getStats(
            @RequestParam(defaultValue = "arbitrum") String chain) {
        long total = positionService.countByChain(chain);
        long open = positionService.getPositionsByStatus(chain, GmxPosition.Status.OPEN).size();
        Map<String, Object> stats = new HashMap<>();
        stats.put("chain", chain);
        stats.put("totalPositions", total);
        stats.put("openPositions", open);
        return stats;
    }

    /** Dashboard 用：最近 N 条 GMX 历史事件 */
    @GetMapping("/history/recent")
    public Map<String, Object> getRecentHistory(
            @RequestParam(defaultValue = "arbitrum-gmx-vault") String chain,
            @RequestParam(defaultValue = "10") int limit) {
        Page<GmxPositionHistory> result = historyRepo.findByChainNameOrderByBlockNumberDesc(
                chain, PageRequest.of(0, limit));

        Map<String, Object> m = new HashMap<>();
        m.put("items", result.getContent());
        m.put("chain", chain);
        m.put("total", result.getTotalElements());
        return m;
    }
}