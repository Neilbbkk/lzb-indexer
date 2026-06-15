package com.lzb.indexer.service;

import com.lzb.indexer.domain.entity.TokenTransfer;
import com.lzb.indexer.domain.repository.TokenTransferRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class TransferQueryService {

    private final TokenTransferRepository transferRepo;

    public TransferQueryService(TokenTransferRepository transferRepo) {
        this.transferRepo = transferRepo;
    }

    public Page<TokenTransfer> getTransfersByChainAndAddress(String chainName, String address, int page, int size) {
        return transferRepo.findByChainNameAndAddress(chainName, address.toLowerCase(), PageRequest.of(page, size));
    }

    public long getTotalTransfers(String chainName) {
        return transferRepo.countByChainName(chainName);
    }
}