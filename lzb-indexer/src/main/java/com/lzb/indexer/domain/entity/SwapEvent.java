package com.lzb.indexer.domain.entity;

import javax.persistence.*;
import java.math.BigInteger;
import java.time.LocalDateTime;

/**
 * Uniswap V2 Swap 事件（流水表）。
 * 每条记录对应链上一个 Pair 合约的 Swap 事件日志。
 * 通过 tx_hash + log_index + chain_name 联合唯一去重。
 */
@Entity
@Table(name = "swap_events",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tx_hash", "log_index", "chain_name"}))
public class SwapEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 交易哈希 */
    @Column(name = "tx_hash", nullable = false, length = 66)
    private String txHash;

    /** 区块号 */
    @Column(name = "block_number", nullable = false)
    private Long blockNumber;

    /** 事件日志在交易内的索引（去重用） */
    @Column(name = "log_index", nullable = false)
    private Integer logIndex;

    /** 发起 swap 的地址（topic[1]，indexed） */
    @Column(name = "sender", nullable = false, length = 42)
    private String sender;

    /** 接收 swap 输出的地址（topic[2]，indexed） */
    @Column(name = "receiver", nullable = false, length = 42)
    private String receiver;

    /** token0 流入数量（原始精度） */
    @Column(name = "amount0_in", nullable = false, columnDefinition = "NUMERIC")
    private BigInteger amount0In;

    /** token1 流入数量（原始精度） */
    @Column(name = "amount1_in", nullable = false, columnDefinition = "NUMERIC")
    private BigInteger amount1In;

    /** token0 流出数量（原始精度） */
    @Column(name = "amount0_out", nullable = false, columnDefinition = "NUMERIC")
    private BigInteger amount0Out;

    /** token1 流出数量（原始精度） */
    @Column(name = "amount1_out", nullable = false, columnDefinition = "NUMERIC")
    private BigInteger amount1Out;

    /** 链标识 */
    @Column(name = "chain_name", nullable = false)
    private String chainName;

    /** 入库时间 */
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public SwapEvent() {}

    public SwapEvent(String txHash, Long blockNumber, Integer logIndex,
                     String sender, String receiver,
                     BigInteger amount0In, BigInteger amount1In,
                     BigInteger amount0Out, BigInteger amount1Out,
                     String chainName) {
        this.txHash = txHash;
        this.blockNumber = blockNumber;
        this.logIndex = logIndex;
        this.sender = sender;
        this.receiver = receiver;
        this.amount0In = amount0In;
        this.amount1In = amount1In;
        this.amount0Out = amount0Out;
        this.amount1Out = amount1Out;
        this.chainName = chainName;
    }

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getTxHash() { return txHash; }
    public Long getBlockNumber() { return blockNumber; }
    public Integer getLogIndex() { return logIndex; }
    public String getSender() { return sender; }
    public String getReceiver() { return receiver; }
    public BigInteger getAmount0In() { return amount0In; }
    public BigInteger getAmount1In() { return amount1In; }
    public BigInteger getAmount0Out() { return amount0Out; }
    public BigInteger getAmount1Out() { return amount1Out; }
    public String getChainName() { return chainName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}