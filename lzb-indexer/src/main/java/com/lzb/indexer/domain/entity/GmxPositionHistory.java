package com.lzb.indexer.domain.entity;

import javax.persistence.*;
import java.math.BigInteger;
import java.time.LocalDateTime;

/**
 * 【GMX 仓位事件流水表】
 *
 * 这是 GMX V2 最核心的原始数据表。每条记录对应链上 EventEmitter 发出的一条
 * PositionIncrease 或 PositionDecrease 事件日志。
 *
 * 设计思路（事件溯源 / Event Sourcing）：
 *   不直接存"当前仓位状态"，而是把每一笔开仓/加仓/减仓/平仓/爆仓事件都原样记下来。
 *   当前仓位状态（gmx_positions 表）由这些事件"重放"计算得出。
 *
 * 好处：
 *   1. 不可篡改 —— 原始事件一旦上链就不可修改，流水表 = 链上事实的完整副本
 *   2. 可追溯 —— 任何时候都能查"这个仓位历史上经历了哪些操作"
 *   3. 可修复 —— 如果聚合表（gmx_positions）算错了，清掉重放流水即可恢复
 *   4. 可审计 —— 每一笔 size/collateral 的变化都有据可查
 *
 * 去重：tx_hash + log_index + chain_name 联合唯一索引。
 */
@Entity
@Table(name = "gmx_position_history",
       uniqueConstraints = @UniqueConstraint(columnNames = {"tx_hash", "log_index", "chain_name"}),
       indexes = {
           @Index(name = "idx_gmx_ph_account", columnList = "chain_name, account, block_number"),
           @Index(name = "idx_gmx_ph_key", columnList = "chain_name, position_key")
       })
public class GmxPositionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 事件类型：INCREASE（开仓/加仓）、DECREASE（减仓/平仓）、LIQUIDATE（爆仓） */
    @Column(name = "event_type", nullable = false, length = 20)
    private String eventType;

    /** 交易哈希（链上唯一标识） */
    @Column(name = "tx_hash", nullable = false, length = 66)
    private String txHash;

    /** 所在区块号 */
    @Column(name = "block_number", nullable = false)
    private Long blockNumber;

    /** 事件日志在交易内的索引（一个交易可能触发多个事件） */
    @Column(name = "log_index", nullable = false)
    private Integer logIndex;

    /** 仓位唯一标识（GMX V2 中就是 orderKey） */
    @Column(name = "position_key", nullable = true, length = 66)
    private String positionKey;

    /** 交易者钱包地址 */
    @Column(name = "account", nullable = false, length = 42)
    private String account;

    /** 抵押品代币地址（如 USDC） */
    @Column(name = "collateral_token", nullable = false, length = 42)
    private String collateralToken;

    /** 交易对/市场标识（GMX V2 中为 market 地址） */
    @Column(name = "index_token", nullable = false, length = 42)
    private String indexToken;

    /** 抵押品变化量（开仓为正，减仓为负，爆仓时被全部扣除） */
    @Column(name = "collateral_delta", nullable = false, columnDefinition = "NUMERIC")
    private BigInteger collateralDelta;

    /** 仓位大小变化量（USD 计价，原始精度） */
    @Column(name = "size_delta", nullable = false, columnDefinition = "NUMERIC")
    private BigInteger sizeDelta;

    /** 多空方向：true = 做多，false = 做空 */
    @Column(name = "is_long", nullable = false)
    private Boolean isLong;

    /** 成交价（开仓/平仓/爆仓时的执行价格） */
    @Column(name = "price", nullable = false, columnDefinition = "NUMERIC")
    private BigInteger price;

    /** 手续费（原始精度） */
    @Column(name = "fee", columnDefinition = "NUMERIC")
    private BigInteger fee;

    /** 链标识 */
    @Column(name = "chain_name", nullable = false)
    private String chainName;

    /** 入库时间 */
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public GmxPositionHistory() {}

    public GmxPositionHistory(String eventType, String txHash, Long blockNumber, Integer logIndex,
                              String positionKey, String account, String collateralToken,
                              String indexToken, BigInteger collateralDelta, BigInteger sizeDelta,
                              Boolean isLong, BigInteger price, BigInteger fee, String chainName) {
        this.eventType = eventType;
        this.txHash = txHash;
        this.blockNumber = blockNumber;
        this.logIndex = logIndex;
        this.positionKey = positionKey;
        this.account = account;
        this.collateralToken = collateralToken;
        this.indexToken = indexToken;
        this.collateralDelta = collateralDelta;
        this.sizeDelta = sizeDelta;
        this.isLong = isLong;
        this.price = price;
        this.fee = fee;
        this.chainName = chainName;
    }

    @PrePersist
    protected void onCreate() { this.createdAt = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getEventType() { return eventType; }
    public String getTxHash() { return txHash; }
    public Long getBlockNumber() { return blockNumber; }
    public void setBlockNumber(Long v) { this.blockNumber = v; }
    public Integer getLogIndex() { return logIndex; }
    public String getPositionKey() { return positionKey; }
    public String getAccount() { return account; }
    public String getCollateralToken() { return collateralToken; }
    public String getIndexToken() { return indexToken; }
    public BigInteger getCollateralDelta() { return collateralDelta; }
    public BigInteger getSizeDelta() { return sizeDelta; }
    public Boolean getIsLong() { return isLong; }
    public BigInteger getPrice() { return price; }
    public BigInteger getFee() { return fee; }
    public String getChainName() { return chainName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
