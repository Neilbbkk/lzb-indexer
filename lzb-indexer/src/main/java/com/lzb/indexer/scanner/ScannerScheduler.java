package com.lzb.indexer.scanner;

import com.lzb.indexer.config.ChainProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

@Component
@EnableScheduling
public class ScannerScheduler {

    private static final Logger log = LoggerFactory.getLogger(ScannerScheduler.class);

    private final BlockScannerFactory scannerFactory;
    private final ChainProperties chainProperties;
    private List<BlockScanner> scanners;

    public ScannerScheduler(BlockScannerFactory scannerFactory, ChainProperties chainProperties) {
        this.scannerFactory = scannerFactory;
        this.chainProperties = chainProperties;
    }

    @PostConstruct
    public void init() {
        this.scanners = scannerFactory.createAll(chainProperties);
        log.info("ScannerScheduler initialized with {} scanner(s), enabled={}",
                scanners.size(), chainProperties.getScanner().isEnabled());
    }

    @Scheduled(fixedRateString = "${app.scanner.fixed-rate-ms:5000}")
    public void runScan() {
        if (!chainProperties.getScanner().isEnabled()) return;

        for (BlockScanner scanner : scanners) {
            if (!scanner.isRunning()) {
                scanner.scan();
            }
        }
    }

    public List<BlockScanner> getScanners() {
        return scanners;
    }
}