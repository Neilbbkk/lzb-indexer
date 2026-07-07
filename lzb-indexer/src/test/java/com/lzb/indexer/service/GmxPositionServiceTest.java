package com.lzb.indexer.service;

import com.lzb.indexer.domain.entity.GmxPosition;
import com.lzb.indexer.domain.entity.GmxPositionHistory;
import com.lzb.indexer.domain.repository.GmxPositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GmxPositionService 单元测试（H2 内存库）
 *
 * 测试事件溯源核心流程：
 *   INCREASE → position OPEN
 *   DECREASE → position CLOSED（size 降到 0）
 *   LIQUIDATE → position LIQUIDATED
 */
@DataJpaTest
@Import(GmxPositionService.class)
class GmxPositionServiceTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private GmxPositionRepository positionRepo;

    @Autowired
    private GmxPositionService service;

    private static final String CHAIN = "arbitrum";
    private static final String POS_KEY =
            "0x8c0be1c9d1c16883de91a2c96db19471980b7d039f912f15842e153306955df9";
    private static final String ACCOUNT = "0x587759c237acca739bce3911647bacf56c876e60";
    private static final String COLLATERAL = "0xb6f667ae1f9ef040485378c79c8b519b6538a3de";
    private static final String INDEX = "0xaf88d065e77c8cc2239327c5edb3a432268e5831";

    /** 66字符的合法 tx hash 模板 */
    private static final String TX =
            "0x17b8f52be011378be9700698df65794dda61523ee8072604941c50c4866bdc45";

    @BeforeEach
    void setUp() {
        positionRepo.deleteAll();
        em.flush();
    }

    // ======================== INCREASE → OPEN ========================

    @Test
    @DisplayName("首次 INCREASE 应创建 OPEN 仓位")
    void testIncreaseCreatesOpenPosition() {
        GmxPositionHistory event = new GmxPositionHistory(
                "INCREASE", TX, 100L, 0,
                POS_KEY, ACCOUNT, COLLATERAL, INDEX,
                new BigInteger("16756925009361970894660490769"),
                new BigInteger("2577354250"),
                false,
                new BigInteger("2662759596499704475000000"),
                BigInteger.ZERO,
                CHAIN);

        service.apply(event);
        em.flush();
        em.clear();

        Optional<GmxPosition> pos = positionRepo.findByChainNameAndPositionKey(CHAIN, POS_KEY);
        assertTrue(pos.isPresent(), "应创建新的 GmxPosition");

        GmxPosition p = pos.get();
        assertEquals(CHAIN, p.getChainName());
        assertEquals(POS_KEY, p.getPositionKey());
        assertEquals(ACCOUNT, p.getAccount());
        assertEquals(COLLATERAL, p.getCollateralToken());
        assertEquals(INDEX, p.getIndexToken());
        assertFalse(p.getIsLong());
        assertEquals(new BigInteger("2577354250"), p.getSize());
        assertEquals(new BigInteger("16756925009361970894660490769"), p.getCollateral());
        assertEquals(GmxPosition.Status.OPEN.name(), p.getStatus());
        assertEquals(Long.valueOf(100L), p.getEntryBlock());
        assertEquals(TX, p.getEntryTx());

        System.out.println("TEST1 PASSED: INCREASE -> OPEN, size=" + p.getSize());
    }

    // ======================== INCREASE + DECREASE → CLOSED ========================

    @Test
    @DisplayName("INCREASE 后全平 DECREASE → CLOSED")
    void testIncreaseThenDecreaseToZeroCloses() {
        BigInteger size = new BigInteger("5000000000");
        BigInteger collateral = new BigInteger("2000000000000000000000");

        service.apply(new GmxPositionHistory(
                "INCREASE", tx(1), 100L, 0,
                POS_KEY, ACCOUNT, COLLATERAL, INDEX,
                collateral, size, true,
                new BigInteger("1900000000000000000000"), BigInteger.ZERO, CHAIN));

        service.apply(new GmxPositionHistory(
                "DECREASE", tx(2), 200L, 1,
                POS_KEY, ACCOUNT, COLLATERAL, INDEX,
                collateral.negate(), size.negate(), true,
                new BigInteger("2000000000000000000000"), BigInteger.ZERO, CHAIN));
        em.flush();
        em.clear();

        Optional<GmxPosition> pos = positionRepo.findByChainNameAndPositionKey(CHAIN, POS_KEY);
        assertTrue(pos.isPresent());
        assertEquals(GmxPosition.Status.CLOSED.name(), pos.get().getStatus());
        assertTrue(pos.get().getSize().compareTo(BigInteger.ZERO) <= 0);

        System.out.println("TEST2 PASSED: INCREASE + DECREASE -> CLOSED");
    }

    // ======================== 部分平仓 ========================

    @Test
    @DisplayName("部分 DECREASE 后仓位仍为 OPEN")
    void testPartialDecreaseKeepsOpen() {
        BigInteger size = new BigInteger("5000000000");
        BigInteger collateral = new BigInteger("2000000000000000000000");

        service.apply(new GmxPositionHistory(
                "INCREASE", tx(3), 100L, 0,
                POS_KEY, ACCOUNT, COLLATERAL, INDEX,
                collateral, size, true,
                new BigInteger("1900000000000000000000"), BigInteger.ZERO, CHAIN));

        service.apply(new GmxPositionHistory(
                "DECREASE", tx(4), 200L, 1,
                POS_KEY, ACCOUNT, COLLATERAL, INDEX,
                new BigInteger("-800000000000000000000"),
                new BigInteger("-2000000000"), true,
                new BigInteger("1950000000000000000000"), BigInteger.ZERO, CHAIN));
        em.flush();
        em.clear();

        Optional<GmxPosition> pos = positionRepo.findByChainNameAndPositionKey(CHAIN, POS_KEY);
        assertTrue(pos.isPresent());
        assertEquals(GmxPosition.Status.OPEN.name(), pos.get().getStatus());
        assertEquals(new BigInteger("3000000000"), pos.get().getSize());

        System.out.println("TEST3 PASSED: 部分平仓 -> 仍 OPEN, size=" + pos.get().getSize());
    }

    // ======================== LIQUIDATE ========================

    @Test
    @DisplayName("LIQUIDATE 标记 LIQUIDATED 并清零")
    void testLiquidateMarksLiquidated() {
        BigInteger size = new BigInteger("5000000000");
        BigInteger collateral = new BigInteger("2000000000000000000000");

        service.apply(new GmxPositionHistory(
                "INCREASE", tx(5), 100L, 0,
                POS_KEY, ACCOUNT, COLLATERAL, INDEX,
                collateral, size, false,
                new BigInteger("1900000000000000000000"), BigInteger.ZERO, CHAIN));

        service.apply(new GmxPositionHistory(
                "LIQUIDATE", tx(6), 200L, 1,
                POS_KEY, ACCOUNT, COLLATERAL, INDEX,
                BigInteger.ZERO, BigInteger.ZERO, false,
                new BigInteger("1000000000000000000000"), BigInteger.ZERO, CHAIN));
        em.flush();
        em.clear();

        Optional<GmxPosition> pos = positionRepo.findByChainNameAndPositionKey(CHAIN, POS_KEY);
        assertTrue(pos.isPresent());
        assertEquals(GmxPosition.Status.LIQUIDATED.name(), pos.get().getStatus());
        assertTrue(pos.get().getSize().compareTo(BigInteger.ZERO) <= 0);

        System.out.println("TEST4 PASSED: LIQUIDATE -> LIQUIDATED");
    }

    // ======================== 多次加仓 ========================

    @Test
    @DisplayName("多次 INCREASE 累加 size 和 collateral")
    void testMultipleIncreasesAccumulate() {
        service.apply(new GmxPositionHistory(
                "INCREASE", tx(7), 100L, 0,
                POS_KEY, ACCOUNT, COLLATERAL, INDEX,
                new BigInteger("1000000"), new BigInteger("1000"), true,
                new BigInteger("100"), BigInteger.ZERO, CHAIN));

        service.apply(new GmxPositionHistory(
                "INCREASE", tx(8), 200L, 1,
                POS_KEY, ACCOUNT, COLLATERAL, INDEX,
                new BigInteger("500000"), new BigInteger("500"), true,
                new BigInteger("120"), BigInteger.ZERO, CHAIN));
        em.flush();
        em.clear();

        Optional<GmxPosition> pos = positionRepo.findByChainNameAndPositionKey(CHAIN, POS_KEY);
        assertTrue(pos.isPresent());
        assertEquals(new BigInteger("1500"), pos.get().getSize());
        assertEquals(new BigInteger("1500000"), pos.get().getCollateral());

        System.out.println("TEST5 PASSED: 多次加仓, size=1500, collateral=1500000");
    }

    // ======================== 查询方法 ========================

    @Test
    @DisplayName("getPositionsByAccount 过滤正确")
    void testQueryByAccount() {
        service.apply(new GmxPositionHistory(
                "INCREASE", tx(9), 100L, 0,
                POS_KEY, ACCOUNT, COLLATERAL, INDEX,
                BigInteger.ONE, BigInteger.ONE, true,
                BigInteger.ONE, BigInteger.ZERO, CHAIN));
        em.flush();
        em.clear();

        List<GmxPosition> results = service.getPositionsByAccount(CHAIN, ACCOUNT);
        assertFalse(results.isEmpty());
        assertEquals(ACCOUNT, results.get(0).getAccount());
    }

    @Test
    @DisplayName("countByChain 返回正确数量")
    void testCountByChain() {
        assertEquals(0, service.countByChain(CHAIN));

        service.apply(new GmxPositionHistory(
                "INCREASE", tx(10), 100L, 0,
                POS_KEY, ACCOUNT, COLLATERAL, INDEX,
                BigInteger.ONE, BigInteger.ONE, true,
                BigInteger.ONE, BigInteger.ZERO, CHAIN));
        em.flush();
        em.clear();

        assertEquals(1, service.countByChain(CHAIN));
    }

    /** 生成唯一 66 字符 tx hash：替换末尾2字符 */
    private static String tx(int n) {
        String hex = String.format("%02x", n);
        return TX.substring(0, 64) + hex;
    }
}