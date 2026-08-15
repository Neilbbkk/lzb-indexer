package com.lzb.indexer.domain.repository;

import com.lzb.indexer.domain.entity.GmxPositionHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GmxPositionHistoryRepository extends JpaRepository<GmxPositionHistory, Long> {

    List<GmxPositionHistory> findByChainNameAndBlockNumberBetweenOrderByBlockNumberAsc(
            String chainName, Long startBlock, Long endBlock);

    List<GmxPositionHistory> findByChainNameAndPositionKeyOrderByBlockNumberAscLogIndexAsc(
            String chainName, String positionKey);

    List<GmxPositionHistory> findByChainNameAndBlockNumberGreaterThanEqual(
            String chainName, Long blockNumber);

    boolean existsByTxHashAndLogIndexAndChainName(String txHash, Integer logIndex, String chainName);

    void deleteByChainNameAndBlockNumberGreaterThan(String chainName, Long blockNumber);

    void deleteByChainNameAndBlockNumberGreaterThanEqual(String chainName, Long blockNumber);

    long countByChainName(String chainName);

    /** 按区块号倒序分页查询，用于 Dashboard 最近 GMX 历史 */
    Page<GmxPositionHistory> findByChainNameOrderByBlockNumberDesc(String chainName, Pageable pageable);
}
