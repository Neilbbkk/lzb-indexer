package com.lzb.indexer.domain.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sync_checkpoints")
public class SyncCheckpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_address", nullable = false, unique = true, length = 42)
    private String contractAddress;

    @Column(name = "last_scanned_block", nullable = false)
    private Long lastScannedBlock;

    @Column(name = "last_scanned_tx_index")
    private Integer lastScannedTxIndex;

    @Column(name = "is_reorg_protected")
    private Boolean reorgProtected;

    @Column(name = "chain_name", nullable = false)
    private String chainName;

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