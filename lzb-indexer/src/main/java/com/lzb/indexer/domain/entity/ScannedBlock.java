package com.lzb.indexer.domain.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "scanned_blocks")
public class ScannedBlock {

    @Id
    @Column(name = "block_number", nullable = false)
    private Long blockNumber;

    @Column(name = "block_hash", nullable = false, length = 66)
    private String blockHash;

    @Column(name = "chain_name", nullable = false)
    private String chainName;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public ScannedBlock() {}

    public ScannedBlock(Long blockNumber, String blockHash, String chainName) {
        this.blockNumber = blockNumber;
        this.blockHash = blockHash;
        this.chainName = chainName;
    }

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }

    public Long getBlockNumber() { return blockNumber; }
    public String getBlockHash() { return blockHash; }
    public String getChainName() { return chainName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}