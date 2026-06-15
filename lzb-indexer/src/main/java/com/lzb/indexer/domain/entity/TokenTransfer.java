package com.lzb.indexer.domain.entity;

import javax.persistence.*;
import java.math.BigInteger;
import java.time.LocalDateTime;

@Entity
@Table(name = "token_transfers",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tx_hash", "log_index", "chain_name"}))
public class TokenTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tx_hash", nullable = false, length = 66)
    private String txHash;

    @Column(name = "block_number", nullable = false)
    private Long blockNumber;

    @Column(name = "log_index", nullable = false)
    private Integer logIndex;

    @Column(name = "from_address", nullable = false, length = 42)
    private String fromAddress;

    @Column(name = "to_address", nullable = false, length = 42)
    private String toAddress;

    @Column(name = "amount", nullable = false, columnDefinition = "NUMERIC")
    private BigInteger amount;

    @Column(name = "chain_name", nullable = false)
    private String chainName;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public TokenTransfer() {}

    public TokenTransfer(String txHash, Long blockNumber, Integer logIndex,
                         String fromAddress, String toAddress, BigInteger value,
                         String chainName) {
        this.txHash = txHash;
        this.blockNumber = blockNumber;
        this.logIndex = logIndex;
        this.fromAddress = fromAddress;
        this.toAddress = toAddress;
        this.amount = value;
        this.chainName = chainName;
    }

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getTxHash() { return txHash; }
    public Long getBlockNumber() { return blockNumber; }
    public void setBlockNumber(Long v) { this.blockNumber = v; }
    public Integer getLogIndex() { return logIndex; }
    public String getFromAddress() { return fromAddress; }
    public String getToAddress() { return toAddress; }
    public BigInteger getAmount() { return amount; }
    public String getChainName() { return chainName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}