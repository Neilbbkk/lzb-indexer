package com.lzb.indexer.domain.repository;

import com.lzb.indexer.domain.entity.GmxPositionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GmxPositionHistoryRepository extends JpaRepository<GmxPositionHistory, Long> {

    List<GmxPositionHistory> findByChainNameAndBlockNumberBetweenOrderByBlockNumberAsc(
            String chainName, Long startBlock, Long endBlock);

    boolean existsByTxHashAndLogIndexAndChainName(String txHash, Integer logIndex, String chainName);

    void deleteByChainNameAndBlockNumberGreaterThan(String chainName, Long blockNumber);

    void deleteByChainNameAndBlockNumberGreaterThanEqual(String chainName, Long blockNumber);

    long countByChainName(String chainName);
}