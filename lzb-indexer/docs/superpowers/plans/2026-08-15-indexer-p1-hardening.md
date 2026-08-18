# lzb-indexer P1 硬化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成 P1 三项整改——删掉半成品策略模式、扫描结果批量写入、启用 Flyway 管理 schema。

**Architecture:** 三个阶段相互独立、各自可提交可验证。Phase 1 把 EventDecoder 收敛成单一解码器（删接口/双 handler）；Phase 2 新增 `ScanEventWriter` Spring Bean 统一批量去重 + 分块 `saveAll`，BlockScanner 只负责采集；Phase 3 用 Flyway V1 建表并开启 `ddl-auto=validate`，同时修掉 `scanned_blocks` 多链主键冲突。

**Tech Stack:** Java 8、Spring Boot 2.7.18、Web3j 4.9.8、PostgreSQL 16、H2（测试）、Flyway、JUnit 5 + Mockito。

---

## 背景与现状（执行前必读）

- `EventHandler.java`、`Erc20EventHandler.java`、`UniswapV2EventHandler.java` 是未提交的半成品：接口存在但 `BlockScanner` 从不调用 `getHandler()`，三个协议分支全在 `BlockScanner` 里写死。
- `EventDecoder` 既实现了 `EventHandler`，又自带 `handlerMap` 路由，全是死代码。
- `BlockScanner` 每条日志先 `existsBy...` 再 `save`，逐条提交，无批量。
- `V1__init_schema.sql` 是 MySQL 语法且和实体对不上；`spring.flyway.enabled: false`，schema 靠 `ddl-auto: update` 生成。
- `ScannedBlock` 主键只有 `block_number`，两条链同块号会主键冲突（当前被 `saveBlockHashesFromLogs` 的 try/catch 吞掉，属于潜伏 bug）。

**测试前置条件（每个需要跑全量的 Task 前执行）：**

```powershell
Get-Process -Name anvil -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Milliseconds 800
Start-Process -FilePath 'D:\lzkcomp\foundry\anvil.exe' -ArgumentList '--port','8545','--chain-id','31337' -WindowStyle Hidden
```

---

## 文件结构总览

### Phase 1（策略模式收尾）
- 删除：`src/main/java/com/lzb/indexer/scanner/EventHandler.java`
- 删除：`src/main/java/com/lzb/indexer/scanner/Erc20EventHandler.java`
- 删除：`src/main/java/com/lzb/indexer/scanner/UniswapV2EventHandler.java`
- 修改：`src/main/java/com/lzb/indexer/scanner/EventDecoder.java`
- 修改：`src/test/java/com/lzb/indexer/scanner/EventDecoderTest.java`
- 修改：`README.md`

### Phase 2（批量写入）
- 新增：`src/main/java/com/lzb/indexer/service/ScanEventWriter.java`
- 新增：`src/test/java/com/lzb/indexer/service/ScanEventWriterTest.java`
- 修改：`src/main/java/com/lzb/indexer/scanner/BlockScanner.java`
- 修改：`src/main/java/com/lzb/indexer/scanner/BlockScannerFactory.java`
- 修改：`src/test/java/com/lzb/indexer/scanner/BlockScannerTest.java`（未跟踪文件，一并提交）
- 修改：`src/main/resources/application.yml`、`application-docker.yml`、`application-dev.yml`、`src/test/resources/application-test.yml`

### Phase 3（Flyway + 主键修复）
- 新增：`src/main/java/com/lzb/indexer/domain/entity/ScannedBlockId.java`
- 修改：`src/main/java/com/lzb/indexer/domain/entity/ScannedBlock.java`
- 重写：`src/main/resources/db/migration/V1__init_schema.sql`
- 修改：`src/main/resources/application.yml`、`application-docker.yml`

---

# Phase 1：策略模式收尾（删除半成品）

## Task 1.1：删除三个半成品文件

**Files:**
- Delete: `src/main/java/com/lzb/indexer/scanner/EventHandler.java`
- Delete: `src/main/java/com/lzb/indexer/scanner/Erc20EventHandler.java`
- Delete: `src/main/java/com/lzb/indexer/scanner/UniswapV2EventHandler.java`

