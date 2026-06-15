package com.lzb.indexer.scanner;

import com.lzb.indexer.config.ChainProperties;
import com.lzb.indexer.domain.repository.ScannedBlockRepository;
import com.lzb.indexer.domain.repository.SyncCheckpointRepository;
import com.lzb.indexer.domain.repository.TokenTransferRepository;
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
    private final TokenTransferRepository transferRepo;
    private final SyncCheckpointRepository checkpointRepo;
    private final ScannedBlockRepository scannedBlockRepo;
    private final MeterRegistry meterRegistry;

    public BlockScannerFactory(EventDecoder eventDecoder,
                               TokenTransferRepository transferRepo,
                               SyncCheckpointRepository checkpointRepo,
                               ScannedBlockRepository scannedBlockRepo,
                               MeterRegistry meterRegistry) {
        this.eventDecoder = eventDecoder;
        this.transferRepo = transferRepo;
        this.checkpointRepo = checkpointRepo;
        this.scannedBlockRepo = scannedBlockRepo;
        this.meterRegistry = meterRegistry;
    }

    public List<BlockScanner> createAll(ChainProperties props) {
        List<BlockScanner> scanners = new ArrayList<>();
        for (ChainProperties.ChainConfig cfg : props.getChains()) {
            BlockScanner scanner = new BlockScanner(
                    cfg, eventDecoder, transferRepo, checkpointRepo, scannedBlockRepo, meterRegistry);
            scanners.add(scanner);
            log.info("Created BlockScanner for chain: {}", cfg.getName());
        }
        return scanners;
    }
}