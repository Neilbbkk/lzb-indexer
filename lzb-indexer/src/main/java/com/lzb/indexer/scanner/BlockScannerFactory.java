package com.lzb.indexer.scanner;

import com.lzb.indexer.config.ChainProperties;
import com.lzb.indexer.domain.repository.*;
import com.lzb.indexer.service.GmxPositionService;
import com.lzb.indexer.service.ScanEventWriter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class BlockScannerFactory {

    private static final Logger log = LoggerFactory.getLogger(BlockScannerFactory.class);

    private final EventDecoder eventDecoder;
    private final GmxEventDecoder gmxEventDecoder;
    private final TokenTransferRepository transferRepo;
    private final SyncCheckpointRepository checkpointRepo;
    private final ScannedBlockRepository scannedBlockRepo;
    private final MeterRegistry meterRegistry;
    private final GmxPositionHistoryRepository gmxHistoryRepo;
    private final GmxPositionService gmxPositionService;
    private final SwapEventRepository swapEventRepo;
    private final SyncErrorRepository syncErrorRepo;
    private final ScanEventWriter scanEventWriter;

    public BlockScannerFactory(EventDecoder eventDecoder, GmxEventDecoder gmxEventDecoder,
                               TokenTransferRepository transferRepo,
                               SyncCheckpointRepository checkpointRepo,
                               ScannedBlockRepository scannedBlockRepo,
                               MeterRegistry meterRegistry,
                               GmxPositionHistoryRepository gmxHistoryRepo,
                               GmxPositionService gmxPositionService,
                               SwapEventRepository swapEventRepo,
                               SyncErrorRepository syncErrorRepo,
                               ScanEventWriter scanEventWriter) {
        this.eventDecoder = eventDecoder;
        this.gmxEventDecoder = gmxEventDecoder;
        this.transferRepo = transferRepo;
        this.checkpointRepo = checkpointRepo;
        this.scannedBlockRepo = scannedBlockRepo;
        this.meterRegistry = meterRegistry;
        this.gmxHistoryRepo = gmxHistoryRepo;
        this.gmxPositionService = gmxPositionService;
        this.swapEventRepo = swapEventRepo;
        this.syncErrorRepo = syncErrorRepo;
        this.scanEventWriter = scanEventWriter;
    }

    public List<BlockScanner> createAll(ChainProperties props) {
        List<BlockScanner> scanners = new ArrayList<>();
        for (ChainProperties.ChainConfig cfg : props.getChains()) {
            BlockScanner scanner = new BlockScanner(
                    cfg, eventDecoder, gmxEventDecoder, transferRepo, checkpointRepo, scannedBlockRepo,
                    meterRegistry, gmxHistoryRepo, gmxPositionService,
                    swapEventRepo, syncErrorRepo, scanEventWriter);
            scanners.add(scanner);
            log.info("Created BlockScanner for chain: {} (protocol={})", cfg.getName(), cfg.getProtocol());
        }
        return scanners;
    }
}