- [ ] **Step 1: 用 apply_patch 删除文件**

```text
*** Begin Patch
*** Delete File: src/main/java/com/lzb/indexer/scanner/EventHandler.java
*** Delete File: src/main/java/com/lzb/indexer/scanner/Erc20EventHandler.java
*** Delete File: src/main/java/com/lzb/indexer/scanner/UniswapV2EventHandler.java
*** End Patch
```

- [ ] **Step 2: 确认删除且没有外部引用**

```powershell
Test-Path src/main/java/com/lzb/indexer/scanner/EventHandler.java   # 期望 False
Get-ChildItem -Path src/main -Recurse -Filter *.java | Select-String -Pattern 'Erc20EventHandler|UniswapV2EventHandler'  # 期望无输出
```

## Task 1.2：精简 EventDecoder

**Files:**
- Modify: `src/main/java/com/lzb/indexer/scanner/EventDecoder.java`

- [ ] **Step 1: 删除 EventHandler 相关死代码**

把当前文件顶部的这段（第 28-60 行附近）：

```java
@Component
public class EventDecoder implements EventHandler {

    private static final Logger log = LoggerFactory.getLogger(EventDecoder.class);
    /** 策略路由：key=protocol，value=对应 EventHandler */
    private final Map<String, EventHandler> handlerMap;

    /** Spring 注入所有 EventHandler Bean，加上自身组成路由表 */
    public EventDecoder(List<EventHandler> handlers) {
        this.handlerMap = new java.util.HashMap<>();
        for (EventHandler h : handlers) {
            handlerMap.put(h.getProtocol(), h);
        }
        handlerMap.put("GMX_VAULT", this);
    }

    /** 按协议名获取对应的事件处理器 */
    public EventHandler getHandler(String protocol) {
        EventHandler h = handlerMap.get(protocol);
        if (h == null) {
            log.warn("No EventHandler for protocol: {}", protocol);
        }
        return h;
    }

    // ============ EventHandler 接口方法 (GMX 代理) ============

    @Override
    public String getProtocol() { return "GMX_VAULT"; }

    @Override
    public java.util.List<String> getEventHashes() {
        return java.util.Arrays.asList(EMIT_EVENT_LOG_HASH, EMIT_EVENT_LOG1_HASH, EMIT_EVENT_LOG2_HASH);
    }
```

替换成：

```java
@Component
public class EventDecoder {

    private static final Logger log = LoggerFactory.getLogger(EventDecoder.class);
```

其余方法（`decode`、`decodeSwap`、GMX 解码、`parse*` 工具方法）全部保留不动。

- [ ] **Step 2: 确认没有残留引用**

```powershell
Get-ChildItem -Path src -Recurse -Filter *.java | Select-String -Pattern 'handlerMap|getHandler\(|getEventHashes\(|implements EventHandler'
```

期望只剩 `getTransferEventHash` / `getSwapEventHash`（静态方法，BlockScanner 在用，保留）。

## Task 1.3：修 EventDecoderTest 构造调用

**Files:**
- Modify: `src/test/java/com/lzb/indexer/scanner/EventDecoderTest.java`

- [ ] **Step 1: 改构造调用并删无用 import**

把：

```java
import java.util.Collections;
```

删除，并把：

```java
    private final EventDecoder decoder = new EventDecoder(Collections.emptyList());
```

改成：

```java
    private final EventDecoder decoder = new EventDecoder();
```

## Task 1.4：更新 README（去掉策略模式宣传）

**Files:**
- Modify: `README.md`

- [ ] **Step 1: 改架构图节点**

```text
-        ED[EventDecoder<br/>Strategy Pattern]
+        ED[EventDecoder]
```

- [ ] **Step 2: 改 Design decisions**

```text
-        - **Strategy Pattern** for event decoding — `EventHandler` interface with per-protocol implementations
+        - **集中式解码** — EventDecoder 按协议分派（ERC20 / Uniswap V2 / GMX V2）
```

