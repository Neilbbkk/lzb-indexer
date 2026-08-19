package com.lzb.indexer.domain.repository;

import com.lzb.indexer.domain.entity.TokenTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface TokenTransferRepository extends JpaRepository<TokenTransfer, Long> {

    @Query("SELECT t FROM TokenTransfer t WHERE t.chainName = :chainName AND (t.fromAddress = :address OR t.toAddress = :address) ORDER BY t.blockNumber DESC")
    Page<TokenTransfer> findByChainNameAndAddress(@Param("chainName") String chainName,
                                                   @Param("address") String address,
                                                   Pageable pageable);

    List<TokenTransfer> findByChainNameAndBlockNumberBetweenOrderByBlockNumberAsc(
            String chainName, Long startBlock, Long endBlock);

    List<TokenTransfer> findByChainNameAndBlockNumberAndLogIndexGreaterThan(
            String chainName, Long blockNumber, Integer logIndex);

    @Transactional
    void deleteByChainNameAndBlockNumberGreaterThan(String chainName, Long blockNumber);

    @Transactional
    void deleteByChainNameAndBlockNumberGreaterThanEqual(String chainName, Long blockNumber);

    boolean existsByTxHashAndLogIndexAndChainName(String txHash, Integer logIndex, String chainName);

    long countByChainName(String chainName);

    /** 按区块号倒序分页查询，用于 Dashboard 最近转账 */
    Page<TokenTransfer> findByChainNameOrderByBlockNumberDesc(String chainName, Pageable pageable);
}