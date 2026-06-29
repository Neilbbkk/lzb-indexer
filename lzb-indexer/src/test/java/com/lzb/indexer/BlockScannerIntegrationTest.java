package com.lzb.indexer;

import com.lzb.indexer.domain.entity.ScannedBlock;
import com.lzb.indexer.domain.entity.TokenTransfer;
import com.lzb.indexer.domain.repository.ScannedBlockRepository;
import com.lzb.indexer.domain.repository.TokenTransferRepository;
import com.lzb.indexer.scanner.BlockScanner;
import com.lzb.indexer.scanner.ScannerScheduler;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.utils.Numeric;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BlockScanner 集成测试。
 *
 * 测试流程：
 *   1. Spring 容器启动前，@DynamicPropertySource 在 Anvil 上部署 TestToken 合约
 *   2. 把合约地址等配置注入 Spring，让 BlockScannerFactory 创建 anvil 链的 scanner
 *   3. 五个测试按 @Order 顺序执行，共享同一个 Anvil 链状态和 H2 数据库
 *
 * 前置条件：anvil 进程监听 localhost:8545，chain-id = 31337
 */
@SpringBootTest                                                          // 启动完整 Spring 容器
@ActiveProfiles("test")                                                  // 加载 application-test.yml：H2 内存库 + scanner 关闭自动调度
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)                    // 按 @Order 注解顺序执行，保证测试间状态可预测
public class BlockScannerIntegrationTest {

    // ======================== Anvil 环境常量 ========================

    /** Anvil 默认 JSON-RPC 端点 */
    private static final String ANVIL_RPC = "http://localhost:8545";

    /** 多链隔离标识，同时作为 Prometheus 指标前缀 */
    private static final String CHAIN_NAME = "anvil";

    /** Anvil 预置账户 #0 的私钥（Hardhat/Foundry 通用），链上持有 10000 ETH */
    private static final String ANVIL_PRIVATE_KEY =
            "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    /** Anvil 预置账户 #0 的地址 */
    private static final String ANVIL_ADDRESS = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266";

    // ======================== 静态共享状态 ========================

    /** @DynamicPropertySource 部署后写入，后续测试方法直接使用 */
    private static String contractAddress;

    /** 直连 Anvil 的 Web3j 实例，测试方法用它发交易、查链状态 */
    private static Web3j web3j;

    /** Anvil #0 账户的 Credentials，用于签名交易 */
    private static Credentials credentials;

    // ======================== Spring 注入 ========================

    @Autowired
    private ScannerScheduler scheduler;          // 通过它拿到 BlockScannerFactory 创建的 scanner

    @Autowired
    private TokenTransferRepository transferRepo; // 验证扫到的 Transfer 是否入库

    @Autowired
    private ScannedBlockRepository scannedBlockRepo; // 验证区块 hash 是否正确保存（reorg 检测用）

    /** @BeforeEach 从 scheduler 获取，每个测试方法复用同一个 scanner 实例 */
    private BlockScanner blockScanner;

    // ======================== 生命周期：Spring 容器启动前 ========================

    /**
     * Spring 容器创建前执行：部署测试合约 → 注入全部 chain 配置。
     *
     * 【关键】Spring Boot 不会跨 property source 合并同一个列表项的不同字段。
     * 因此必须在这里注册 chains[0] 的全部 8 个属性，而不是只补 contract-address。
     */
    @DynamicPropertySource
    static void prepareAnvilAndRegisterChain(DynamicPropertyRegistry registry) throws Exception {
        // --- 1. 连接 Anvil ---
        web3j = Web3j.build(new HttpService(ANVIL_RPC));
        credentials = Credentials.create(ANVIL_PRIVATE_KEY);

        // 防止 CI 环境连到其他链（比如 Sepolia），造成私钥泄露或状态异常
        BigInteger chainId = web3j.ethChainId().send().getChainId();
        if (chainId.longValue() != 31337) {
            throw new RuntimeException("Expected Anvil (31337), got chain " + chainId);
        }

        // --- 2. 部署 TestToken 合约 + 多挖 3 个块让链有足够历史 ---
        contractAddress = deployContract();
        mineExtraBlocks();
        System.out.println("Contract deployed at: " + contractAddress);

        // --- 3. 完整注册 chains[0] 全部属性（见上方【关键】注释） ---
        registry.add("app.chains[0].name",              () -> CHAIN_NAME);
        registry.add("app.chains[0].rpc-url",           () -> ANVIL_RPC);
        registry.add("app.chains[0].contract-address",  () -> contractAddress);
        registry.add("app.chains[0].wallet-address",    () -> ANVIL_ADDRESS);
        registry.add("app.chains[0].private-key",       () -> ANVIL_PRIVATE_KEY);
        registry.add("app.chains[0].start-block",       () -> "0");
        registry.add("app.chains[0].page-size",         () -> "100");   // 100 块/次，小批量便于测试
        registry.add("app.chains[0].reorg-depth",       () -> "1");     // anvil 不回滚，深度设 1 即可
    }

