package com.lzb.indexer.controller;

import com.lzb.indexer.dto.TransferResponse;
import com.lzb.indexer.service.TransferQueryService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferQueryService queryService;

    public TransferController(TransferQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public Map<String, Object> getTransfers(
            @RequestParam String address,
            @RequestParam(defaultValue = "sepolia") String chain,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<com.lzb.indexer.domain.entity.TokenTransfer> result =
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
}