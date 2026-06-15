package com.lzb.indexer.dto;

import com.lzb.indexer.domain.entity.TokenTransfer;

import java.math.BigInteger;

public class TransferResponse {

    private String txHash;
    private Long blockNumber;
    private Integer logIndex;
    private String from;
    private String to;
    private BigInteger value;
    private String valueFormatted;

    public static TransferResponse from(TokenTransfer t) {
        TransferResponse r = new TransferResponse();
        r.txHash = t.getTxHash();
        r.blockNumber = t.getBlockNumber();
        r.logIndex = t.getLogIndex();
        r.from = t.getFromAddress();
        r.to = t.getToAddress();
        r.value = t.getAmount();
        r.valueFormatted = t.getAmount().divide(BigInteger.TEN.pow(18)) + " LZB";
        return r;
    }

    public String getTxHash() { return txHash; }
    public Long getBlockNumber() { return blockNumber; }
    public Integer getLogIndex() { return logIndex; }
    public String getFrom() { return from; }
    public String getTo() { return to; }
    public BigInteger getAmount() { return value; }
    public String getValueFormatted() { return valueFormatted; }
}