    /**
     * 用另一个独立账户发 3 笔空 ETH 转账，把链高度推上去。
     *
     * 目的：让合约部署块（约 block 1）和后续块之间有间隔，
     * 验证 scanner 能跨多块扫描，而不是只扫部署块。
     */
    private static void mineExtraBlocks() throws Exception {
        Credentials dummy = Credentials.create(
                "0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d");   // Anvil 预置账户 #1
        for (int i = 0; i < 3; i++) {
            // 每次获取最新 nonce，因为上一笔交易会消耗它
            BigInteger nonce = web3j.ethGetTransactionCount(
                    dummy.getAddress(), DefaultBlockParameterName.LATEST)
                    .send().getTransactionCount();

            // 构造一笔简单的 ETH 转账：转 1 wei 给 Anvil #0
            RawTransaction tx = RawTransaction.createEtherTransaction(
                    nonce,
                    DefaultGasProvider.GAS_PRICE,
                    DefaultGasProvider.GAS_LIMIT,
                    ANVIL_ADDRESS,
                    BigInteger.ONE);                       // 1 wei，几乎无成本

            // RLP 编码 + 签名 + 十六进制 → 广播
            byte[] signed = TransactionEncoder.signMessage(tx, dummy);
            web3j.ethSendRawTransaction(Numeric.toHexString(signed)).send();
        }
        System.out.println("Mined 3 extra blocks, chain tip: "
                + web3j.ethBlockNumber().send().getBlockNumber());
    }

    /**
     * 调用 Foundry 的 forge create 部署 TestToken.sol 到 Anvil。
     *
     * forge create 流程：编译 Solidity → 发部署交易 → stdout 打印 "Deployed to: 0x..."
     * 本方法启子进程运行 forge，解析 stdout 提取合约地址。
     */
    private static String deployContract() throws Exception {
        // forge 项目根目录
        String forgeRoot = Paths.get("src/test/solidity").toAbsolutePath().toString();

        // forge create TestToken --broadcast --rpc-url ... --private-key ... --root ...
        ProcessBuilder pb = new ProcessBuilder(
                "forge", "create", "TestToken",
                "--broadcast",                                     // 实际广播交易（不加只会模拟）
                "--rpc-url", ANVIL_RPC,
                "--private-key", ANVIL_PRIVATE_KEY,
                "--root", forgeRoot
        );
        pb.redirectErrorStream(true);                              // stderr 合并到 stdout，方便一次读完
        Process p = pb.start();

        // 逐行读取 forge 输出
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        p.waitFor(30, TimeUnit.SECONDS);                           // 30 秒超时，防止 CI 里 forge 卡死

        // 解析输出，找 "Deployed to: 0x..." 行
        String out = output.toString();
        for (String line : out.split("\n")) {
            if (line.contains("Deployed to:")) {
                String addr = line.substring(line.lastIndexOf(":") + 1).trim();
                return addr;
            }
        }
        throw new RuntimeException("Could not find Deployed to in forge output:\n" + out);
    }

    // ======================== 每个测试方法前 ========================

    /**
     * 从 ScannerScheduler 获取 anvil 链的 BlockScanner。
     *
     * scheduler.getScanners() 返回 BlockScannerFactory 创建的所有 scanner 列表。
     * 本测试只注册了 1 条链，所以 get(0) 就是 anvil 的 scanner。
     */
    @BeforeEach
    void setUp() {
        this.blockScanner = scheduler.getScanners().get(0);
    }

    // ======================== 测试 1：扫 mint transfer ========================

