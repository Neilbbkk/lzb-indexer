package com.lzb.indexer.scanner;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 静态事件发布桥。
 * BlockScanner 不是 Spring Bean（由 Factory new 出来），
 * 通过这个静态桥来发布 Spring ApplicationEvent，无需改动构造函数。
 */
@Component
public class StaticEventPublisher {

    private static volatile ApplicationEventPublisher publisher;

    public StaticEventPublisher(ApplicationEventPublisher publisher) {
        StaticEventPublisher.publisher = publisher;
    }

    /** 任意地方调用，发布事件（空安全） */
    public static void publish(Object event) {
        ApplicationEventPublisher p = publisher;
        if (p != null) {
            p.publishEvent(event);
        }
    }
}