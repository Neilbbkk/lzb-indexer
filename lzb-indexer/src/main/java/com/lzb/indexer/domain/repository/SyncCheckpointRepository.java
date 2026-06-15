package com.lzb.indexer.domain.repository;

import com.lzb.indexer.domain.entity.SyncCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SyncCheckpointRepository extends JpaRepository<SyncCheckpoint, Long> {
    Optional<SyncCheckpoint> findByChainNameAndContractAddress(String chainName, String contractAddress);
}