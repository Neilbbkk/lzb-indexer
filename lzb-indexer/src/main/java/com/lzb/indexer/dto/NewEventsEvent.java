package com.lzb.indexer.dto;

/**
 * 扫描器入库新事件后发布的 Spring 事件。
 * 携带链名、事件类型、新增数量，供 WebSocket 监听器转发给前端。
 */
public class NewEventsEvent {

    /** 链标识（如 sepolia / ethereum-uniswap / arbitrum-gmx-vault） */
    private final String chain;

    /** 事件类型：transfer / swap / gmx */
    private final String eventType;

    /** 本轮新增条数 */
    private final int count;

    public NewEventsEvent(String chain, String eventType, int count) {
        this.chain = chain;
        this.eventType = eventType;
        this.count = count;
    }

    public String getChain() { return chain; }
    public String getEventType() { return eventType; }
    public int getCount() { return count; }
}