- [ ] **Step 3: 改 Project Structure 描述**

```text
-        scanner/         — BlockScanner, EventDecoder, EventHandler (Strategy), Scheduler
+        scanner/         — BlockScanner, EventDecoder, ScannerScheduler
```

## Task 1.5：跑全量测试并提交

- [ ] **Step 1: 重启 Anvil（见文首命令）**
- [ ] **Step 2: 跑测试**

```powershell
mvn test 2>&1 | Select-String -Pattern 'Tests run:|BUILD SUCCESS|BUILD FAILURE'
```

期望：`Tests run: 28, Failures: 0, Errors: 0` + `BUILD SUCCESS`。

- [ ] **Step 3: 提交**

```powershell
git add -- src/main/java/com/lzb/indexer/scanner/EventDecoder.java src/test/java/com/lzb/indexer/scanner/EventDecoderTest.java README.md
git commit -m "refactor: 删除半成品 EventHandler 策略层，EventDecoder 收敛为单一解码器"
```

（三个被删文件未跟踪，无需 git add。）

---

# Phase 2：批量写入

## Task 2.1：写失败的 ScanEventWriterTest（TDD）

**Files:**
- Create: `src/test/java/com/lzb/indexer/service/ScanEventWriterTest.java`

- [ ] **Step 1: 创建测试文件**

```java
package com.lzb.indexer.service;

import com.lzb.indexer.domain.entity.TokenTransfer;
import com.lzb.indexer.domain.repository.GmxPositionHistoryRepository;
import com.lzb.indexer.domain.repository.SwapEventRepository;
import com.lzb.indexer.domain.repository.TokenTransferRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScanEventWriterTest {

    @Mock private TokenTransferRepository transferRepo;
    @Mock private SwapEventRepository swapEventRepo;
    @Mock private GmxPositionHistoryRepository gmxHistoryRepo;

    private TokenTransfer transfer(int i) {
        return new TokenTransfer("0x" + String.format("%064x", i), 1L, i,
                "0x1111111111111111111111111111111111111111",
                "0x2222222222222222222222222222222222222222",
                BigInteger.ONE, "chain");
    }

    @Test
    void saveTransfersChunksAt500() {
        when(transferRepo.existsByTxHashAndLogIndexAndChainName(any(), any(), any())).thenReturn(false);
        List<TokenTransfer> events = new ArrayList<>();
        for (int i = 0; i < 600; i++) {
            events.add(transfer(i));
        }

        ScanEventWriter writer = new ScanEventWriter(transferRepo, swapEventRepo, gmxHistoryRepo);
        writer.saveTransfers(events);

        verify(transferRepo, times(2)).saveAll(anyList());
    }

    @Test
    void saveTransfersSkipsDuplicates() {
        when(transferRepo.existsByTxHashAndLogIndexAndChainName(any(), any(), any())).thenReturn(true);
        List<TokenTransfer> events = new ArrayList<>();
        events.add(transfer(1));

        ScanEventWriter writer = new ScanEventWriter(transferRepo, swapEventRepo, gmxHistoryRepo);
        writer.saveTransfers(events);

        verify(transferRepo, never()).saveAll(anyList());
    }
}
```

- [ ] **Step 2: 跑测试确认失败（ScanEventWriter 不存在）**

```powershell
mvn test -Dtest=ScanEventWriterTest 2>&1 | Select-String -Pattern 'COMPILATION ERROR|BUILD FAILURE|ScanEventWriter'
```

期望：编译失败，提示找不到 `ScanEventWriter`。

## Task 2.2：实现 ScanEventWriter

**Files:**
- Create: `src/main/java/com/lzb/indexer/service/ScanEventWriter.java`

- [ ] **Step 1: 创建文件**

