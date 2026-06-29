package com.lzb.indexer.domain.entity;

import javax.persistence.*;
import java.math.BigInteger;
import java.time.LocalDateTime;

/**
 * ?GMX ????????
 *
 * ??"??"????????????????????????? position_key ????
 * ?????? size?collateral????????
 *
 * ? gmx_position_history?????????
 *   history ?"??"?????????+100?-50?-50?
 *   positions ?"??"????????0??? 100-50-50=0?????
 *
 * ???????????
 *   ????? history ???GmxPositionService.apply() ???
 *     1. ?? position_key ????????? GmxPosition
 *     2. ?? eventType ???????
 *        INCREASE   -> ?? size ? collateral
 *        DECREASE   -> ?? size ? collateral?delta ???????????
 *        LIQUIDATE  -> ?????size ??
 *     3. ?? size ?? ??0??? status = CLOSED
 *
 * ????
 *   OPEN -> (size ?? 0) -> CLOSED
 *   OPEN -> (???)      -> LIQUIDATED
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

    /** ???????GMX V2 orderKey? */
    @Column(name = "position_key", nullable = false, length = 66)
    private String positionKey;

    /** ??????? */
    @Column(name = "account", nullable = false, length = 42)
    private String account;

    /** ????????? USDC? */
    @Column(name = "collateral_token", nullable = false, length = 42)
    private String collateralToken;

    /** ???/???? */
    @Column(name = "index_token", nullable = false, length = 42)
    private String indexToken;

    /** ???? */
    @Column(name = "is_long", nullable = false)
    private Boolean isLong;

    /** ???????USD ?????????? 0 ????? */
    @Column(name = "size", nullable = false, columnDefinition = "NUMERIC")
    private BigInteger size;

    /** ????????????? */
    @Column(name = "collateral", nullable = false, columnDefinition = "NUMERIC")
    private BigInteger collateral;

    /** ???????????? */
    @Column(name = "average_price", nullable = false, columnDefinition = "NUMERIC")
    private BigInteger averagePrice;

    /** ???????? */
    @Column(name = "total_fee", columnDefinition = "NUMERIC")
    private BigInteger totalFee;

    /** ???????? */
    @Column(name = "entry_block", nullable = false)
    private Long entryBlock;

    /** ????????? */
    @Column(name = "entry_tx", nullable = false, length = 66)
    private String entryTx;

    /** ?????????? */
    @Column(name = "last_update_block", nullable = false)
    private Long lastUpdateBlock;

    /** ??????????? */
    @Column(name = "last_update_tx", nullable = false, length = 66)
    private String lastUpdateTx;

    /** ???? */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    public enum Status {
        OPEN,       // ???
        CLOSED,     // ???
        LIQUIDATED  // ???
    }

    /** ??? */
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

    /** ???????????? txHash ?? entryTx */
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

    // ===== ???? =====

    /** ????? size ? collateral??????? */
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

    /** ?? */
    public void markClosed(Long blockNumber) {
        this.lastUpdateBlock = blockNumber;
        this.status = "CLOSED";
    }

    /** ?? */
    public void markLiquidated(Long blockNumber) {
        this.lastUpdateBlock = blockNumber;
        this.status = "LIQUIDATED";
    }

    /** ???????? */
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
