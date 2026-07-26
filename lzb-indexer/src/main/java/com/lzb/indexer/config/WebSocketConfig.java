package com.lzb.indexer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket 配置。
 * 前端通过 SockJS 连接 /ws，订阅 /topic/events 接收实时推送。
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 客户端订阅前缀：服务端 → 客户端
        registry.enableSimpleBroker("/topic");
        // 服务端接收前缀：客户端 → 服务端（本场景不用）
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // SockJS 端点，前端连接 ws://host:port/ws
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }
}