```java
package com.lzb.indexer.service;

import com.lzb.indexer.domain.entity.GmxPositionHistory;
import com.lzb.indexer.domain.entity.SwapEvent;
import com.lzb.indexer.domain.entity.TokenTransfer;
import com.lzb.indexer.domain.repository.GmxPositionHistoryRepository;
import com.lzb.indexer.domain.repository.SwapEventRepository;
import com.lzb.indexer.domain.repository.TokenTransferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 扫描结果批量持久化：先按 (tx_hash, log_index, chain_name) 去重，再每 500 条一批 saveAll。
 * 由 Spring 代理，保证每个方法整体在一个事务里。
 */
@Service
public class ScanEventWriter {

    static final int BATCH_SIZE = 500;

    private final TokenTransferRepository transferRepo;
    private final SwapEventRepository swapEventRepo;
    private final GmxPositionHistoryRepository gmxHistoryRepo;

    public ScanEventWriter(TokenTransferRepository transferRepo,
                           SwapEventRepository swapEventRepo,
                           GmxPositionHistoryRepository gmxHistoryRepo) {
        this.transferRepo = transferRepo;
        this.swapEventRepo = swapEventRepo;
        this.gmxHistoryRepo = gmxHistoryRepo;
    }

    @Transactional
    public void saveTransfers(List<TokenTransfer> events) {
        List<TokenTransfer> fresh = new ArrayList<>();
        for (TokenTransfer e : events) {
            if (!transferRepo.existsByTxHashAndLogIndexAndChainName(
                    e.getTxHash(), e.getLogIndex(), e.getChainName())) {
                fresh.add(e);
            }
        }
        saveInChunks(fresh, transferRepo::saveAll);
    }

    @Transactional
    public void saveSwaps(List<SwapEvent> events) {
        List<SwapEvent> fresh = new ArrayList<>();
        for (SwapEvent e : events) {
            if (!swapEventRepo.existsByTxHashAndLogIndexAndChainName(
                    e.getTxHash(), e.getLogIndex(), e.getChainName())) {
                fresh.add(e);
            }
        }
        saveInChunks(fresh, swapEventRepo::saveAll);
    }

    @Transactional
    public void saveGmxHistory(List<GmxPositionHistory> events) {
        List<GmxPositionHistory> fresh = new ArrayList<>();
        for (GmxPositionHistory e : events) {
            if (!gmxHistoryRepo.existsByTxHashAndLogIndexAndChainName(
                    e.getTxHash(), e.getLogIndex(), e.getChainName())) {
                fresh.add(e);
            }
        }
        saveInChunks(fresh, gmxHistoryRepo::saveAll);
    }

    private <T> void saveInChunks(List<T> items, Consumer<List<T>> saver) {
        for (int i = 0; i < items.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, items.size());
            saver.accept(new ArrayList<>(items.subList(i, end)));
        }
    }
}
```

- [ ] **Step 2: 跑测试确认通过**

```powershell
mvn test -Dtest=ScanEventWriterTest 2>&1 | Select-String -Pattern 'Tests run:|BUILD SUCCESS|BUILD FAILURE'
```

期望：`Tests run: 2, Failures: 0, Errors: 0` + `BUILD SUCCESS`。

## Task 2.3：BlockScanner 改为采集 + 批量写

**Files:**
- Modify: `src/main/java/com/lzb/indexer/scanner/BlockScanner.java`

- [ ] **Step 1: 加字段和构造参数**

在字段区加：

```java
    private final ScanEventWriter scanEventWriter;
```

构造器签名末尾加一个参数（保持现有顺序不变，只追加）：

```java
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
```

构造器体内追加：

```java
        this.scanEventWriter = scanEventWriter;
```

顶部 import 区追加：

```java
import com.lzb.indexer.service.ScanEventWriter;
```

- [ ] **Step 2: 重写 processErc20Events**

把整个方法体替换为：

```java
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
                log.warn("BlockScanner[{}] ERC20 decode failed block {}: {}", chainName, bn, e.getMessage());
                recordError("DECODE", bn, "ERC20 Transfer: " + e.getMessage());
            }
        }
        scanEventWriter.saveTransfers(batch);
        transfersFound.increment(batch.size());
        log.debug("BlockScanner[{}] ERC20: {}-{} had {} transfers", chainName, fromBlock, toBlock, batch.size());
        StaticEventPublisher.publish(new com.lzb.indexer.dto.NewEventsEvent(chainName, "transfer", batch.size()));
        return logResults;
    }
```

