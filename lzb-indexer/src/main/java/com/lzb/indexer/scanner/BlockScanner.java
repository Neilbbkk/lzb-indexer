package com.lzb.indexer.scanner;

import com.lzb.indexer.config.ChainProperties.ChainConfig;
import com.lzb.indexer.domain.entity.*;
import com.lzb.indexer.domain.repository.*;
import com.lzb.indexer.service.GmxPositionService;
import io.micrometer.core.instrument.*;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 区块扫描器。
 *
 * 支持两种协议：
 *   ERC20   — 扫 Transfer 事件，存入 token_transfers
 *   GMX*    — 扫 PositionIncrease / PositionDecrease 事件，
 *             存入 gmx_position_history，并通过 GmxPositionService 更新 gmx_positions
 *
 * 每一种 ChainConfig 创建独立 scanner，共享同一个 EventDecoder 和 Repository。
 */
public class BlockScanner {

    private static final Logger log = LoggerFactory.getLogger(BlockScanner.class);

    private final String chainName;
    private final String protocol;
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

    private final GmxPositionHistoryRepository gmxHistoryRepo;
    private final GmxPositionService gmxPositionService;

    private final Counter transfersFound;
    private final Counter positionsFound;
    private final Counter blocksProcessed;
    private final Timer scanTimer;

    private volatile boolean running = false;
    private volatile long latestScannedBlock = 0;
    private volatile long chainTip = 0;

    public BlockScanner(ChainConfig cfg, EventDecoder eventDecoder,
                        TokenTransferRepository transferRepo,
                        SyncCheckpointRepository checkpointRepo,
                        ScannedBlockRepository scannedBlockRepo,
                        MeterRegistry meterRegistry,
                        GmxPositionHistoryRepository gmxHistoryRepo,
                        GmxPositionService gmxPositionService) {
        this.chainName = cfg.getName();
        this.protocol = cfg.getProtocol() != null ? cfg.getProtocol() : "ERC20";
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
        this.gmxHistoryRepo = gmxHistoryRepo;
        this.gmxPositionService = gmxPositionService;

        String prefix = "scanner." + chainName;
        this.blocksProcessed = Counter.builder(prefix + ".blocks.processed")
                .register(meterRegistry);
        this.transfersFound = Counter.builder(prefix + ".transfers.found")
                .register(meterRegistry);
        this.positionsFound = Counter.builder(prefix + ".positions.found")
                .register(meterRegistry);
        this.scanTimer = Timer.builder(prefix + ".scan.duration")
                .register(meterRegistry);

        Gauge.builder(prefix + ".last.block", this, s -> s.latestScannedBlock)
                .register(meterRegistry);
        Gauge.builder(prefix + ".chain.tip", this, s -> s.chainTip)
                .register(meterRegistry);

        log.info("BlockScanner[{}] created: protocol={}, contract={}, startBlock={}, pageSize={}, reorgDepth={}",
                chainName, protocol, contractAddress, startBlock, pageSize, reorgDepth);
    }

    public String getChainName() { return chainName; }
    public String getProtocol() { return protocol; }
    public long getLatestScannedBlock() { return latestScannedBlock; }
    public long getChainTip() { return chainTip; }
    public boolean isRunning() { return running; }

    // ======================== 主循环 ========================

    public void scan() {
        if (running) return;
        running = true;
        try {
            SyncCheckpoint cp = getOrInitCheckpoint();
            chainTip = web3j.ethBlockNumber().send().getBlockNumber().longValue();
            long fromBlock = Math.max(cp.getLastScannedBlock(), startBlock - 1);
            long toBlock = Math.min(fromBlock + pageSize, chainTip);

            if (fromBlock >= toBlock) {
                log.debug("BlockScanner[{}] up to date at block {}", chainName, fromBlock);
                return;
            }

            Timer.Sample sample = Timer.start(meterRegistry);
            try {
                log.info("BlockScanner[{}] fetching logs for blocks {}-{}", chainName, fromBlock + 1, toBlock);

                if (protocol.equals("ERC20")) {
                    processErc20Events(fromBlock, toBlock);
                } else if (protocol.startsWith("GMX")) {
                    processGmxEvents(fromBlock, toBlock);
                }

                for (long b = fromBlock + 1; b <= toBlock; b++) {
                    saveBlockHash(b);
                }

                blocksProcessed.increment(toBlock - fromBlock);
                latestScannedBlock = toBlock;
                cp.setLastScannedBlock(toBlock);
                checkpointRepo.save(cp);

                verifyAndHandleReorg();
            } finally {
                sample.stop(scanTimer);
            }
        } catch (Exception e) {
            log.error("BlockScanner[{}] scan failed: {}", chainName, e.getMessage());
        } finally {
            running = false;
        }
    }

    // ======================== ERC20 事件处理 ========================