    /**
     * TestToken 构造函数里 emit Transfer(address(0), msg.sender, 1_000_000 * 10^18)，
     * 这是一笔"零地址铸币"事件，from=0x000...000。
     * 验证 scanner 首次扫描能发现它。
     */
    @Test
    @Order(1)
    @DisplayName("扫 Anvil 链应发现合约部署时的 mint Transfer")
    void testScanFindsMint() {
        // 首次扫描：从 start-block=0 开始，扫到链当前高度（约 block 4-5）
        blockScanner.scan();

        // 查询 0~10 块范围内所有 Transfer，按块号升序
        List<TokenTransfer> transfers = transferRepo
                .findByChainNameAndBlockNumberBetweenOrderByBlockNumberAsc(
                        CHAIN_NAME, 0L, 10L);

        assertFalse(transfers.isEmpty(),
                "至少应扫到一笔 mint Transfer");

        // mint 的特征：from 地址是全零（0x000...000）
        TokenTransfer mint = transfers.stream()
                .filter(t -> "0x0000000000000000000000000000000000000000"
                        .equals(t.getFromAddress()))
                .findFirst()
                .orElse(null);

        assertNotNull(mint,
                "应包含 from=0x0 的铸币 Transfer");
        // to 是部署者（Anvil #0），地址统一小写比较
        assertEquals(ANVIL_ADDRESS.toLowerCase(), mint.getToAddress());
        // 金额 = 1_000_000 * 10^18（TestToken 构造函数里写死的）
        assertEquals(
                new BigInteger("1000000000000000000000000"),
                mint.getAmount());
        assertEquals(CHAIN_NAME, mint.getChainName(),
                "chainName 应正确标记为 anvil");
    }

    // ======================== 测试 2：发 Transfer 后扫到 ========================

    /**
     * 手动构造一笔 transfer(recipient, 100) 交易，广播后让 scanner 再扫一次，
     * 验证新 Transfer 事件被正确索引入库。
     *
     * 涉及的 Web3j 知识点：
     *   - ERC20 transfer 的函数选择器 = keccak256("transfer(address,uint256)") 的前 4 字节 = 0xa9059cbb
     *   - calldata 编码：选择器(4 字节) + address(32 字节，左补零) + uint256(32 字节，左补零)
     */
    @Test
    @Order(2)
    @DisplayName("发一笔 Transfer 交易后，scanner 再扫应发现新事件")
    void testScanFindsNewTransfer() throws Exception {
        // Anvil 预置账户 #2 作为接收方
        String recipient = "0x70997970C51812dc3A010C7d01b50e0d17dc79C8";

        // 手动编码 transfer(address,uint256) 的 calldata：
        //   0xa9059cbb                                              ← 函数选择器
        //   + recipient 去掉 "0x" 前缀后左补零到 32 字节              ← address to
        //   + 000...64                                              ← uint256 100（0x64）
        String transferData = "0xa9059cbb"
                + "000000000000000000000000" + recipient.substring(2)
                + "0000000000000000000000000000000000000000000000000000000000000064";

        // 获取部署者的当前 nonce
        BigInteger nonce = web3j.ethGetTransactionCount(
                ANVIL_ADDRESS, DefaultBlockParameterName.LATEST)
                .send().getTransactionCount();

        // 构造"调合约方法"的交易：to=合约地址, value=0（不发 ETH）, data=calldata
        RawTransaction rawTxn = RawTransaction.createTransaction(
                nonce,
                DefaultGasProvider.GAS_PRICE,
                DefaultGasProvider.GAS_LIMIT,
                contractAddress,                             // to = TestToken 合约
                BigInteger.ZERO,                             // value = 0，不转 ETH
                transferData);                               // data = transfer(recipient, 100)

        // RLP 编码 + 签名 → 广播
        byte[] signed = TransactionEncoder.signMessage(rawTxn, credentials);
        EthSendTransaction sent = web3j.ethSendRawTransaction(
                Numeric.toHexString(signed)).send();

        assertFalse(sent.hasError(),
                "Transfer 交易应成功: " + sent.getError());

        // 等待 Anvil 出块（即时挖矿，半秒足够）
        Thread.sleep(500);

        // 发一笔无关的 ETH 转账，确保前面 transfer 所在的块已确认
        // （Anvil 自动挖矿：收到新 tx 时出当前块，所以发第二笔能"推"前一笔进块）
        BigInteger nonce2 = web3j.ethGetTransactionCount(
                ANVIL_ADDRESS, DefaultBlockParameterName.LATEST)
                .send().getTransactionCount();
        RawTransaction dummyTx = RawTransaction.createEtherTransaction(
                nonce2,
                DefaultGasProvider.GAS_PRICE,
                DefaultGasProvider.GAS_LIMIT,
                "0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC",   // 任意地址
                BigInteger.ONE);
        byte[] signedDummy = TransactionEncoder.signMessage(dummyTx, credentials);
        web3j.ethSendRawTransaction(Numeric.toHexString(signedDummy)).send();
        Thread.sleep(300);

        // 第二次扫描：应扫到新块里的 Transfer
        blockScanner.scan();

        // 查 Anvil #0 相关的所有 Transfer（作为 from 或 to），按块号降序
        List<TokenTransfer> transfers = transferRepo
                .findByChainNameAndAddress(
                        CHAIN_NAME,
                        ANVIL_ADDRESS.toLowerCase(),
                        PageRequest.of(0, 20))
                .getContent();

        // 过滤 toAddress == recipient 的那笔
        TokenTransfer found = transfers.stream()
                .filter(t -> t.getToAddress().equals(recipient.toLowerCase()))
                .findFirst()
                .orElse(null);

        assertNotNull(found,
                "应找到发给 " + recipient + " 的 Transfer");
        assertEquals(BigInteger.valueOf(100), found.getAmount(),
                "金额应为 100");
        assertEquals(CHAIN_NAME, found.getChainName());
    }