- [ ] **Step 3: 重写 processUniswapEvents**

```java
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
                log.warn("BlockScanner[{}] Uniswap decode failed block {}: {}", chainName, bn, e.getMessage());
                recordError("DECODE", bn, "Uniswap Swap: " + e.getMessage());
            }
        }
        scanEventWriter.saveSwaps(batch);
        swapsFound.increment(batch.size());
        log.info("BlockScanner[{}] Uniswap {}-{} had {} swaps", chainName, fromBlock, toBlock, batch.size());
        StaticEventPublisher.publish(new com.lzb.indexer.dto.NewEventsEvent(chainName, "swap", batch.size()));
        return logResults;
    }
```

- [ ] **Step 4: 重写 processGmxEvents 的写入段**

把循环里这段：

```java
                if (gmxHistoryRepo.existsByTxHashAndLogIndexAndChainName(
                        event.getTxHash(), event.getLogIndex(), chainName)) {
                    continue;
                }

                gmxHistoryRepo.save(event);
                gmxPositionService.apply(event);
                positionsFound.increment();
                positionEventCount++;
```

替换为：

```java
                batch.add(event);
                positionEventCount++;
```

并在循环前声明：

```java
        List<GmxPositionHistory> batch = new ArrayList<>();
```

循环结束后（`StaticEventPublisher.publish` 之前）插入：

```java
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
```

注意：`processGmxEvents` 里 `gmxHistoryRepo` 字段在删掉旧逻辑后不再被该方法使用，但构造器参数保留（reorg 回滚还在用）。

- [ ] **Step 5: 编译检查**

```powershell
mvn -q -DskipTests compile 2>&1 | Select-Object -Last 10
```

期望：exit=0，无编译错误。

## Task 2.4：更新 BlockScannerFactory

**Files:**
- Modify: `src/main/java/com/lzb/indexer/scanner/BlockScannerFactory.java`

- [ ] **Step 1: 注入并传递 ScanEventWriter**

```java
    private final ScanEventWriter scanEventWriter;
```

构造器加参数 `ScanEventWriter scanEventWriter`，体内 `this.scanEventWriter = scanEventWriter;`，`createAll` 里：

```java
            BlockScanner scanner = new BlockScanner(
                    cfg, eventDecoder, transferRepo, checkpointRepo, scannedBlockRepo,
                    meterRegistry, gmxHistoryRepo, gmxPositionService,
                    swapEventRepo, syncErrorRepo, scanEventWriter);
```

import 区加 `import com.lzb.indexer.service.ScanEventWriter;`。

## Task 2.5：更新 BlockScannerTest 构造调用

**Files:**
- Modify: `src/test/java/com/lzb/indexer/scanner/BlockScannerTest.java`

- [ ] **Step 1: 加 mock 并补构造参数**

```java
    @Mock private ScanEventWriter scanEventWriter;
```

三处 `new BlockScanner(...)` 末尾都补上 `, scanEventWriter`（syncErrorRepo 之后）。

## Task 2.6：配置 Hibernate 批处理

**Files:**
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/resources/application-docker.yml`
- Modify: `src/main/resources/application-dev.yml`
- Modify: `src/test/resources/application-test.yml`

- [ ] **Step 1: 四个 yml 的 `spring.jpa` 下追加**

```yaml
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        jdbc:
          batch_size: 500
          order_inserts: true
