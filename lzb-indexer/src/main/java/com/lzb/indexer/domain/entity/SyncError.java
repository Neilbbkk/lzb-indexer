package com.lzb.indexer.domain.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 同步错误记录。
 *
 * 扫链过程中出现的异常（RPC 超时、解码失败等）都记到这里，方便排查问题。
 */
@Entity
@Table(name = "sync_errors")
public class SyncError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 出错时正在扫描的区块号 */
    @Column(name = "block_number", nullable = false)
    private Long blockNumber;

    /** 错误详情 */
    @Column(name = "error_message", nullable = false, columnDefinition = "TEXT")
    private String errorMessage;

    /** 入库时间 */
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public SyncError() {}

    public SyncError(Long blockNumber, String errorMessage) {
        this.blockNumber = blockNumber;
        this.errorMessage = errorMessage;
    }

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getBlockNumber() { return blockNumber; }
    public String getErrorMessage() { return errorMessage; }
}
