package com.lzb.indexer.scanner;

import com.lzb.indexer.config.ChainProperties.ChainConfig;
import com.lzb.indexer.domain.entity.ScannedBlock;
import com.lzb.indexer.domain.entity.SyncCheckpoint;
import com.lzb.indexer.domain.entity.TokenTransfer;
import com.lzb.indexer.domain.repository.ScannedBlockRepository;
import com.lzb.indexer.domain.repository.SyncCheckpointRepository;
import com.lzb.indexer.domain.repository.TokenTransferRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.http.HttpService;
import okhttp3.OkHttpClient;
import java.util.concurrent.TimeUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BlockScanner {

    private static final Logger log = LoggerFactory.getLogger(BlockScanner.class);

    private final String chainName;
    private final String contractAddress;
    private final int pageSize;
    private final int reorgDepth;
    private final long startBlock;

    private final Web3j web3j;
    private final EventDecoder eventDecoder;
    private final TokenTransferRepository transferRepo;
    private final SyncCheckpointRepository checkpointRepo;
    private final ScannedBlockRepository scannedBlockRepo;
    private final MeterRegistry meterRegistry;

    private final Counter blocksProcessed;
    private final Counter transfersFound;
    private final Timer scanTimer;

    private volatile boolean running = false;
    private volatile long latestScannedBlock = 0;
    private volatile long chainTip = 0;

    public BlockScanner(ChainConfig cfg, EventDecoder eventDecoder,
                        TokenTransferRepository transferRepo,
                        SyncCheckpointRepository checkpointRepo,
                        ScannedBlockRepository scannedBlockRepo,
                        MeterRegistry meterRegistry) {
        this.chainName = cfg.getName();
        this.contractAddress = cfg.getContractAddress();
        this.pageSize = cfg.getPageSize();
        this.reorgDepth = cfg.getReorgDepth();
        this.startBlock = cfg.getStartBlock();
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        this.web3j = Web3j.build(new HttpService(cfg.getRpcUrl(), httpClient));
        this.eventDecoder = eventDecoder;
        this.transferRepo = transferRepo;
        this.checkpointRepo = checkpointRepo;
        this.scannedBlockRepo = scannedBlockRepo;
        this.meterRegistry = meterRegistry;

        String prefix = "scanner." + chainName;
        this.blocksProcessed = Counter.builder(prefix + ".blocks.processed")
                .register(meterRegistry);
        this.transfersFound = Counter.builder(prefix + ".transfers.found")
                .register(meterRegistry);
        this.scanTimer = Timer.builder(prefix + ".scan.duration")
                .register(meterRegistry);

        Gauge.builder(prefix + ".last.block", this, s -> s.latestScannedBlock)
                .register(meterRegistry);
        Gauge.builder(prefix + ".chain.tip", this, s -> s.chainTip)
                .register(meterRegistry);

        log.info("BlockScanner[{}] created: startBlock={}, pageSize={}, reorgDepth={}",
                chainName, startBlock, pageSize, reorgDepth);
    }

    public String getChainName() { return chainName; }
    public boolean isRunning() { return running; }
    public long getLatestScannedBlock() { return latestScannedBlock; }
    public long getChainTip() { return chainTip; }

    public void scan() {
        if (running) return;
        running = true;
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            doScan();
        } finally {
            sample.stop(scanTimer);
            running = false;
        }
    }

    private void doScan() {
        try {
            chainTip = web3j.ethBlockNumber().send().getBlockNumber().longValue();
            long safeTip = chainTip - reorgDepth;
            if (safeTip < 0) safeTip = 0;

            verifyAndHandleReorg();

            SyncCheckpoint checkpoint = getOrInitCheckpoint();
            int cycles = 0;
            while (cycles < 100) {
                long fromBlock = checkpoint.getLastScannedBlock();
                if (fromBlock >= safeTip) {
                    latestScannedBlock = safeTip;
                    return;
                }
                long toBlock = Math.min(fromBlock + pageSize, safeTip);

                log.info("BlockScanner[{}] fetching logs for blocks {}-{}", chainName, fromBlock + 1, toBlock);
                List<TokenTransfer> transfers = fetchTransfers(fromBlock + 1, toBlock);
                for (TokenTransfer t : transfers) {
                    if (!transferRepo.existsByTxHashAndLogIndexAndChainName(
                            t.getTxHash(), t.getLogIndex(), chainName)) {
                        transferRepo.save(t);
                        transfersFound.increment();
                    }
                }

                for (long b = fromBlock + 1; b <= toBlock; b++) {
                    saveBlockHash(b);
                }

                blocksProcessed.increment(toBlock - fromBlock);
                checkpoint.setLastScannedBlock(toBlock);
                checkpointRepo.save(checkpoint);
                latestScannedBlock = toBlock;

                log.info("BlockScanner[{}] scanned {}-{} ({} blocks, {} transfers)",
                        chainName, fromBlock + 1, toBlock, toBlock - fromBlock, transfers.size());

                if (toBlock >= safeTip) {
                    latestScannedBlock = safeTip;
                    return;
                }
                cycles++;
            }
        } catch (Exception e) {
            log.error("BlockScanner[{}] scan failed: {}", chainName, e.getMessage());
        }
    }

    private List<TokenTransfer> fetchTransfers(long fromBlock, long toBlock) throws Exception {
        EthFilter filter = new EthFilter(
                new DefaultBlockParameterNumber(fromBlock),
                new DefaultBlockParameterNumber(toBlock),
                contractAddress);
        EthLog ethLog = web3j.ethGetLogs(filter).send();
        List<TokenTransfer> results = new ArrayList<>();
        for (EthLog.LogResult<?> lr : ethLog.getLogs()) {
            Log l = (Log) lr.get();
            TokenTransfer t = eventDecoder.decode(l, chainName);
            if (t != null) results.add(t);
        }
        return results;
    }

    private void saveBlockHash(long blockNumber) {
        try {
            if (scannedBlockRepo.existsById(blockNumber)) return;
            EthBlock.Block block = web3j.ethGetBlockByNumber(
                    new DefaultBlockParameterNumber(blockNumber), false).send().getBlock();
            if (block != null) {
                scannedBlockRepo.save(new ScannedBlock(blockNumber, block.getHash(), chainName));
            }
        } catch (Exception e) {
            log.warn("BlockScanner[{}] save block hash failed {}: {}",
                    chainName, blockNumber, e.getMessage());
        }
    }

    private void verifyAndHandleReorg() {
        List<ScannedBlock> recentBlocks = scannedBlockRepo
                .findByChainNameOrderByBlockNumberAsc(chainName, PageRequest.of(0, reorgDepth));
        if (recentBlocks.isEmpty()) return;

        Long rollbackTarget = null;
        for (ScannedBlock sb : recentBlocks) {
            try {
                EthBlock.Block onChain = web3j.ethGetBlockByNumber(
                        new DefaultBlockParameterNumber(sb.getBlockNumber()), false).send().getBlock();
                if (onChain == null || !onChain.getHash().equalsIgnoreCase(sb.getBlockHash())) {
                    rollbackTarget = sb.getBlockNumber();
                    break;
                }
            } catch (Exception e) {
                log.warn("BlockScanner[{}] reorg check failed block {}: {}",
                        chainName, sb.getBlockNumber(), e.getMessage());
            }
        }

        if (rollbackTarget != null) {
            log.warn("BlockScanner[{}] REORG at block {}! Rolling back...",
                    chainName, rollbackTarget);
            transferRepo.deleteByChainNameAndBlockNumberGreaterThanEqual(chainName, rollbackTarget);
            scannedBlockRepo.deleteByChainNameAndBlockNumberGreaterThanEqual(chainName, rollbackTarget);
            SyncCheckpoint cp = checkpointRepo
                    .findByChainNameAndContractAddress(chainName, contractAddress).orElse(null);
            if (cp != null) {
                cp.setLastScannedBlock(rollbackTarget - 1);
                checkpointRepo.save(cp);
            }
            log.warn("BlockScanner[{}] rollback done. Reset to {}", chainName, rollbackTarget - 1);
        }
    }

    private SyncCheckpoint getOrInitCheckpoint() {
        Optional<SyncCheckpoint> existing = checkpointRepo
                .findByChainNameAndContractAddress(chainName, contractAddress);
        if (existing.isPresent()) return existing.get();
        SyncCheckpoint cp = new SyncCheckpoint(contractAddress, startBlock, chainName);
        return checkpointRepo.save(cp);
    }
}