```

（`ddl-auto` 保持各文件原值；test 文件是 `update`。）

## Task 2.7：全量测试并提交

- [ ] **Step 1: 重启 Anvil（见文首命令）**
- [ ] **Step 2: 跑全量**

```powershell
mvn test 2>&1 | Select-String -Pattern 'Tests run:|BUILD SUCCESS|BUILD FAILURE'
```

期望：`Tests run: 30, Failures: 0, Errors: 0`（28 + 新增 2 个 ScanEventWriterTest）。

- [ ] **Step 3: 提交**

```powershell
git add -- src/main/java/com/lzb/indexer/service/ScanEventWriter.java src/test/java/com/lzb/indexer/service/ScanEventWriterTest.java src/main/java/com/lzb/indexer/scanner/BlockScanner.java src/main/java/com/lzb/indexer/scanner/BlockScannerFactory.java src/test/java/com/lzb/indexer/scanner/BlockScannerTest.java src/main/resources/application.yml src/main/resources/application-docker.yml src/main/resources/application-dev.yml src/test/resources/application-test.yml
git commit -m "perf: 扫描结果批量去重写入（ScanEventWriter，500/批）+ Hibernate 批处理配置"
```

---

# Phase 3：Flyway + scanned_blocks 主键修复

## Task 3.1：新增 ScannedBlockId 复合主键类

**Files:**
- Create: `src/main/java/com/lzb/indexer/domain/entity/ScannedBlockId.java`

- [ ] **Step 1: 创建文件**

```java
package com.lzb.indexer.domain.entity;

import java.io.Serializable;
import java.util.Objects;

/**
 * scanned_blocks 复合主键：同一区块号在不同链上各自独立，避免多链冲突。
 */
public class ScannedBlockId implements Serializable {

    private Long blockNumber;
    private String chainName;

    public ScannedBlockId() {}

    public ScannedBlockId(Long blockNumber, String chainName) {
        this.blockNumber = blockNumber;
        this.chainName = chainName;
    }

    public Long getBlockNumber() { return blockNumber; }
    public void setBlockNumber(Long v) { this.blockNumber = v; }
    public String getChainName() { return chainName; }
    public void setChainName(String v) { this.chainName = v; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScannedBlockId)) return false;
        ScannedBlockId that = (ScannedBlockId) o;
        return Objects.equals(blockNumber, that.blockNumber)
                && Objects.equals(chainName, that.chainName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(blockNumber, chainName);
    }
}
```

## Task 3.2：ScannedBlock 改用 @IdClass

**Files:**
- Modify: `src/main/java/com/lzb/indexer/domain/entity/ScannedBlock.java`

- [ ] **Step 1: 改类注解和主键字段**

```java
@Entity
@IdClass(ScannedBlockId.class)
@Table(name = "scanned_blocks")
public class ScannedBlock {

    @Id
    @Column(name = "block_number", nullable = false)
    private Long blockNumber;

    @Column(name = "block_hash", nullable = false, length = 66)
    private String blockHash;

