package com.lzb.indexer.service;

import com.lzb.indexer.dto.NewEventsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 监听 NewEventsEvent，通过 STOMP WebSocket 推送给前端。
 * 前端收到后立即调 REST 接口拉取最新数据（推送通知 + REST 拉取的混合模式）。
 */
@Component
public class EventPushListener {

    private static final Logger log = LoggerFactory.getLogger(EventPushListener.class);

    private final SimpMessagingTemplate messagingTemplate;

    public EventPushListener(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * 异步处理，不阻塞扫描线程。
     * 映射 chain → 前端 tab：
     *   sepolia              → transfers
     *   ethereum-uniswap     → swaps
     *   arbitrum-gmx-vault   → gmx
     */
    @Async
    @EventListener
    public void onNewEvents(NewEventsEvent event) {
        String tab = mapToTab(event.getChain());
        Map<String, Object> msg = new HashMap<>();
        msg.put("tab", tab);
        msg.put("chain", event.getChain());
        msg.put("type", event.getEventType());
        msg.put("count", event.getCount());
        msg.put("timestamp", System.currentTimeMillis());

        messagingTemplate.convertAndSend("/topic/events", msg);
        log.debug("WS push → {}: +{} {}", tab, event.getCount(), event.getEventType());
    }

    private String mapToTab(String chain) {
        if (chain == null) return "transfers";
        if (chain.contains("sepolia")) return "transfers";
        if (chain.contains("uniswap")) return "swaps";
        if (chain.contains("gmx") || chain.contains("arbitrum")) return "gmx";
        return "transfers";
    }
}