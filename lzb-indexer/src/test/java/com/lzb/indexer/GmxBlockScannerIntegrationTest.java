package com.lzb.indexer;

import com.lzb.indexer.domain.entity.GmxPosition;
import com.lzb.indexer.domain.entity.GmxPositionHistory;
import com.lzb.indexer.domain.repository.GmxPositionHistoryRepository;
import com.lzb.indexer.domain.repository.GmxPositionRepository;
import com.lzb.indexer.scanner.BlockScanner;
import com.lzb.indexer.scanner.ScannerScheduler;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.*;
import org.web3j.abi.datatypes.generated.*;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.utils.Numeric;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GMX 仓位扫描集成测试。
 *
 * 使用 Anvil + TestGmxVault 合约，验证全链路：
 *   emitIncrease → scanner 扫到 → GmxPositionHistory 入库 → GmxPosition OPEN
 *   emitDecrease → scanner 扫到 → GmxPosition CLOSED
 *   emitLiquidate → scanner 扫到 → GmxPosition LIQUIDATED
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GmxBlockScannerIntegrationTest {

    private static final String ANVIL_RPC = "http://localhost:8545";
    private static final String CHAIN_NAME = "anvil-gmx";
    private static final String PROTOCOL = "GMX_VAULT";
    private static final String ANVIL_PRIVATE_KEY =
            "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    private static final String ANVIL_ADDRESS = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266";

    /** 固定仓位 key，便于跨测试共享 */
    private static final Bytes32 POSITION_KEY = new Bytes32(
            Numeric.hexStringToByteArray("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"));

    private static final String ACCOUNT = "0x70997970C51812dc3A010C7d01b50e0d17dc79C8";
    private static final String COLLATERAL = "0xaf88d065e77c8cC2239327C5EDb3A432268e5831"; // USDC
    private static final String INDEX = "0x82aF49447D8a07e3bd95BD0d56f35241523fBab1";       // WETH

    private static String contractAddress;
    private static Web3j web3j;
    private static Credentials credentials;

    @Autowired private ScannerScheduler scheduler;
    @Autowired private GmxPositionHistoryRepository historyRepo;
    @Autowired private GmxPositionRepository positionRepo;

    private BlockScanner blockScanner;

    // ======================== 合约部署 + 配置注入 ========================

    @DynamicPropertySource
    static void deployAndRegister(DynamicPropertyRegistry registry) throws Exception {
        web3j = Web3j.build(new HttpService(ANVIL_RPC));
        credentials = Credentials.create(ANVIL_PRIVATE_KEY);

        BigInteger chainId = web3j.ethChainId().send().getChainId();
        if (chainId.longValue() != 31337) {
            throw new RuntimeException("Expected Anvil (31337), got chain " + chainId);
        }

        contractAddress = deployContract();
        System.out.println("GMX test contract deployed at: " + contractAddress);

        registry.add("app.chains[0].name",             () -> CHAIN_NAME);
        registry.add("app.chains[0].protocol",          () -> PROTOCOL);
        registry.add("app.chains[0].rpc-url",           () -> ANVIL_RPC);
        registry.add("app.chains[0].contract-address",  () -> contractAddress);
        registry.add("app.chains[0].wallet-address",    () -> ANVIL_ADDRESS);
        registry.add("app.chains[0].private-key",       () -> ANVIL_PRIVATE_KEY);
        registry.add("app.chains[0].start-block",       () -> "0");
        registry.add("app.chains[0].page-size",         () -> "100");
        registry.add("app.chains[0].reorg-depth",       () -> "1");
    }

    private static String deployContract() throws Exception {
        String forgeRoot = Paths.get("src/test/solidity").toAbsolutePath().toString();
        ProcessBuilder pb = new ProcessBuilder(
                "forge", "create", "TestGmxVault", "--broadcast",
                "--rpc-url", ANVIL_RPC,
                "--private-key", ANVIL_PRIVATE_KEY,
                "--root", forgeRoot
        );
        pb.redirectErrorStream(true);
        Process p = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) output.append(line).append("\n");
        }
        p.waitFor(30, TimeUnit.SECONDS);
        for (String line : output.toString().split("\n")) {
            if (line.contains("Deployed to:")) {
                return line.substring(line.lastIndexOf(":") + 1).trim();
            }
        }
        throw new RuntimeException("forge create failed:\n" + output);
    }

    @BeforeEach
    void setUp() {
        this.blockScanner = scheduler.getScanners().get(0);
    }

    // ======================== 测试 1：开仓 → OPEN ========================

    @Test
    @Order(1)
    @DisplayName("emitIncrease 后 scanner 应扫到并创建 OPEN 仓位")
    void testScanFindsIncrease() throws Exception {
        // 编码 emitIncrease(bytes32,address,address,address,uint256,uint256,bool,uint256,uint256)
        Function func = new Function("emitIncrease",
                Arrays.asList(
                        POSITION_KEY,
                        new Address(ACCOUNT),
                        new Address(COLLATERAL),
                        new Address(INDEX),
                        new Uint256(new BigInteger("1000000000")),              // 1000 USDC
                        new Uint256(new BigInteger("5000000000000000000")),      // 5 ETH
                        new Bool(true),                                         // long
                        new Uint256(new BigInteger("2000000000000000000000")),   // price ~2000
                        new Uint256(BigInteger.ZERO)                            // fee
                ), Collections.emptyList());

        String data = FunctionEncoder.encode(func);
        sendSignedTx(data);
        Thread.sleep(500);

        // 推一个空块确保事件已入块
        mineEmptyBlock();
        Thread.sleep(300);

        blockScanner.scan();

        // 验证事件流水
        List<GmxPositionHistory> history = historyRepo.findByChainNameAndBlockNumberBetweenOrderByBlockNumberAsc(
                CHAIN_NAME, 0L, 100L);
        assertFalse(history.isEmpty(), "应扫到至少一条 GMX 事件");
        GmxPositionHistory evt = history.get(0);
        assertEquals("INCREASE", evt.getEventType());
        assertEquals(ACCOUNT.toLowerCase(), evt.getAccount());
        assertEquals(true, evt.getIsLong());

        // 验证持仓快照
        Optional<GmxPosition> pos = positionRepo.findByChainNameAndPositionKey(
                CHAIN_NAME, Numeric.toHexString(POSITION_KEY.getValue()));
        assertTrue(pos.isPresent(), "应创建持仓记录");
        assertEquals(GmxPosition.Status.OPEN, pos.get().getStatus());
        System.out.println("TEST1 PASSED: position OPEN, size=" + pos.get().getSize());
    }

    // ======================== 测试 2：全平 → CLOSED ========================

    @Test
    @Order(2)
    @DisplayName("emitDecrease 全平后 scanner 应标记 CLOSED")
    void testScanFindsDecreaseClose() throws Exception {
        Function func = new Function("emitDecrease",
                Arrays.asList(
                        POSITION_KEY,
                        new Address(ACCOUNT),
                        new Address(COLLATERAL),
                        new Address(INDEX),
                        new Uint256(new BigInteger("5000000000000000000")),      // sizeDelta
                        new Uint256(new BigInteger("5000000000000000000")),      // collateralDelta (本次不关心)
                        new Bool(true),
                        new Address(ACCOUNT),                                    // receiver
                        new Uint256(new BigInteger("2000000000000000000000")),
                        new Uint256(BigInteger.ZERO)
                ), Collections.emptyList());

        sendSignedTx(FunctionEncoder.encode(func));
        Thread.sleep(500);
        mineEmptyBlock();
        Thread.sleep(300);

        blockScanner.scan();

        Optional<GmxPosition> pos = positionRepo.findByChainNameAndPositionKey(
                CHAIN_NAME, Numeric.toHexString(POSITION_KEY.getValue()));
        assertTrue(pos.isPresent());
        assertEquals(GmxPosition.Status.CLOSED, pos.get().getStatus(),
                "全平后状态应为 CLOSED");
        System.out.println("TEST2 PASSED: position CLOSED");
    }

    // ======================== 测试 3：重开 → 爆仓 ========================

    @Test
    @Order(3)
    @DisplayName("重新开仓后 emitLiquidate 应标记 LIQUIDATED")
    void testScanFindsLiquidate() throws Exception {
        // 重新开仓
        Function increase = new Function("emitIncrease",
                Arrays.asList(
                        POSITION_KEY,
                        new Address(ACCOUNT),
                        new Address(COLLATERAL),
                        new Address(INDEX),
                        new Uint256(new BigInteger("500000000")),
                        new Uint256(new BigInteger("2000000000000000000")),      // 2 ETH
                        new Bool(false),                                        // short
                        new Uint256(new BigInteger("1900000000000000000000")),
                        new Uint256(BigInteger.ZERO)
                ), Collections.emptyList());

        sendSignedTx(FunctionEncoder.encode(increase));
        Thread.sleep(500);
        mineEmptyBlock();
        Thread.sleep(300);
        blockScanner.scan();

        Optional<GmxPosition> reopened = positionRepo.findByChainNameAndPositionKey(
                CHAIN_NAME, Numeric.toHexString(POSITION_KEY.getValue()));
        assertTrue(reopened.isPresent());
        assertEquals(GmxPosition.Status.OPEN, reopened.get().getStatus(),
                "重新开仓应为 OPEN");

        // 爆仓
        Function liquidate = new Function("emitLiquidate",
                Arrays.asList(
                        POSITION_KEY,
                        new Address(ACCOUNT),
                        new Address(COLLATERAL),
                        new Address(INDEX),
                        new Bool(false),
                        new Uint256(new BigInteger("2000000000000000000")),       // size
                        new Uint256(new BigInteger("500000000")),                // collateral
                        new Uint256(BigInteger.ZERO),                            // reserveAmount
                        new Int256(new BigInteger("-100000000")),                // realisedPnl (负 = 亏损)
                        new Uint256(new BigInteger("1800000000000000000000"))    // markPrice
                ), Collections.emptyList());

        sendSignedTx(FunctionEncoder.encode(liquidate));
        Thread.sleep(500);
        mineEmptyBlock();
        Thread.sleep(300);
        blockScanner.scan();

        Optional<GmxPosition> liquidated = positionRepo.findByChainNameAndPositionKey(
                CHAIN_NAME, Numeric.toHexString(POSITION_KEY.getValue()));
        assertTrue(liquidated.isPresent());
        assertEquals(GmxPosition.Status.LIQUIDATED, liquidated.get().getStatus(),
                "爆仓后状态应为 LIQUIDATED");
        System.out.println("TEST3 PASSED: position LIQUIDATED");
    }

    // ======================== 辅助方法 ========================

    /** 签名 + 发送交易（跟 ERC20 测试同一模式） */
    private void sendSignedTx(String data) throws Exception {
        BigInteger nonce = web3j.ethGetTransactionCount(
                ANVIL_ADDRESS, DefaultBlockParameterName.LATEST).send().getTransactionCount();

        RawTransaction rawTx = RawTransaction.createTransaction(
                nonce, DefaultGasProvider.GAS_PRICE, DefaultGasProvider.GAS_LIMIT,
                contractAddress, BigInteger.ZERO, data);

        byte[] signed = TransactionEncoder.signMessage(rawTx, credentials);
        EthSendTransaction sent = web3j.ethSendRawTransaction(Numeric.toHexString(signed)).send();
        assertFalse(sent.hasError(), "Tx should succeed: " + (sent.getError() != null ? sent.getError().getMessage() : ""));
    }

    /** 发一笔无关转账推块，确保前一笔交易已被打包 */
    private void mineEmptyBlock() throws Exception {
        Credentials dummy = Credentials.create(
                "0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d");
        BigInteger nonce = web3j.ethGetTransactionCount(
                dummy.getAddress(), DefaultBlockParameterName.LATEST).send().getTransactionCount();
        RawTransaction tx = RawTransaction.createEtherTransaction(
                nonce, DefaultGasProvider.GAS_PRICE, DefaultGasProvider.GAS_LIMIT,
                ANVIL_ADDRESS, BigInteger.ONE);
        byte[] signed = TransactionEncoder.signMessage(tx, dummy);
        web3j.ethSendRawTransaction(Numeric.toHexString(signed)).send();
    }
}