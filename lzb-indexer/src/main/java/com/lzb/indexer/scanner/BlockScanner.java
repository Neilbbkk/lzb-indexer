package com.lzb.indexer.scanner;

import com.lzb.indexer.config.ChainProperties.ChainConfig;
import com.lzb.indexer.domain.entity.*;
import com.lzb.indexer.domain.repository.*;
import com.lzb.indexer.service.GmxPositionService;
import com.lzb.indexer.service.ScanEventWriter;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * 区块链扫描器。
 * - 支持 ERC20 Transfer、GMX V2 仓位、Uniswap V2 Swap 三种协议
 * - 从 eth_getLogs 直接提取 blockHash，不再逐块 RPC
 * - 内建 RPC 重试：最多 3 次，指数退避
 * - 异常持久化到 sync_errors 表：RPC / DECODE / DB 三类
 */
public class BlockScanner {

    private static final Logger log = LoggerFactory.getLogger(BlockScanner.class);
    private static final int MAX_RETRIES = 2;
    private static final long RETRY_BASE_MS = 1000L;

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
    private final SwapEventRepository swapEventRepo;
    private final SyncErrorRepository syncErrorRepo;
    private final ScanEventWriter scanEventWriter;

    private final Counter transfersFound;
    private final Counter positionsFound;
    private final Counter swapsFound;
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
                         GmxPositionService gmxPositionService,
                         SwapEventRepository swapEventRepo,
                         SyncErrorRepository syncErrorRepo,
                         ScanEventWriter scanEventWriter) {
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
                .retryOnConnectionFailure(true)
                .build();
        this.web3j = Web3j.build(new HttpService(cfg.getRpcUrl(), httpClient));
        this.eventDecoder = eventDecoder;
        this.transferRepo = transferRepo;
        this.checkpointRepo = checkpointRepo;
        this.scannedBlockRepo = scannedBlockRepo;
        this.meterRegistry = meterRegistry;
        this.gmxHistoryRepo = gmxHistoryRepo;
        this.gmxPositionService = gmxPositionService;
        this.swapEventRepo = swapEventRepo;
        this.syncErrorRepo = syncErrorRepo;
        this.scanEventWriter = scanEventWriter;

        String prefix = "scanner." + chainName;
        this.blocksProcessed = Counter.builder(prefix + ".blocks.processed")
                .register(meterRegistry);
        this.transfersFound = Counter.builder(prefix + ".transfers.found")
                .register(meterRegistry);
        this.positionsFound = Counter.builder(prefix + ".positions.found")
                .register(meterRegistry);
        this.swapsFound = Counter.builder(prefix + ".swaps.found")
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

    // ======================== 错误记录 ========================

    private void recordError(String errorType, long blockNumber, String msg) {
        try {
            syncErrorRepo.save(new SyncError(chainName, blockNumber, errorType, msg));
        } catch (Exception ignored) {}
    }

    // ======================== RPC 重试 ========================

    private <T> T retryRpc(Callable<T> call, String operation) throws Exception {
        for (int i = 0; i <= MAX_RETRIES; i++) {
            try {
                return call.call();
            } catch (Exception e) {
                if (i >= MAX_RETRIES) {
                    recordError("RPC", 0, operation + " failed: " + e.getMessage());
                    throw e;
                }
                long delay = RETRY_BASE_MS << i;
                log.warn("BlockScanner[{}] RPC {} failed (attempt {}/{}), retry in {}ms: {}",
                        chainName, operation, i + 1, MAX_RETRIES + 1, delay, e.getMessage());
                Thread.sleep(delay);
            }
        }
        throw new RuntimeException("unreachable");
    }

    // ======================== 扫描主循环 ========================

    public void scan() {
        if (running) return;
        running = true;
        long fromBlock = 0;
        try {
            SyncCheckpoint cp = getOrInitCheckpoint();
            chainTip = retryRpc(() ->
                    web3j.ethBlockNumber().send().getBlockNumber().longValue(), "eth_blockNumber");
            fromBlock = Math.max(cp.getLastScannedBlock(), startBlock - 1);
            long toBlock = Math.min(fromBlock + pageSize, chainTip);

            if (fromBlock >= toBlock) {
                log.debug("BlockScanner[{}] up to date at block {}", chainName, fromBlock);
                return;
            }

            Timer.Sample sample = Timer.start(meterRegistry);
            try {
                log.info("BlockScanner[{}] fetching logs for blocks {}-{}", chainName, fromBlock + 1, toBlock);

                List<EthLog.LogResult> logResults;
                if (protocol.equals("ERC20")) {
                    logResults = processErc20Events(fromBlock, toBlock);
                } else if (protocol.equals("UNISWAP_V2")) {
                    logResults = processUniswapEvents(fromBlock, toBlock);
                } else if (protocol.startsWith("GMX")) {
                    logResults = processGmxEvents(fromBlock, toBlock);
                } else {
                    logResults = new ArrayList<>();
                }

                saveBlockHashesFromLogs(logResults);

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
            recordError("RPC", fromBlock + 1, "scan cycle failed: " + e.getMessage());
        } finally {
            running = false;
        }
    }

    // ======================== ERC20 Transfer ========================

    private List<EthLog.LogResult> processErc20Events(long fromBlock, long toBlock) throws Exception {
        EthFilter filter = new EthFilter(
                new DefaultBlockParameterNumber(fromBlock),
                new DefaultBlockParameterNumber(toBlock),
                contractAddress);
        filter.addOptionalTopics(EventDecoder.getTransferEventHash());

        EthLog ethLog = retryRpc(() -> web3j.ethGetLogs(filter).send(), "eth_getLogs");
        List<EthLog.LogResult> logResults = ethLog.getLogs();
        List<TokenTransfer> batch = new ArrayList<>();

        for (EthLog.LogResult lr : logResults) {
            Log l = (Log) lr.get();
            try {
                TokenTransfer t = eventDecoder.decode(l, chainName);
                if (t != null) {
                    batch.add(t);
                }
            } catch (Exception e) {
                long bn = l.getBlockNumber().longValue();
                log.warn("BlockScanner[{}] ERC20 decode failed block {}: {}",
                        chainName, bn, e.getMessage());
                recordError("DECODE", bn, "ERC20 Transfer: " + e.getMessage());
            }
        }
        scanEventWriter.saveTransfers(batch);
        transfersFound.increment(batch.size());
        log.debug("BlockScanner[{}] ERC20: {}-{} had {} transfers", chainName, fromBlock, toBlock, batch.size());
        StaticEventPublisher.publish(new com.lzb.indexer.dto.NewEventsEvent(chainName, "transfer", batch.size()));
        return logResults;
    }

    // ======================== Uniswap V2 Swap ========================

    /**
     * 处理 Uniswap V2 Pair 合约的 Swap 事件。
     * 过滤 topic0 = keccak256("Swap(address,uint256,uint256,uint256,uint256,address)")。
     */
    private List<EthLog.LogResult> processUniswapEvents(long fromBlock, long toBlock) throws Exception {
        EthFilter filter = new EthFilter(
                new DefaultBlockParameterNumber(fromBlock),
                new DefaultBlockParameterNumber(toBlock),
                contractAddress);
        filter.addOptionalTopics(EventDecoder.getSwapEventHash());

        EthLog ethLog = retryRpc(() -> web3j.ethGetLogs(filter).send(), "eth_getLogs");
        List<EthLog.LogResult> logResults = ethLog.getLogs();
        List<SwapEvent> batch = new ArrayList<>();

        for (EthLog.LogResult lr : logResults) {
            Log l = (Log) lr.get();
            long bn = l.getBlockNumber().longValue();
            try {
                SwapEvent swap = eventDecoder.decodeSwap(l, chainName);
                if (swap != null) {
                    batch.add(swap);
                }
            } catch (Exception e) {
                log.warn("BlockScanner[{}] Uniswap decode failed block {}: {}",
                        chainName, bn, e.getMessage());
                recordError("DECODE", bn, "Uniswap Swap: " + e.getMessage());
            }
        }
        scanEventWriter.saveSwaps(batch);
        swapsFound.increment(batch.size());
        log.info("BlockScanner[{}] Uniswap {}-{} had {} swaps", chainName, fromBlock, toBlock, batch.size());
        StaticEventPublisher.publish(new com.lzb.indexer.dto.NewEventsEvent(chainName, "swap", batch.size()));
        return logResults;
    }

    // ======================== GMX V2 ========================

    private List<EthLog.LogResult> processGmxEvents(long fromBlock, long toBlock) throws Exception {
        EthFilter filter = new EthFilter(
                new DefaultBlockParameterNumber(fromBlock),
                new DefaultBlockParameterNumber(toBlock),
                contractAddress);

        EthLog ethLog = retryRpc(() -> web3j.ethGetLogs(filter).send(), "eth_getLogs");
        if (ethLog.hasError()) {
            log.warn("BlockScanner[{}] ethGetLogs error: {}", chainName, ethLog.getError().getMessage());
            return new ArrayList<>();
        }
        List<EthLog.LogResult> logs = ethLog.getLogs();
        if (logs == null || logs.isEmpty()) return new ArrayList<>();
        log.info("BlockScanner[{}] GMX {}-{} got {} raw logs", chainName, fromBlock, toBlock, logs.size());

        int positionEventCount = 0;
        int gmxV2Match = 0;
        int decoded = 0;
        List<GmxPositionHistory> batch = new ArrayList<>();
        for (EthLog.LogResult lr : logs) {
            Log l = (Log) lr.get();
            long bn = l.getBlockNumber().longValue();

            if (!eventDecoder.isGmxV2Event(l)) continue;
            gmxV2Match++;
            if (gmxV2Match <= 5) {
                log.info("BlockScanner[{}] topic[0]={} topic[1]={}",
                        chainName, l.getTopics().get(0), l.getTopics().get(1));
            }

            try {
                GmxPositionHistory event = null;
                if (eventDecoder.isIncreasePositionEvent(l)) {
                    event = eventDecoder.decodeIncreasePosition(l, chainName);
                } else if (eventDecoder.isDecreasePositionEvent(l)) {
                    event = eventDecoder.decodeDecreasePosition(l, chainName);
                }

                if (event == null) continue;
                decoded++;
                batch.add(event);
                positionEventCount++;
            } catch (Exception e) {
                log.warn("BlockScanner[{}] GMX decode failed block {}: {}",
                        chainName, bn, e.getMessage());
                recordError("DECODE", bn, "GMX event: " + e.getMessage());
            }
        }
        scanEventWriter.saveGmxHistory(batch);
        for (GmxPositionHistory event : batch) {
            try {
                gmxPositionService.apply(event);
                positionsFound.increment();
            } catch (Exception e) {
                log.warn("BlockScanner[{}] GMX apply failed: {}", chainName, e.getMessage());
                recordError("DB", event.getBlockNumber(), "GMX apply: " + e.getMessage());
            }
        }
        log.info("BlockScanner[{}] GMX {}-{} v2Match={} decoded={} saved={}",
                chainName, fromBlock, toBlock, gmxV2Match, decoded, positionEventCount);
        StaticEventPublisher.publish(new com.lzb.indexer.dto.NewEventsEvent(chainName, "gmx", positionEventCount));
        return logs;
    }

    // ======================== 批量存区块 Hash ========================

    private void saveBlockHashesFromLogs(List<EthLog.LogResult> logResults) {
        if (logResults == null || logResults.isEmpty()) return;

        Map<Long, ScannedBlock> unique = new LinkedHashMap<>();
        for (EthLog.LogResult lr : logResults) {
            Log l = (Log) lr.get();
            long bn = l.getBlockNumber().longValue();
            if (!unique.containsKey(bn)) {
                unique.put(bn, new ScannedBlock(bn, l.getBlockHash(), chainName));
            }
        }

        List<ScannedBlock> newBlocks = new ArrayList<>();
        for (ScannedBlock sb : unique.values()) {
            if (!scannedBlockRepo.existsByBlockNumberAndChainName(sb.getBlockNumber(), chainName)) {
                newBlocks.add(sb);
            }
        }

        if (!newBlocks.isEmpty()) {
            try {
                scannedBlockRepo.saveAll(newBlocks);
                log.info("BlockScanner[{}] saved {} new block hashes", chainName, newBlocks.size());
            } catch (Exception e) {
                log.error("BlockScanner[{}] DB saveAll failed: {}", chainName, e.getMessage());
                recordError("DB", newBlocks.get(0).getBlockNumber(), "saveAll block hashes: " + e.getMessage());
            }
        }
    }

    // ======================== Reorg 检测与回滚 ========================

    private void verifyAndHandleReorg() {
        List<ScannedBlock> recentBlocks = scannedBlockRepo
                .findByChainNameOrderByBlockNumberDesc(chainName, PageRequest.of(0, reorgDepth));
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
            } else if (protocol.equals("UNISWAP_V2")) {
                swapEventRepo.deleteByChainNameAndBlockNumberGreaterThanEqual(chainName, rollbackTarget);
            } else if (protocol.startsWith("GMX")) {
                java.util.Set<String> affectedKeys = new java.util.HashSet<>();
                List<GmxPositionHistory> affected = gmxHistoryRepo
                        .findByChainNameAndBlockNumberGreaterThanEqual(chainName, rollbackTarget);
                for (GmxPositionHistory e : affected) {
                    if (e.getPositionKey() != null) {
                        affectedKeys.add(e.getPositionKey());
                    }
                }
                gmxHistoryRepo.deleteByChainNameAndBlockNumberGreaterThanEqual(chainName, rollbackTarget);
                gmxPositionService.rebuildPositions(chainName, affectedKeys);
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
