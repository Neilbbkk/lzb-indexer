package com.lzb.indexer.controller;

import com.lzb.indexer.domain.entity.SwapEvent;
import com.lzb.indexer.domain.repository.SwapEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Uniswap V2 Swap 事件查询 API。
 */
@RestController
@RequestMapping("/api/swaps")
public class SwapController {

    private final SwapEventRepository swapEventRepo;

    public SwapController(SwapEventRepository swapEventRepo) {
        this.swapEventRepo = swapEventRepo;
    }

    /** 分页查询 Swap 事件，支持按 sender 过滤 */
    @GetMapping
    public Map<String, Object> getSwaps(
            @RequestParam(defaultValue = "ethereum-uniswap") String chain,
            @RequestParam(required = false) String sender,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<SwapEvent> result;
        if (sender != null && !sender.isEmpty()) {
            result = swapEventRepo.findByChainNameAndSender(
                    chain, sender, PageRequest.of(page, size));
        } else {
            result = swapEventRepo.findByChainName(
                    chain, PageRequest.of(page, size));
        }

        Map<String, Object> m = new HashMap<>();
        m.put("items", result.getContent());
        m.put("chain", chain);
        m.put("page", page);
        m.put("size", size);
        m.put("total", result.getTotalElements());
        m.put("totalPages", result.getTotalPages());
        return m;
    }

    /** 最近 Swap 事件（最新 N 条） */
    @GetMapping("/recent")
    public Map<String, Object> getRecent(
            @RequestParam(defaultValue = "ethereum-uniswap") String chain,
            @RequestParam(defaultValue = "10") int limit) {
        Page<SwapEvent> result = swapEventRepo.findByChainNameOrderByBlockNumberDesc(
                chain, PageRequest.of(0, limit));

        Map<String, Object> m = new HashMap<>();
        m.put("items", result.getContent());
        m.put("chain", chain);
        m.put("total", result.getTotalElements());
        return m;
    }
}