package com.lzb.indexer.domain.entity;

import javax.persistence.*;
import java.math.BigInteger;
import java.time.LocalDateTime;

/**
 * GMX 仓位聚合快照
 *
 * 采用"事件溯源"模式：gmx_position_history 存事件流水，gmx_positions 存当前快照
 * 以 position_key 作为业务主键，通过叠加 size 和 collateral 实现快照更新
 *
 * 和 gmx_position_history 的关系：
 *   history 做"加减法"：INCREASE=+100, DECREASE=-50, DECREASE=-50
 *   positions 做"快照"：叠加后等于 0，即 100-50-50=0，状态变为 CLOSED
 *
 * 聚合过程：
 *   每个 history 事件由 GmxPositionService.apply() 处理：
 *     1. 根据 position_key 查找或创建对应的 GmxPosition
 *     2. 根据 eventType 分别处理：
 *        INCREASE   -> 增加 size 和 collateral
 *        DECREASE   -> 减少 size 和 collateral（delta 已由 EventDecoder 取负）
 *        LIQUIDATE  -> 将 size 直接清零
 *     3. 如果 size 降到 0，则 status = CLOSED
 *
 * 状态机：
 *   OPEN -> (size 降到 0) -> CLOSED
 *   OPEN -> (被清算)      -> LIQUIDATED
 */
@Entity
@Table(name = "gmx_positions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"chain_name", "position_key"}),
       indexes = {
           @Index(name = "idx_gmx_pos_account", columnList = "chain_name, account, status"),
           @Index(name = "idx_gmx_pos_market", columnList = "chain_name, index_token, status")
       })
public class GmxPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 仓位唯一标识，对应 GMX V2 的 orderKey */
    @Column(name = "position_key", nullable = false, length = 66)
    private String positionKey;

    /** 交易账户地址 */
    @Column(name = "account", nullable = false, length = 42)
    private String account;

    /** 抵押代币地址（如 USDC） */
    @Column(name = "collateral_token", nullable = false, length = 42)
    private String collateralToken;

    /** 指数代币/market 地址 */
    @Column(name = "index_token", nullable = false, length = 42)
    private String indexToken;

    /** 做多/做空 */
    @Column(name = "is_long", nullable = false)
    private Boolean isLong;

    /** 当前仓位大小（USD 计），仓平后为 0 */
    @Column(name = "size", nullable = false, columnDefinition = "NUMERIC")
    private BigInteger size;

    /** 当前抵押品金额（USD 计） */
    @Column(name = "collateral", nullable = false, columnDefinition = "NUMERIC")
    private BigInteger collateral;

    /** 加权平均开仓价格 */
    @Column(name = "average_price", nullable = false, columnDefinition = "NUMERIC")
    private BigInteger averagePrice;

    /** 累计手续费 */
    @Column(name = "total_fee", columnDefinition = "NUMERIC")
    private BigInteger totalFee;

    /** 开仓区块号 */
    @Column(name = "entry_block", nullable = false)
    private Long entryBlock;

    /** 开仓交易哈希 */
    @Column(name = "entry_tx", nullable = false, length = 66)
    private String entryTx;

    /** 最后更新区块号 */
    @Column(name = "last_update_block", nullable = false)
    private Long lastUpdateBlock;

    /** 最后更新交易哈希 */
    @Column(name = "last_update_tx", nullable = false, length = 66)
    private String lastUpdateTx;

    /** 仓位状态 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    public enum Status {
        OPEN,       // 持仓中
        CLOSED,     // 已平仓
        LIQUIDATED  // 已清算
    }

    /** 所属链 */
    @Column(name = "chain_name", nullable = false)
    private String chainName;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public GmxPosition() {}

    public GmxPosition(String positionKey, String account, String collateralToken,
                       String indexToken, Boolean isLong, BigInteger size,
                       BigInteger collateral, BigInteger averagePrice, BigInteger fee,
                       Long entryBlock, String entryTx, String chainName) {
        this.positionKey = positionKey;
        this.account = account;
        this.collateralToken = collateralToken;
        this.indexToken = indexToken;
        this.isLong = isLong;
        this.size = size;
        this.collateral = collateral;
        this.averagePrice = averagePrice;
        this.totalFee = fee;
        this.entryBlock = entryBlock;
        this.entryTx = entryTx;
        this.lastUpdateBlock = entryBlock;
        this.lastUpdateTx = entryTx;
        this.status = "OPEN";
        this.chainName = chainName;
    }

    /** 工厂方法：创建新仓位，同时填充 entryTx 防止之前 entryTx 为空的 bug */
    public static GmxPosition open(String positionKey, String account,
                                   String collateralToken, String indexToken,
                                   Boolean isLong, BigInteger size, BigInteger collateral,
                                   BigInteger price, BigInteger fee, Long blockNumber,
                                   String txHash, String chainName) {
        return new GmxPosition(positionKey, account, collateralToken, indexToken,
                isLong, size, collateral, price, fee, blockNumber, txHash, chainName);
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() { this.updatedAt = LocalDateTime.now(); }

    // ===== 业务方法 =====

    /** 加仓：更新 size、collateral 和加权平均价 */
    public void applyIncrease(BigInteger sizeDelta, BigInteger collateralDelta,
                              BigInteger price, BigInteger fee, Long blockNumber) {
        BigInteger newSize = this.size.add(sizeDelta);
        if (newSize.compareTo(BigInteger.ZERO) <= 0) {
            this.averagePrice = price;
        } else {
            BigInteger oldValue = this.size.multiply(this.averagePrice);
            BigInteger newValue = sizeDelta.multiply(price);
            this.averagePrice = oldValue.add(newValue).divide(newSize);
        }
        this.size = newSize;
        this.collateral = this.collateral.add(collateralDelta);
        if (fee != null) {
            this.totalFee = (this.totalFee != null ? this.totalFee : BigInteger.ZERO).add(fee);
        }
    }

    /** 平仓 */
    public void markClosed(Long blockNumber) {
        this.lastUpdateBlock = blockNumber;
        this.status = "CLOSED";
    }

    /** 清算 */
    public void markLiquidated(Long blockNumber) {
        this.lastUpdateBlock = blockNumber;
        this.status = "LIQUIDATED";
    }

    /** 更新最后修改记录 */
    public void touch(Long blockNumber, String txHash) {
        this.lastUpdateBlock = blockNumber;
        this.lastUpdateTx = txHash;
    }

    // ===== getters/setters =====

    public Long getId() { return id; }
    public String getPositionKey() { return positionKey; }
    public String getAccount() { return account; }
    public String getCollateralToken() { return collateralToken; }
    public String getIndexToken() { return indexToken; }
    public Boolean getIsLong() { return isLong; }
    public BigInteger getSize() { return size; }
    public BigInteger getCollateral() { return collateral; }
    public BigInteger getAveragePrice() { return averagePrice; }
    public BigInteger getTotalFee() { return totalFee; }
    public Long getEntryBlock() { return entryBlock; }
    public String getEntryTx() { return entryTx; }
    public Long getLastUpdateBlock() { return lastUpdateBlock; }
    public String getLastUpdateTx() { return lastUpdateTx; }
    public String getStatus() { return status; }
    public String getChainName() { return chainName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}