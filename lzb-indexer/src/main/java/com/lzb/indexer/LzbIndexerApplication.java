package com.lzb.indexer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAspectJAutoProxy
@EnableAsync
public class LzbIndexerApplication {
    public static void main(String[] args) {
        SpringApplication.run(LzbIndexerApplication.class, args);
    }
}