package com.lzb.indexer.controller;

import com.lzb.indexer.dto.TransferResponse;
import com.lzb.indexer.service.TransferQueryService;
import com.lzb.indexer.domain.entity.TokenTransfer;
import com.lzb.indexer.domain.repository.TokenTransferRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ERC20 转账查询 API。
 */
@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferQueryService queryService;
    private final TokenTransferRepository transferRepo;

    public TransferController(TransferQueryService queryService,
                              TokenTransferRepository transferRepo) {
        this.queryService = queryService;
        this.transferRepo = transferRepo;
    }

    /** 按地址查询转账记录 */
    @GetMapping
    public Map<String, Object> getTransfers(
            @RequestParam String address,
            @RequestParam(defaultValue = "sepolia") String chain,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<TokenTransfer> result =
                queryService.getTransfersByChainAndAddress(chain, address, page, size);

        List<TransferResponse> items = result.getContent().stream()
                .map(TransferResponse::from)
                .collect(Collectors.toList());

        Map<String, Object> m = new HashMap<>();
        m.put("items", items);
        m.put("chain", chain);
        m.put("page", page);
        m.put("size", size);
        m.put("total", result.getTotalElements());
        m.put("totalPages", result.getTotalPages());
        return m;
    }

    /** Dashboard 用：某链最近 N 条转账（不限定地址） */
    @GetMapping("/recent")
    public Map<String, Object> getRecent(
            @RequestParam(defaultValue = "sepolia") String chain,
            @RequestParam(defaultValue = "10") int limit) {
        Page<TokenTransfer> result = transferRepo.findByChainNameOrderByBlockNumberDesc(
                chain, PageRequest.of(0, limit));

        List<TransferResponse> items = result.getContent().stream()
                .map(TransferResponse::from)
                .collect(Collectors.toList());

        Map<String, Object> m = new HashMap<>();
        m.put("items", items);
        m.put("chain", chain);
        m.put("total", result.getTotalElements());
        return m;
    }
}