    // ======================== 测试 3：区块 hash 存储 ========================

    /**
     * 验证 reorg 保护机制：每扫一个块，BlockScanner 会存 (blockNumber, blockHash, chainName)
     * 到 scanned_blocks 表。如果链发生重组，下次扫描时会对比 hash，不一致则回滚重扫。
     *
     * 这里逐块对比：数据库里的 hash == 链上真实 hash。
     */
    @Test
    @Order(3)
    @DisplayName("每扫一个块应存下 block hash，用于 reorg 检测")
    void testBlockHashSaved() {
        List<ScannedBlock> blocks = scannedBlockRepo.findAll();
        assertFalse(blocks.isEmpty(),
                "scanned_blocks 表应有记录");

        for (ScannedBlock sb : blocks) {
            try {
                // 从链上拿该块的完整信息（fullTx=false：不需要交易列表）
                EthBlock.Block chainBlock = web3j.ethGetBlockByNumber(
                        new DefaultBlockParameterNumber(sb.getBlockNumber()), false)
                        .send().getBlock();

                assertNotNull(chainBlock,
                        "块 " + sb.getBlockNumber() + " 应存在");
                // 对比 hash（统一小写，因为链返回的 hash 大小写不固定）
                assertEquals(
                        chainBlock.getHash().toLowerCase(),
                        sb.getBlockHash().toLowerCase(),
                        "块 " + sb.getBlockNumber() + " 的 hash 不匹配（可能发生了 reorg）");
                assertEquals(CHAIN_NAME, sb.getChainName(),
                        "chainName 应正确隔离");
            } catch (Exception e) {
                fail("验证块 " + sb.getBlockNumber() + " 失败: " + e.getMessage());
            }
        }
    }

    // ======================== 测试 4：幂等性 ========================

    /**
     * 同一批块扫两次，Transfer 数不应增加。
     * 依赖 existsByTxHashAndLogIndexAndChainName 去重。
     */
    @Test
    @Order(4)
    @DisplayName("重复扫描同一批块不应产生重复 Transfer")
    void testScanIsIdempotent() {
        long countBefore = transferRepo.count();
        blockScanner.scan();
        long countAfter = transferRepo.count();

        assertEquals(countBefore, countAfter,
                "重复扫描不应新增 Transfer（依赖 tx_hash + log_index + chain_name 去重）");
    }

    // ======================== 测试 5：Scanner 内部状态 ========================

    /**
     * 验证 BlockScanner 的状态字段在扫描后正确更新：
     *   - isRunning：扫描期间为 true，结束后自动重置为 false
     *   - latestScannedBlock：已扫到的最大块号
     *   - chainTip：链最新高度（每次 scan 开始时从链上获取）
     */
    @Test
    @Order(5)
    @DisplayName("Scanner 扫描后内部状态应正确")
    void testScannerState() {
        assertFalse(blockScanner.isRunning(),
                "扫描结束应自动重置 running=false");
        assertTrue(blockScanner.getLatestScannedBlock() > 0,
                "应已扫到至少一个块");
        assertTrue(blockScanner.getChainTip() > 0,
                "应知道链的当前高度");
        assertEquals(CHAIN_NAME, blockScanner.getChainName(),
                "链名应为 anvil");
    }
}