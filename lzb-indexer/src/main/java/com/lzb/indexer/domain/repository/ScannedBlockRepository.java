package com.lzb.indexer.domain.repository;

import com.lzb.indexer.domain.entity.ScannedBlock;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface ScannedBlockRepository extends JpaRepository<ScannedBlock, Long> {

    List<ScannedBlock> findByChainNameOrderByBlockNumberDesc(String chainName, Pageable pageable);

    List<ScannedBlock> findByChainNameOrderByBlockNumberAsc(String chainName, Pageable pageable);

    @Modifying
    @Transactional
    @Query("DELETE FROM ScannedBlock s WHERE s.blockNumber >= :blockNumber AND s.chainName = :chainName")
    void deleteByChainNameAndBlockNumberGreaterThanEqual(String chainName, Long blockNumber);
}