    @Id
    @Column(name = "chain_name", nullable = false)
    private String chainName;
```

其余字段和方法不动。

## Task 3.3：重写 Flyway V1 迁移

**Files:**
- Rewrite: `src/main/resources/db/migration/V1__init_schema.sql`

- [ ] **Step 1: 整文件替换为以下 PostgreSQL DDL**

```sql
-- lzb-indexer 初始 schema（PostgreSQL 16）

CREATE TABLE token_transfers (
    id           BIGSERIAL PRIMARY KEY,
    tx_hash      VARCHAR(66)  NOT NULL,
    block_number BIGINT       NOT NULL,
    log_index    INT          NOT NULL,
    from_address VARCHAR(42)  NOT NULL,
    to_address   VARCHAR(42)  NOT NULL,
    amount       NUMERIC      NOT NULL,
    chain_name   VARCHAR(255) NOT NULL,
    created_at   TIMESTAMP,
    CONSTRAINT uk_transfer UNIQUE (tx_hash, log_index, chain_name)
);
CREATE INDEX idx_transfer_chain_block ON token_transfers (chain_name, block_number);
CREATE INDEX idx_transfer_chain_from ON token_transfers (chain_name, from_address);
CREATE INDEX idx_transfer_chain_to ON token_transfers (chain_name, to_address);

CREATE TABLE swap_events (
    id           BIGSERIAL PRIMARY KEY,
    tx_hash      VARCHAR(66)  NOT NULL,
    block_number BIGINT       NOT NULL,
    log_index    INT          NOT NULL,
    sender       VARCHAR(42)  NOT NULL,
    receiver     VARCHAR(42)  NOT NULL,
    amount0_in   NUMERIC      NOT NULL,
    amount1_in   NUMERIC      NOT NULL,
    amount0_out  NUMERIC      NOT NULL,
    amount1_out  NUMERIC      NOT NULL,
    chain_name   VARCHAR(255) NOT NULL,
    created_at   TIMESTAMP,
    CONSTRAINT uk_swap UNIQUE (tx_hash, log_index, chain_name)
);
CREATE INDEX idx_swap_chain_block ON swap_events (chain_name, block_number);
CREATE INDEX idx_swap_chain_sender ON swap_events (chain_name, sender);
CREATE INDEX idx_swap_chain_receiver ON swap_events (chain_name, receiver);

CREATE TABLE gmx_position_history (
    id               BIGSERIAL PRIMARY KEY,
    event_type       VARCHAR(20)  NOT NULL,
    tx_hash          VARCHAR(66)  NOT NULL,
    block_number     BIGINT       NOT NULL,
    log_index        INT          NOT NULL,
    position_key     VARCHAR(66),
    account          VARCHAR(42)  NOT NULL,
    collateral_token VARCHAR(42)  NOT NULL,
    index_token      VARCHAR(42)  NOT NULL,
    collateral_delta NUMERIC      NOT NULL,
    size_delta       NUMERIC      NOT NULL,
    is_long          BOOLEAN      NOT NULL,
    price            NUMERIC      NOT NULL,
    fee              NUMERIC,
    chain_name       VARCHAR(255) NOT NULL,
    created_at       TIMESTAMP,
    CONSTRAINT uk_gmx_history UNIQUE (tx_hash, log_index, chain_name)
);
CREATE INDEX idx_gmx_ph_account ON gmx_position_history (chain_name, account, block_number);
CREATE INDEX idx_gmx_ph_key ON gmx_position_history (chain_name, position_key);

CREATE TABLE gmx_positions (
    id                BIGSERIAL PRIMARY KEY,
    position_key      VARCHAR(66)  NOT NULL,
    account           VARCHAR(42)  NOT NULL,
    collateral_token  VARCHAR(42)  NOT NULL,
    index_token       VARCHAR(42)  NOT NULL,
    is_long           BOOLEAN      NOT NULL,
    size              NUMERIC      NOT NULL,
    collateral        NUMERIC      NOT NULL,
    average_price     NUMERIC      NOT NULL,
    total_fee         NUMERIC,
    entry_block       BIGINT       NOT NULL,
    entry_tx          VARCHAR(66)  NOT NULL,
    last_update_block BIGINT       NOT NULL,
    last_update_tx    VARCHAR(66)  NOT NULL,
    status            VARCHAR(16)  NOT NULL,
    chain_name        VARCHAR(255) NOT NULL,
    created_at        TIMESTAMP,
    updated_at        TIMESTAMP,
    CONSTRAINT uk_gmx_position UNIQUE (chain_name, position_key)
);
CREATE INDEX idx_gmx_pos_account ON gmx_positions (chain_name, account, status);
CREATE INDEX idx_gmx_pos_market ON gmx_positions (chain_name, index_token, status);

CREATE TABLE scanned_blocks (
    block_number BIGINT       NOT NULL,
    block_hash   VARCHAR(66)  NOT NULL,
    chain_name   VARCHAR(255) NOT NULL,
    created_at   TIMESTAMP,
    PRIMARY KEY (block_number, chain_name)
);

CREATE TABLE sync_checkpoints (
    id                    BIGSERIAL PRIMARY KEY,
    contract_address      VARCHAR(42)  NOT NULL,
    last_scanned_block    BIGINT       NOT NULL,
    last_scanned_tx_index INT,
    is_reorg_protected    BOOLEAN,
    chain_name            VARCHAR(255) NOT NULL,
    updated_at            TIMESTAMP,
    CONSTRAINT uk_checkpoint_contract UNIQUE (contract_address)
);

CREATE TABLE sync_errors (
    id            BIGSERIAL PRIMARY KEY,
    chain_name    VARCHAR(255) NOT NULL,
    block_number  BIGINT       NOT NULL,
    error_type    VARCHAR(20)  NOT NULL,
    error_message TEXT         NOT NULL,
    created_at    TIMESTAMP
);
CREATE INDEX idx_sync_errors_block ON sync_errors (chain_name, block_number);
```

## Task 3.4：启用 Flyway + ddl validate

**Files:**
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/resources/application-docker.yml`

- [ ] **Step 1: 两个文件的 spring 段改成**

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/lzb_indexer?sslmode=disable
    driver-class-name: org.postgresql.Driver
    username: postgres
    password: ${DB_PASSWORD:postgres}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    database-platform: org.hibernate.dialect.PostgreSQLDialect
  flyway:
    enabled: true
    baseline-on-migrate: true
```

（docker 版 datasource url 保持 `jdbc:postgresql://postgres:5432/lzb_indexer...`，其余相同。）

说明：`baseline-on-migrate: true` 让已存在的旧库（Hibernate 生成的 schema）基线到 V1 之上、跳过执行；全新库则正常执行 V1 建表。

## Task 3.5：验证

- [ ] **Step 1: 确认测试环境不受影响（test profile 仍 ddl-auto=update + flyway disabled）**

```powershell
mvn test -Dtest=EventDecoderTest 2>&1 | Select-String -Pattern 'Tests run:|BUILD SUCCESS|BUILD FAILURE'
```

期望：`Tests run: 9, Failures: 0, Errors: 0` + `BUILD SUCCESS`。

- [ ] **Step 2: 打 jar**

```powershell
mvn -q -DskipTests package
```

期望：exit=0。

- [ ] **Step 3: 用本地 PostgreSQL 验证 Flyway 启动（扫描器关掉）**

```powershell
java -Djava.net.preferIPv4Stack=true -jar target/lzb-indexer-1.0.0.jar --app.scanner.enabled=false 2>&1 | Select-String -Pattern 'Flyway|Successfully|up to date|Started LzbIndexerApplication|ERROR'
```

期望输出包含：

```text
Successfully validated 1 migration
Schema "lzb_indexer" is up to date. No migration necessary.
Started LzbIndexerApplication
```

跑完 20 秒后 `Ctrl+C` 停掉进程。

- [ ] **Step 4: 跑全量测试（先重启 Anvil）**

```powershell
mvn test 2>&1 | Select-String -Pattern 'Tests run:|BUILD SUCCESS|BUILD FAILURE'
```

期望：`Tests run: 30, Failures: 0, Errors: 0` + `BUILD SUCCESS`。

## Task 3.6：提交

- [ ] **Step 1: 提交**

```powershell
git add -- src/main/java/com/lzb/indexer/domain/entity/ScannedBlockId.java src/main/java/com/lzb/indexer/domain/entity/ScannedBlock.java src/main/resources/db/migration/V1__init_schema.sql src/main/resources/application.yml src/main/resources/application-docker.yml
git commit -m "feat: 启用 Flyway 管理 schema（V1 PostgreSQL DDL + ddl validate），修复 scanned_blocks 多链复合主键"
```

---

## 验收总清单

- [ ] `mvn test` 全绿（Phase 1 后 28、Phase 2/3 后 30）
- [ ] `git log` 出现三个阶段各自的 commit
- [ ] 本地 PostgreSQL 启动时 Flyway 校验通过，日志出现 `Schema "lzb_indexer" is up to date`
- [ ] `src/main` 下不再有 `EventHandler` / `handlerMap` / `getEventHashes`
- [ ] BlockScanner 不再逐条 `save`，全部走 `ScanEventWriter`

## 本期不处理（记录在案）

- `sync_checkpoints.contract_address` 唯一约束不含 chain_name（多链同合约地址会冲突）——当前三条链地址不同，暂不修。
- git 历史里的私钥/Infura key 重写——需要用户确认后单独做。
- 集成测试仍依赖本地 Anvil 且需每次重启（README 已写明）。
