package com.lzb.indexer.domain.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sync_errors")
public class SyncError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "block_number", nullable = false)
    private Long blockNumber;

    @Column(name = "error_message", nullable = false, columnDefinition = "TEXT")
    private String errorMessage;

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