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

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BlockScannerIntegrationTest {

    private static final String ANVIL_RPC = "http://localhost:8545";
    private static final String CHAIN_NAME = "anvil";
    private static final String ANVIL_PRIVATE_KEY =
            "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    private static final String ANVIL_ADDRESS = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266";

    private static String contractAddress;
    private static Web3j web3j;
    private static Credentials credentials;

    @Autowired
    private ScannerScheduler scheduler;

    @Autowired
    private TokenTransferRepository transferRepo;

    @Autowired
    private ScannedBlockRepository scannedBlockRepo;

    private BlockScanner blockScanner;

    // ---- @DynamicPropertySource: deploy contract, then register ALL chain props ----

    @DynamicPropertySource
    static void prepareAnvilAndRegisterChain(DynamicPropertyRegistry registry) throws Exception {
        web3j = Web3j.build(new HttpService(ANVIL_RPC));
        credentials = Credentials.create(ANVIL_PRIVATE_KEY);

        BigInteger chainId = web3j.ethChainId().send().getChainId();
        if (chainId.longValue() != 31337) {
            throw new RuntimeException("Expected Anvil (31337), got chain " + chainId);
        }

        contractAddress = deployContract();
        mineExtraBlocks();
        System.out.println("Contract deployed at: " + contractAddress);

        // Spring Boot's relaxed binding merges list items by index ONLY within
        // the same property source.  Since @DynamicPropertySource adds a new
        // source, we must provide EVERY field here — not just contract-address.
        registry.add("app.chains[0].name", () -> CHAIN_NAME);
        registry.add("app.chains[0].rpc-url", () -> ANVIL_RPC);
        registry.add("app.chains[0].contract-address", () -> contractAddress);
        registry.add("app.chains[0].wallet-address", () -> ANVIL_ADDRESS);
        registry.add("app.chains[0].private-key", () -> ANVIL_PRIVATE_KEY);
        registry.add("app.chains[0].start-block", () -> "0");
        registry.add("app.chains[0].page-size", () -> "100");
        registry.add("app.chains[0].reorg-depth", () -> "1");
    }

    private static void mineExtraBlocks() throws Exception {
        for (int i = 0; i < 3; i++) {
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
        System.out.println("Mined 3 extra blocks, chain tip: "
                + web3j.ethBlockNumber().send().getBlockNumber());
    }

    private static String deployContract() throws Exception {
        String forgeRoot = Paths.get("src/test/solidity").toAbsolutePath().toString();
        ProcessBuilder pb = new ProcessBuilder(
                "forge", "create", "TestToken", "--broadcast",
                "--rpc-url", ANVIL_RPC,
                "--private-key", ANVIL_PRIVATE_KEY,
                "--root", forgeRoot
        );
        pb.redirectErrorStream(true);
        Process p = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        p.waitFor(30, TimeUnit.SECONDS);

        String out = output.toString();
        for (String line : out.split("\n")) {
            if (line.contains("Deployed to:")) {
                String addr = line.substring(line.lastIndexOf(":") + 1).trim();
                return addr;
            }
        }
        throw new RuntimeException("Could not find Deployed to in forge output:\n" + out);
    }

    // ---- test methods ----

    @BeforeEach
    void setUp() {
        this.blockScanner = scheduler.getScanners().get(0);
    }

    @Test
    @Order(1)
    @DisplayName("Scanning Anvil should discover mint transfer from contract deployment")
    void testScanFindsMint() {
        blockScanner.scan();

        List<TokenTransfer> transfers = transferRepo
                .findByChainNameAndBlockNumberBetweenOrderByBlockNumberAsc(
                        CHAIN_NAME, 0L, 10L);

        assertFalse(transfers.isEmpty(), "Should have found at least the mint transfer");

        TokenTransfer mint = transfers.stream()
                .filter(t -> "0x0000000000000000000000000000000000000000".equals(t.getFromAddress()))
                .findFirst()
                .orElse(null);

        assertNotNull(mint, "Should contain a mint transfer from zero address");
        assertEquals(ANVIL_ADDRESS.toLowerCase(), mint.getToAddress());
        assertEquals(
                new BigInteger("1000000000000000000000000"),
                mint.getAmount());
        assertEquals(CHAIN_NAME, mint.getChainName());
    }

    @Test
    @Order(2)
    @DisplayName("Scan should detect a new Transfer after sending tokens")
    void testScanFindsNewTransfer() throws Exception {
        String recipient = "0x70997970C51812dc3A010C7d01b50e0d17dc79C8";

        String transferData = "0xa9059cbb"
                + "000000000000000000000000" + recipient.substring(2)
                + "0000000000000000000000000000000000000000000000000000000000000064";

        BigInteger nonce = web3j.ethGetTransactionCount(
                ANVIL_ADDRESS, DefaultBlockParameterName.LATEST).send().getTransactionCount();

        RawTransaction rawTxn = RawTransaction.createTransaction(
                nonce, DefaultGasProvider.GAS_PRICE, DefaultGasProvider.GAS_LIMIT,
                contractAddress, BigInteger.ZERO, transferData);
        byte[] signed = TransactionEncoder.signMessage(rawTxn, credentials);
        EthSendTransaction sent = web3j.ethSendRawTransaction(Numeric.toHexString(signed)).send();
        assertFalse(sent.hasError(), "Transfer tx should succeed: " + sent.getError());

        Thread.sleep(500);

        BigInteger nonce2 = web3j.ethGetTransactionCount(
                ANVIL_ADDRESS, DefaultBlockParameterName.LATEST).send().getTransactionCount();
        RawTransaction dummyTx = RawTransaction.createEtherTransaction(
                nonce2, DefaultGasProvider.GAS_PRICE, DefaultGasProvider.GAS_LIMIT,
                "0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC", BigInteger.ONE);
        byte[] signedDummy = TransactionEncoder.signMessage(dummyTx, credentials);
        web3j.ethSendRawTransaction(Numeric.toHexString(signedDummy)).send();
        Thread.sleep(300);

        blockScanner.scan();

        List<TokenTransfer> transfers = transferRepo
                .findByChainNameAndAddress(CHAIN_NAME, ANVIL_ADDRESS.toLowerCase(),
                        PageRequest.of(0, 20))
                .getContent();

        TokenTransfer found = transfers.stream()
                .filter(t -> t.getToAddress().equals(recipient.toLowerCase()))
                .findFirst()
                .orElse(null);

        assertNotNull(found, "Should find transfer to " + recipient);
        assertEquals(BigInteger.valueOf(100), found.getAmount());
        assertEquals(CHAIN_NAME, found.getChainName());
    }

    @Test
    @Order(3)
    @DisplayName("Block hashes should be persisted for reorg detection")
    void testBlockHashSaved() {
        List<ScannedBlock> blocks = scannedBlockRepo.findAll();
        assertFalse(blocks.isEmpty(), "Scanned blocks should be saved for reorg detection");

        for (ScannedBlock sb : blocks) {
            try {
                EthBlock.Block chainBlock = web3j.ethGetBlockByNumber(
                        new DefaultBlockParameterNumber(sb.getBlockNumber()), false)
                        .send().getBlock();
                assertNotNull(chainBlock, "Block " + sb.getBlockNumber() + " should exist");
                assertEquals(
                        chainBlock.getHash().toLowerCase(),
                        sb.getBlockHash().toLowerCase(),
                        "Block hash mismatch at " + sb.getBlockNumber());
                assertEquals(CHAIN_NAME, sb.getChainName());
            } catch (Exception e) {
                fail("Failed to verify block " + sb.getBlockNumber() + ": " + e.getMessage());
            }
        }
    }

    @Test
    @Order(4)
    @DisplayName("Duplicate scan should be idempotent")
    void testScanIsIdempotent() {
        long countBefore = transferRepo.count();
        blockScanner.scan();
        long countAfter = transferRepo.count();
        assertEquals(countBefore, countAfter, "Duplicate scan should not add duplicate transfers");
    }

    @Test
    @Order(5)
    @DisplayName("Scanner should report correct chain state")
    void testScannerState() {
        assertFalse(blockScanner.isRunning(), "Scanner should not be stuck running");
        assertTrue(blockScanner.getLatestScannedBlock() > 0, "Should have scanned some blocks");
        assertTrue(blockScanner.getChainTip() > 0, "Should know chain tip");
        assertEquals(CHAIN_NAME, blockScanner.getChainName());
    }
}