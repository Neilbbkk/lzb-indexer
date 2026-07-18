package com.lzb.indexer.domain.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 链同步错误记录，持久化扫描过程中的所有异常，方便事后排查。
 */
@Entity
@Table(name = "sync_errors")
public class SyncError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 出错的链 */
    @Column(name = "chain_name", nullable = false)
    private String chainName;

    /** 出错时正在扫描的区块号 */
    @Column(name = "block_number", nullable = false)
    private Long blockNumber;

    /** 错误类型：RPC / DECODE / DB */
    @Column(name = "error_type", nullable = false, length = 20)
    private String errorType;

    /** 错误详情 */
    @Column(name = "error_message", nullable = false, columnDefinition = "TEXT")
    private String errorMessage;

    /** 入库时间 */
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public SyncError() {}

    public SyncError(String chainName, Long blockNumber, String errorType, String errorMessage) {
        this.chainName = chainName;
        this.blockNumber = blockNumber;
        this.errorType = errorType;
        this.errorMessage = errorMessage;
    }

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getChainName() { return chainName; }
    public Long getBlockNumber() { return blockNumber; }
    public String getErrorType() { return errorType; }
    public String getErrorMessage() { return errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}