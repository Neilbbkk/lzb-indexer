package com.lzb.indexer.domain.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 已扫描区块记录。
 *
 * 每扫完一个区块就存一条，记录区块号和链上 hash。
 * 用途：reorg 检测——定期对比本地 hash 和链上 hash，不一致说明发生了链重组，触发回滚。
 */
@Entity
@Table(name = "scanned_blocks")
public class ScannedBlock {

    /** 区块号 */
    @Id
    @Column(name = "block_number", nullable = false)
    private Long blockNumber;

    /** 链上区块 hash（用于 reorg 校验） */
    @Column(name = "block_hash", nullable = false, length = 66)
    private String blockHash;

    /** 链标识（如 arbitrum-gmx-vault） */
    @Column(name = "chain_name", nullable = false)
    private String chainName;

    /** 入库时间 */
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
