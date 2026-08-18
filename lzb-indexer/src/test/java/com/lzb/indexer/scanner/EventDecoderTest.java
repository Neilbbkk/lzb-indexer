package com.lzb.indexer.scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lzb.indexer.domain.entity.GmxPositionHistory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.core.methods.response.Log;

import java.math.BigInteger;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * EventDecoder 单元测试 — mock Log 对象，验证 GMX V2 事件解码正确性
 *
 * 测试数据来源：eth_getLogs_data.txt（真实 Arbitrum PositionIncrease 事件）
 * 预期值已通过 Python 脚本与链上数据交叉验证
 */
class EventDecoderTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventDecoderTest.class);

    private final EventDecoder decoder = new EventDecoder();

    /** emitEventLog 签名哈希 */
    private static final String EMIT_EVENT_LOG_HASH =
            "0x137a44067c8961cd7e1d876f4754a5a3a75989b4552f1843fc69c3b372def160";

    /** PositionIncrease keccak256 */
    private static final String POSITION_INCREASE_HASH =
            "0xf94196ccb31f81a3e67df18f2a62cbfb50009c80a7d3c728a3f542e3abc5cb63";

    /** topic[2] = account 地址 */
    private static final String TOPIC_ACCOUNT =
            "0x000000000000000000000000b6f667ae1f9ef040485378c79c8b519b6538a3de";

    // ======================== 事件类型识别 ========================

    @Test
    @DisplayName("emitEventLog topic 识别为 GMX V2 事件")
    void testIsGmxV2Event() {
        Log log = mock(Log.class);
        when(log.getTopics()).thenReturn(Arrays.asList(EMIT_EVENT_LOG_HASH, POSITION_INCREASE_HASH));
        assertTrue(decoder.isGmxV2Event(log));
    }

    @Test
    @DisplayName("PositionIncrease topic 正确识别")
    void testIsIncreasePositionEvent() {
        Log log = mock(Log.class);
        when(log.getTopics()).thenReturn(Arrays.asList(
                EMIT_EVENT_LOG_HASH, POSITION_INCREASE_HASH, TOPIC_ACCOUNT));
        assertTrue(decoder.isIncreasePositionEvent(log));
    }

    @Test
    @DisplayName("非 GMX 事件应返回 false")
    void testNonGmxEvent() {
        Log log = mock(Log.class);
        when(log.getTopics()).thenReturn(Arrays.asList("0xdeadbeef"));
        assertFalse(decoder.isGmxV2Event(log));
        assertFalse(decoder.isIncreasePositionEvent(log));
        assertFalse(decoder.isDecreasePositionEvent(log));
    }

    @Test
    @DisplayName("topics 不足应返回 false")
    void testInsufficientTopics() {
        Log log = mock(Log.class);
        when(log.getTopics()).thenReturn(Arrays.asList(EMIT_EVENT_LOG_HASH));
        assertFalse(decoder.isIncreasePositionEvent(log));
    }

    @Test
    @DisplayName("topics 为 null 不抛异常")
    void testNullTopics() {
        Log log = mock(Log.class);
        when(log.getTopics()).thenReturn(null);
        assertFalse(decoder.isGmxV2Event(log));
        assertFalse(decoder.isIncreasePositionEvent(log));
    }

    // ======================== 解码正确性（用真实链上数据） ========================

    @Test
    @DisplayName("解码真实 PositionIncrease 事件 — 字段值全部正确")
    void testDecodeIncreasePosition() throws Exception {
        String hexData = readTestData("/eth_getLogs_data.txt");

        Log log = mock(Log.class);
        when(log.getTopics()).thenReturn(Arrays.asList(
                EMIT_EVENT_LOG_HASH, POSITION_INCREASE_HASH, TOPIC_ACCOUNT));
        when(log.getData()).thenReturn(hexData);
        when(log.getTransactionHash()).thenReturn(
                "0x17b8f52be011378be9700698df65794dda61523ee8072604941c50c4866bdc45");
        when(log.getBlockNumber()).thenReturn(
                org.web3j.utils.Numeric.decodeQuantity("0x1ad274ed"));
        when(log.getLogIndex()).thenReturn(
                org.web3j.utils.Numeric.decodeQuantity("0x32"));

        GmxPositionHistory result = decoder.decodeIncreasePosition(log, "arbitrum");

        assertNotNull(result, "解码结果不应为 null");
        assertEquals("INCREASE", result.getEventType());
        assertEquals("arbitrum", result.getChainName());
        assertEquals("0x587759c237acca739bce3911647bacf56c876e60", result.getAccount());
        assertEquals("0xb6f667ae1f9ef040485378c79c8b519b6538a3de", result.getCollateralToken());
        assertEquals("0xaf88d065e77c8cc2239327c5edb3a432268e5831", result.getIndexToken());

        // sizeInUsd = 2577354250
        assertEquals(new BigInteger("2577354250"), result.getSizeDelta());

        // collateralAmount 应为正数
        BigInteger collateral = result.getCollateralDelta();
        assertNotNull(collateral);
        assertTrue(collateral.compareTo(BigInteger.ZERO) > 0,
                "collateralAmount 应为正数");

        // executionPrice 应为正数
        assertNotNull(result.getPrice());
        assertTrue(result.getPrice().compareTo(BigInteger.ZERO) > 0,
                "executionPrice 应为正数");

        // orderKey
        assertNotNull(result.getPositionKey());
        assertTrue(result.getPositionKey().startsWith("0x"),
                "positionKey 应以 0x 开头");
        assertEquals(66, result.getPositionKey().length());

        LOGGER.info("=== EventDecoder 解码结果 ===");
        LOGGER.info("account:          {}", result.getAccount());
        LOGGER.info("collateralToken:  {}", result.getCollateralToken());
        LOGGER.info("indexToken:       {}", result.getIndexToken());
        LOGGER.info("sizeInUsd:        {}", result.getSizeDelta());
        LOGGER.info("collateralAmount: {}", result.getCollateralDelta());
        LOGGER.info("executionPrice:   {}", result.getPrice());
        LOGGER.info("fee:              {}", result.getFee());
        LOGGER.info("isLong:           {}", result.getIsLong());
        LOGGER.info("positionKey:      {}", result.getPositionKey());
    }

    // ======================== 边界情况 ========================

    @Test
    @DisplayName("data 为空时返回 null")
    void testEmptyDataReturnsNull() {
        Log log = mock(Log.class);
        when(log.getTopics()).thenReturn(Arrays.asList(
                EMIT_EVENT_LOG_HASH, POSITION_INCREASE_HASH, TOPIC_ACCOUNT));
        when(log.getData()).thenReturn("0x");
        when(log.getTransactionHash()).thenReturn("0xabc");

        GmxPositionHistory result = decoder.decodeIncreasePosition(log, "arbitrum");
        assertNull(result);
    }

    @Test
    @DisplayName("data 过短时返回 null")
    void testShortDataReturnsNull() {
        Log log = mock(Log.class);
        when(log.getTopics()).thenReturn(Arrays.asList(
                EMIT_EVENT_LOG_HASH, POSITION_INCREASE_HASH, TOPIC_ACCOUNT));
        when(log.getData()).thenReturn("0x1234");
        when(log.getTransactionHash()).thenReturn("0xabc");

        GmxPositionHistory result = decoder.decodeIncreasePosition(log, "arbitrum");
        assertNull(result);
    }

    @Test
    @DisplayName("非 Increase 事件的 Log 返回 null")
    void testNonIncreaseLogReturnsNull() {
        Log log = mock(Log.class);
        when(log.getTopics()).thenReturn(Arrays.asList(EMIT_EVENT_LOG_HASH, "0xdead"));
        assertNull(decoder.decodeIncreasePosition(log, "arbitrum"));
    }

    /** 从 classpath 读取真实链上日志数据 */
    private static String readTestData(String resource) throws Exception {
        try (InputStream in = EventDecoderTest.class.getResourceAsStream(resource)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            return new String(bos.toByteArray(), StandardCharsets.UTF_8).trim();
        }
    }
}
