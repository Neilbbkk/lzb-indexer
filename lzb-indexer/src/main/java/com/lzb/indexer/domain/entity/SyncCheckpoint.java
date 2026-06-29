package com.lzb.indexer.domain.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 同步检查点。
 *
 * 记录每条链（每个合约地址）的扫描进度。
 * 程序重启后从 last_scanned_block + 1 继续扫，不会重复扫描。
 */
@Entity
@Table(name = "sync_checkpoints")
public class SyncCheckpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 被扫描的合约地址 */
    @Column(name = "contract_address", nullable = false, unique = true, length = 42)
    private String contractAddress;

    /** 已扫描到的最新区块号 */
    @Column(name = "last_scanned_block", nullable = false)
    private Long lastScannedBlock;

    /** 已扫描到的最新交易索引（预留） */
    @Column(name = "last_scanned_tx_index")
    private Integer lastScannedTxIndex;

    /** 是否启用 reorg 保护 */
    @Column(name = "is_reorg_protected")
    private Boolean reorgProtected;

    /** 链标识 */
    @Column(name = "chain_name", nullable = false)
    private String chainName;

    /** 最后更新时间 */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public SyncCheckpoint() {}

    public SyncCheckpoint(String contractAddress, Long lastScannedBlock, String chainName) {
        this.contractAddress = contractAddress;
        this.lastScannedBlock = lastScannedBlock;
        this.lastScannedTxIndex = 0;
        this.reorgProtected = false;
        this.chainName = chainName;
    }

    @PrePersist
    @PreUpdate
    protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getContractAddress() { return contractAddress; }
    public Long getLastScannedBlock() { return lastScannedBlock; }
    public void setLastScannedBlock(Long v) { this.lastScannedBlock = v; }
    public Integer getLastScannedTxIndex() { return lastScannedTxIndex; }
    public void setLastScannedTxIndex(Integer v) { this.lastScannedTxIndex = v; }
    public Boolean getReorgProtected() { return reorgProtected; }
    public void setReorgProtected(Boolean v) { this.reorgProtected = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public String getChainName() { return chainName; }
}