    private void processErc20Events(long fromBlock, long toBlock) throws Exception {
        EthFilter filter = new EthFilter(
                new DefaultBlockParameterNumber(fromBlock),
                new DefaultBlockParameterNumber(toBlock),
                contractAddress);
        filter.addOptionalTopics(EventDecoder.getTransferEventHash());

        EthLog ethLog = web3j.ethGetLogs(filter).send();
        int transferCount = 0;

        for (EthLog.LogResult lr : ethLog.getLogs()) {
            Log l = (Log) lr.get();
            TokenTransfer t = eventDecoder.decode(l, chainName);
            if (t == null) continue;

            if (transferRepo.existsByTxHashAndLogIndexAndChainName(
                    t.getTxHash(), t.getLogIndex(), t.getChainName())) {
                continue;
            }

            transferRepo.save(t);
            transfersFound.increment();
            transferCount++;
        }
        log.debug("BlockScanner[{}] ERC20: {}-{} had {} transfers", chainName, fromBlock, toBlock, transferCount);
    }

    // ======================== GMX 事件处理（V2） ========================

    private void processGmxEvents(long fromBlock, long toBlock) throws Exception {
        EthFilter filter = new EthFilter(
                new DefaultBlockParameterNumber(fromBlock),
                new DefaultBlockParameterNumber(toBlock),
                contractAddress);

        EthLog ethLog = web3j.ethGetLogs(filter).send();
        if (ethLog.hasError()) {
            log.warn("BlockScanner[{}] ethGetLogs error: {}", chainName, ethLog.getError().getMessage());
            return;
        }
        List<EthLog.LogResult> logs = ethLog.getLogs();
        if (logs == null || logs.isEmpty()) return;
        log.info("BlockScanner[{}] GMX {}-{} got {} raw logs", chainName, fromBlock, toBlock, logs.size());

        int positionEventCount = 0;
        int gmxV2Match = 0;
        int decoded = 0;
        for (EthLog.LogResult lr : logs) {
            Log l = (Log) lr.get();

            if (!eventDecoder.isGmxV2Event(l)) continue;
            gmxV2Match++;
            if (gmxV2Match <= 5) {
                log.info("BlockScanner[{}] topic[0]={} topic[1]={}",
                        chainName, l.getTopics().get(0), l.getTopics().get(1));
            }

            GmxPositionHistory event = null;
            if (eventDecoder.isIncreasePositionEvent(l)) {
                event = eventDecoder.decodeIncreasePosition(l, chainName);
            } else if (eventDecoder.isDecreasePositionEvent(l)) {
                event = eventDecoder.decodeDecreasePosition(l, chainName);
            } else if (eventDecoder.isLiquidatePositionEvent(l)) {
                event = eventDecoder.decodeLiquidatePosition(l, chainName);
            }

            if (event == null) continue;
            decoded++;

            if (gmxHistoryRepo.existsByTxHashAndLogIndexAndChainName(
                    event.getTxHash(), event.getLogIndex(), chainName)) {
                continue;
            }

            gmxHistoryRepo.save(event);
            gmxPositionService.apply(event);
            positionsFound.increment();
            positionEventCount++;
        }
        log.info("BlockScanner[{}] GMX {}-{} v2Match={} decoded={} saved={}",
                chainName, fromBlock, toBlock, gmxV2Match, decoded, positionEventCount);
    }

    // ======================== 区块 Hash ========================

    private void saveBlockHash(long blockNumber) {
        try {
            if (scannedBlockRepo.existsByBlockNumberAndChainName(blockNumber, chainName)) return;
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

    // ======================== Reorg 检测与回滚 ========================

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

            scannedBlockRepo.deleteByChainNameAndBlockNumberGreaterThanEqual(chainName, rollbackTarget);

            if (protocol.equals("ERC20")) {
                transferRepo.deleteByChainNameAndBlockNumberGreaterThanEqual(chainName, rollbackTarget);
            } else if (protocol.startsWith("GMX")) {
                gmxHistoryRepo.deleteByChainNameAndBlockNumberGreaterThanEqual(chainName, rollbackTarget);
            }

            SyncCheckpoint cp = checkpointRepo
                    .findByChainNameAndContractAddress(chainName, contractAddress).orElse(null);
            if (cp != null) {
                cp.setLastScannedBlock(rollbackTarget - 1);
                checkpointRepo.save(cp);
            }
            log.warn("BlockScanner[{}] rollback done. Reset to {}", chainName, rollbackTarget - 1);
        }
    }

    // ======================== Checkpoint ========================

    private SyncCheckpoint getOrInitCheckpoint() {
        Optional<SyncCheckpoint> existing = checkpointRepo
                .findByChainNameAndContractAddress(chainName, contractAddress);
        if (existing.isPresent()) return existing.get();
        SyncCheckpoint cp = new SyncCheckpoint(contractAddress, startBlock, chainName);
        return checkpointRepo.save(cp);
    }
}
