package com.lzb.indexer.scanner;

import com.lzb.indexer.config.ChainProperties;
import com.lzb.indexer.domain.entity.*;
import com.lzb.indexer.domain.repository.*;
import com.lzb.indexer.service.GmxPositionService;
import com.lzb.indexer.service.ScanEventWriter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * BlockScanner 单元测试。
 * 测试扫描器初始化 + EventHandler 路由。
 */
@ExtendWith(MockitoExtension.class)
class BlockScannerTest {

    @Mock private EventDecoder eventDecoder;
    @Mock private TokenTransferRepository transferRepo;
    @Mock private SyncCheckpointRepository checkpointRepo;
    @Mock private ScannedBlockRepository scannedBlockRepo;
    @Mock private GmxPositionHistoryRepository gmxHistoryRepo;
    @Mock private GmxPositionService gmxPositionService;
    @Mock private SwapEventRepository swapEventRepo;
    @Mock private SyncErrorRepository syncErrorRepo;
    @Mock private ScanEventWriter scanEventWriter;

    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
    }

    @Test
    void shouldCreateScannerForErc20Protocol() {
        ChainProperties.ChainConfig cfg = new ChainProperties.ChainConfig();
        cfg.setName("sepolia");
        cfg.setProtocol("ERC20");
        cfg.setRpcUrl("https://sepolia.infura.io/v3/test");
        cfg.setContractAddress("0x8f15B4F7F145f3F0B17C231746377F76f5771Be8");
        cfg.setStartBlock(6900000L);
        cfg.setPageSize(2000);
        cfg.setReorgDepth(12);

        BlockScanner scanner = new BlockScanner(cfg, eventDecoder, transferRepo,
                checkpointRepo, scannedBlockRepo, meterRegistry,
                gmxHistoryRepo, gmxPositionService, swapEventRepo, syncErrorRepo, scanEventWriter);

        assertNotNull(scanner);
        assertEquals("sepolia", scanner.getChainName());
        assertEquals("ERC20", scanner.getProtocol());
        // latestScannedBlock 从 checkpoint 加载，初始为 0
    }

    @Test
    void shouldCreateScannerForUniswapProtocol() {
        ChainProperties.ChainConfig cfg = new ChainProperties.ChainConfig();
        cfg.setName("ethereum-uniswap");
        cfg.setProtocol("UNISWAP_V2");
        cfg.setRpcUrl("https://eth.drpc.org");
        cfg.setContractAddress("0xB4e16d0168e52d35CaCD2c6185b44281Ec28C9Dc");
        cfg.setStartBlock(20800000L);
        cfg.setPageSize(2000);
        cfg.setReorgDepth(12);

        BlockScanner scanner = new BlockScanner(cfg, eventDecoder, transferRepo,
                checkpointRepo, scannedBlockRepo, meterRegistry,
                gmxHistoryRepo, gmxPositionService, swapEventRepo, syncErrorRepo, scanEventWriter);

        assertNotNull(scanner);
        assertEquals("UNISWAP_V2", scanner.getProtocol());
    }

    @Test
    void shouldCreateScannerForGmxProtocol() {
        ChainProperties.ChainConfig cfg = new ChainProperties.ChainConfig();
        cfg.setName("arbitrum-gmx-vault");
        cfg.setProtocol("GMX_VAULT");
        cfg.setRpcUrl("https://arb1.arbitrum.io/rpc");
        cfg.setContractAddress("0xC8ee91A54287DB53897056e12D9819156D3822Fb");
        cfg.setStartBlock(450000000L);
        cfg.setPageSize(2000);
        cfg.setReorgDepth(12);

        BlockScanner scanner = new BlockScanner(cfg, eventDecoder, transferRepo,
                checkpointRepo, scannedBlockRepo, meterRegistry,
                gmxHistoryRepo, gmxPositionService, swapEventRepo, syncErrorRepo, scanEventWriter);

        assertNotNull(scanner);
        assertEquals("GMX_VAULT", scanner.getProtocol());
    }
}
