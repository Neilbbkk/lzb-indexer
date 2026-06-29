package com.lzb.indexer.controller;

import com.lzb.indexer.domain.entity.GmxPosition;
import com.lzb.indexer.service.GmxPositionService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gmx")
public class GmxController {

    private final GmxPositionService positionService;

    public GmxController(GmxPositionService positionService) {
        this.positionService = positionService;
    }

    @GetMapping("/positions/{address}")
    public List<GmxPosition> getPositions(
            @PathVariable String address,
            @RequestParam(defaultValue = "arbitrum") String chain) {
        return positionService.getPositionsByAccount(chain, address);
    }

    @GetMapping("/positions/open")
    public List<GmxPosition> getOpenPositions(
            @RequestParam(defaultValue = "arbitrum") String chain) {
        return positionService.getPositionsByStatus(chain, GmxPosition.Status.OPEN);
    }

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
}