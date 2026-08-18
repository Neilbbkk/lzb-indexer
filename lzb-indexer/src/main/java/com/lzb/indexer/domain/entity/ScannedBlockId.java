package com.lzb.indexer.domain.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for scanned_blocks: the same block number on different
 * chains must not collide.
 */
public class ScannedBlockId implements Serializable {

    private Long blockNumber;
    private String chainName;

    public ScannedBlockId() {}

    public ScannedBlockId(Long blockNumber, String chainName) {
        this.blockNumber = blockNumber;
        this.chainName = chainName;
    }

    public Long getBlockNumber() { return blockNumber; }
    public void setBlockNumber(Long v) { this.blockNumber = v; }
    public String getChainName() { return chainName; }
    public void setChainName(String v) { this.chainName = v; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScannedBlockId)) return false;
        ScannedBlockId that = (ScannedBlockId) o;
        return Objects.equals(blockNumber, that.blockNumber)
                && Objects.equals(chainName, that.chainName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(blockNumber, chainName);
    }
}
