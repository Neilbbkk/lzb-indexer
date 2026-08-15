package com.lzb.indexer.service;

import com.lzb.indexer.config.ChainProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * TokenService 单元测试。
 * Web3j 调用全部 mock，只测业务逻辑层。
 */
@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock
    private ChainProperties chainProperties;

    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        // 空链配置：TokenService 初始化时不会尝试连接任何 RPC
        when(chainProperties.getChains()).thenReturn(Collections.emptyList());
        tokenService = new TokenService(chainProperties);
        tokenService.init();
    }

    @Test
    void shouldInitializeWithoutChains() {
        // 空配置下服务应正常初始化不抛异常
        assertNotNull(tokenService);
    }

}
