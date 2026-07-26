package com.lzb.indexer.domain.repository;

import com.lzb.indexer.domain.entity.SwapEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Uniswap V2 Swap 事件数据访问。
 */
@Repository
public interface SwapEventRepository extends JpaRepository<SwapEvent, Long> {

    /** 按 txHash + logIndex + chainName 判断是否已入库 */
    boolean existsByTxHashAndLogIndexAndChainName(String txHash, Integer logIndex, String chainName);

    /** reorg 回滚时删除 >= 指定区块号的所有记录 */
    void deleteByChainNameAndBlockNumberGreaterThanEqual(String chainName, Long blockNumber);

    /** 按区块范围统计，用于数据校验 */
    List<SwapEvent> findByChainNameAndBlockNumberBetween(String chainName, Long from, Long to);

    /** 分页查询某链所有 Swap */
    Page<SwapEvent> findByChainName(String chainName, Pageable pageable);

    /** 分页查询某链某 sender 的 Swap */
    Page<SwapEvent> findByChainNameAndSender(String chainName, String sender, Pageable pageable);

    /** 按区块号倒序，取最新 N 条 */
    Page<SwapEvent> findByChainNameOrderByBlockNumberDesc(String chainName, Pageable pageable);
}