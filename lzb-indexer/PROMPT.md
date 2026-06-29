# lzb-indexer 项目对话完整摘要

## 用户画像

- UNSW CS 本科，3.5 年 Java 后端开发
- 目标：海外远程区块链后端（非 Solidity，非全栈），北美 $100K+ 或澳洲
- 性格：暴躁老哥，要事实不要安慰，动手前先讲思路让我确认，别自己哼哧一顿改
- 钱包：0x3642287697C85eEB038C04aA00Da55b059B00593
- LZB Token (ERC20, Sepolia)：0x8f15B4F7F145f3F0B17C231746377F76f5771Be8

## 环境约束（铁律）

- 所有软件安装到 D 盘，禁止 C 盘
- 编码 UTF-8 无 BOM
- Java 8 + Maven 3.8 + Spring Boot 2.7.18 + Web3j 4.9.8
- Docker Desktop 在 D:\lzkcomp\Docker，数据卷 D:\pgdata
- PostgreSQL 16 裸机在 D:\lzkcomp\pgsql (后改为 D:\pgsql16, data D:\pgdata)
- 代理：Clash Verge，mixed 端口 7897，需开启"允许局域网连接"
- Docker Desktop Settings → Resources → Proxies 配 http://127.0.0.1:7897
- 网络不稳：Docker Hub、Sepolia RPC 间歇性被墙
- PowerShell 注意中文路径 + 参数引号问题
- 写文件用 Python 或 [System.IO.File]::WriteAllText，不要用 apply_patch
- 正则替换代码容易搞坏，大改动直接重写整个文件

## 技术栈习惯

- Foundry 不是 Hardhat
- Web3j 4.9.8
- 转账用 call{value:}("") + require(success)
- Event 比 storage 便宜 50 倍
- Checks-Effects-Interactions 模式防重入
- 构造器注入，不用 @Autowired/@Resource 字段注入
- JPA 不是 MyBatis-Plus（海外求职 JPA 是标准答案）
- @Value 被 PowerShell 吞变量，改用 Environment 或 @ConfigurationProperties

## 项目：lzb-indexer

**位置：** D:\lzkcomp\web3\lzb-indexer
**技术栈：** Java 8 + Maven 3.8 + Spring Boot 2.7.18 + Web3j 4.9.8 + PostgreSQL + H2(test)

### 核心架构（多链改造后）

```
Spring 容器
  ├── ChainProperties (@ConfigurationProperties) —— 读 app.chains[] 列表
  ├── BlockScannerFactory —— 按 chain 配置创建 BlockScanner 实例
  │     ├── new BlockScanner(sepolia) —— 自建 Web3j、自管状态
  │     └── new BlockScanner(mumbai)   —— 加链只需 YAML 加一项
  ├── ScannerScheduler —— 遍历所有 scanner，@Scheduled 定时触发
  ├── EventDecoder —— ABI 解码 Transfer + GMX V2 emitEventLog
  ├── TokenService —— 链上交互（balanceOf, transfer）
  ├── GmxPositionService —— 事件流水 → 持仓快照聚合
  └── 各 Repository —— JPA 数据访问，全部带 chainName 过滤
```

### docker-compose（5 容器）

| 服务 | 端口 | 备注 |
|------|------|------|
| postgres | 5432 | |
| pgadmin | 5050 | admin@admin.com / admin |
| prometheus | 9090 | 5s 刮一次 /actuator/prometheus |
| grafana | 3100 | admin / admin，仪表盘 LZB Indexer |
| lzb-indexer | 8080 | ⚠️ Docker 内 Alpine SSL 有问题，本机 java -jar 跑 |

### 多链配置结构

```yaml
app:
  scanner:
    enabled: true
    fixed-rate-ms: 5000
  chains:
    - name: sepolia
      rpc-url: "https://ethereum-sepolia.publicnode.com"
      contract-address: "0x8f15B4F7F145f3F0B17C231746377F76f5771Be8"
      wallet-address: "0x3642287697C85eEB038C04aA00Da55b059B00593"
      private-key: "0x..."
      start-block: 6900000
      page-size: 2000
      reorg-depth: 12
    - name: arbitrum-gmx-vault
      rpc-url: "https://arb1.arbitrum.io/rpc"
      contract-address: "0xC8ee91A54287DB53897056e12D9819156D3822Fb"
      protocol: GMX_VAULT
      start-block: 450000000
      page-size: 2000
      reorg-depth: 12
```

## 数据库表

| 表 | 用途 | 唯一键 |
|----|------|--------|
| sync_checkpoints | 扫块进度 | chain_name + contract_address |
| scanned_blocks | 区块 hash 记录（reorg 检测） | chain_name + block_number |
| token_transfers | Transfer 事件 | tx_hash + log_index + chain_name |
| sync_errors | 异常记录 | — |
| gmx_position_history | GMX V2 仓位事件流水（event sourcing） | tx_hash + log_index + chain_name |
| gmx_positions | GMX V2 当前持仓聚合快照 | chain_name + position_key |

### gmx_position_history 字段

event_type(INCREASE/DECREASE/LIQUIDATE), tx_hash, block_number, log_index,
position_key, account, collateral_token, index_token, collateral_delta,
size_delta, is_long, price, fee, chain_name, created_at

索引：idx_gmx_ph_account(chain_name, account, block_number), idx_gmx_ph_key(chain_name, position_key)

### gmx_positions 字段

position_key, account, collateral_token, index_token, is_long,
size, collateral, average_price, total_fee, entry_block, entry_tx,
last_update_block, last_update_tx, status(OPEN/CLOSED/LIQUIDATED),
chain_name, created_at, updated_at

状态机：OPEN → (size=0) → CLOSED | OPEN → (被清算) → LIQUIDATED

## Prometheus 指标（按链前缀）

- scanner.{chain}.blocks.processed — Counter，累计扫块数
- scanner.{chain}.transfers.found — Counter，累计 Transfer 数
- scanner.{chain}.positions.found — Counter，累计 GMX 事件数
- scanner.{chain}.scan.duration — Timer，每次 scan 耗时
- scanner.{chain}.last.block — Gauge，最新已扫块号
- scanner.{chain}.chain.tip — Gauge，链当前高度

## REST API

- GET /api/indexer/status[?chain=sepolia] — 返回所有（或指定）链的扫描状态
- GET /api/transfers?address=0x...&chain=sepolia&page=0&size=20
- GET /api/token/info、/api/token/balance/{address}、/api/token/my-balance
- POST /api/token/transfer?to=...&amount=...
- GET /actuator/prometheus — Prometheus 刮取端点

## 进度条

```
ERC20 基础索引          ██████████████████████████████  100%
多链 + Reorg 保护       ██████████████████████████████  100%
Prometheus/Grafana      ██████████████████████████████  100%
集成测试 (Anvil)         ██████████████████████████████  100%
Docker/生产化            █████████████████████████░░░░░   85%
GMX V2 仓位跟踪          ████████████████████████░░░░░░   80%  ← 5/6 字段正常
CI/CD                   ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░    0%
第二个项目               ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░    0%
简历包装                 ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░    0%
```

## 学习路线

ER C20 索引（已完成）→ 多链改造（已完 成）→ DeFi 链上数据深入（GMX V2 进行中）
→ 链下 Keeper Bot / 价格预言机 → 简历包装 → 投递

## 关键文件路径

| 文件 | 路径 |
|------|------|
| 项目根 | D:\lzkcomp\web3\lzb-indexer |
| BlockScanner | src/main/java/com/lzb/indexer/scanner/BlockScanner.java |
| BlockScannerFactory | src/main/java/com/lzb/indexer/scanner/BlockScannerFactory.java |
| ScannerScheduler | src/main/java/com/lzb/indexer/scanner/ScannerScheduler.java |
| EventDecoder | src/main/java/com/lzb/indexer/scanner/EventDecoder.java |
| ChainProperties | src/main/java/com/lzb/indexer/config/ChainProperties.java |
| ScannerMetricsAspect | src/main/java/com/lzb/indexer/aspect/ScannerMetricsAspect.java |
| TokenService | src/main/java/com/lzb/indexer/service/TokenService.java |
| TokenTransfer | src/main/java/com/lzb/indexer/domain/entity/TokenTransfer.java |
| SyncCheckpoint | src/main/java/com/lzb/indexer/domain/entity/SyncCheckpoint.java |
| ScannedBlock | src/main/java/com/lzb/indexer/domain/entity/ScannedBlock.java |
| Repositories | src/main/java/com/lzb/indexer/domain/repository/*.java |
| IndexerController | src/main/java/com/lzb/indexer/controller/IndexerController.java |
| TransferController | src/main/java/com/lzb/indexer/controller/TransferController.java |
| application.yml | src/main/resources/application.yml |
| application-docker.yml | src/main/resources/application-docker.yml |
| application-dev.yml | src/main/resources/application-dev.yml |
| application-test.yml | src/test/resources/application-test.yml |
| Dockerfile | D:\lzkcomp\web3\lzb-indexer\Dockerfile |
| docker-compose.yml | D:\lzkcomp\web3\lzb-indexer\docker-compose.yml |
| prometheus.yml | D:\lzkcomp\web3\lzb-indexer\prometheus.yml |
| Grafana 仪表盘 | D:\lzkcomp\web3\lzb-indexer\grafana-dashboards\lzb-indexer.json |
| 集成测试 | src/test/java/com/lzb/indexer/BlockScannerIntegrationTest.java |
| 测试 Solidity | src/test/solidity/ |
| PROMPT.md | D:\lzkcomp\web3\lzb-indexer\PROMPT.md |
| AGENTS.md | D:\lzkcomp\web3\lzb-indexer\AGENTS.md |
| PostgreSQL 裸机 | D:\pgsql16 (data: D:\pgdata) |
| Docker CLI | D:\lzkcomp\Docker\resources\bin\docker.exe |

### 2026-06-28 新增文件（GMX V2）

| 文件 | 路径 |
|------|------|
| GmxPositionHistory | src/main/java/com/lzb/indexer/domain/entity/GmxPositionHistory.java |
| GmxPosition | src/main/java/com/lzb/indexer/domain/entity/GmxPosition.java |
| GmxPositionService | src/main/java/com/lzb/indexer/service/GmxPositionService.java |
| GmxPositionHistoryRepository | src/main/java/com/lzb/indexer/domain/repository/GmxPositionHistoryRepository.java |
| GmxPositionRepository | src/main/java/com/lzb/indexer/domain/repository/GmxPositionRepository.java |

## 成果（2026-06-11 ~ 06-14）

- pom.xml 格式修复 + spring-boot-starter-aop 正确加入
- AOP 切面 pointcut 修复（execution(public void ...)）
- Docker 代理配置（Clash LAN + Docker Desktop Proxies）
- Prometheus + Grafana 可视化（7 面板仪表盘）
- lzb-indexer Docker 化（Alpine SSL 未彻底解决，暂本机跑）
- 多链支持完整改造：ChainProperties 配置类、BlockScanner 去 @Component per-chain 实例、BlockScannerFactory 组装、ScannerScheduler 遍历多 Scanner、所有实体/Repository/Controller 加 chainName、指标按链前缀、测试修复
- 本机验证通过：page-size=2000，Sepolia 扫块正常

## 成果（2026-06-28 — GMX V2 仓位跟踪）

### GMX V2 事件解码机制

- 合约：0xC8ee91A54287DB53897056e12D9819156D3822Fb（Arbitrum EventEmitter）
- 部署块：450,000,000
- RPC：https://arb1.arbitrum.io/rpc
- V2 用通用事件 emitEventLog/emitEventLog2（非 V1 命名事件）
- topic[0] = 0x137a44... (emitEventLog) 或 0x468a25... (emitEventLog2)
- topic[1] = keccak256(eventName)："PositionIncrease" 或 "PositionDecrease"
- 数据体：EventUtils.EventLogData（7 元组键值对：addressItems, uintItems, intItems, boolItems, bytes32Items, bytesItems, stringItems）
- event data 布局：[msgSender(32B)][eventName_offset(32B)][eventData_offset(32B)][eventName 数据][EventLogData abi encode]

### 已修复的 5 个 Bug

**1. parse*KV 方法双重偏移 bug**

根因：structCharOff 已指向数组数据起始（第一字节=length），但方法内部又把 length 当 offset 跳了一次，导致地址/布尔解析全部失败。

修复：删掉 `itemsOff = structCharOff + bytesToBigInt(hex, structCharOff).intValue() * 2`，直接在 structCharOff 读 length。

影响文件：EventDecoder.java 的 parseAddrKV, parseUintKV, parseBoolKV, parseKV32 四个方法。

**2. applyDecrease 双重取反 bug**

根因：EventDecoder.decodePosition 对 DECREASE 事件已经把 sizeDelta/collateralDelta 取负了。GmxPositionService.applyDecrease 又对它们 .negate() 了一次 → 减仓变成加仓。

修复：applyDecrease 直接用 e.getSizeDelta() 和 e.getCollateralDelta()，不再取反。

影响文件：GmxPositionService.java

**3. entry_tx 始终为空**

根因：GmxPosition.open() 工厂方法不接受 txHash 参数，写死传 ""。

修复：open() 加 String txHash 参数，所有调用处（applyIncrease, applyDecrease, applyLiquidate）传入 e.getTxHash()。

影响文件：GmxPosition.java, GmxPositionService.java

**4. is_long 始终为 false**

根因：部署在 Arbitrum 上的 EventEmitter 合约 EventLogData 结构体字段顺序与 GitHub 源码不同。boolItems 在 relOff[2] 而非 relOff[3]。

修复：parseBoolKV(hex, edOffChar + relOff[2] * 2, bools)

调试过程：尝试了 relOff 索引 0, 1, 2, 3, 5, 6 共 6 种排列，最终确认 index 2 是 boolItems。

**5. collateral_token 始终为空**

根因：addressItems 的值槽存的不是内联地址（20 bytes left-padded），而是动态 offset（值 < 10000，如 0x40=64）。需要跟随 offset 跳转才能读到真正的地址值。

修复：parseAddrKV 里加判断——value 槽的值 < 10000 且 > 0 时，当成 byte offset 跟随跳转。

```java
BigInteger rawVal = bytesToBigInt(hex, cursor + 64);
if (rawVal.compareTo(BigInteger.valueOf(10000)) < 0 && rawVal.signum() > 0) {
    int valOff = (cursor / 2 + rawVal.intValue()) * 2;
    val = "0x" + hex.substring(valOff + 24, valOff + 64);
}
```

注：当前取到的地址是账户地址（account），不是代币地址（如 USDC）。根因是 addressItems 整体偏移位置还有偏差——部署版合约 EventLogData 字段顺序与 GitHub 源码不一致。

### 当前状态（27 events from blocks 450M-452M）

| 字段 | 状态 |
|------|------|
| position_key | ✅ 正常（从 bytes32Items["orderKey"]） |
| account | ✅ 正常（从 topic fallback） |
| collateral_token | ✅ 27/27 有值（取到的是账户地址，非代币地址） |
| index_token | ❌ 仍为空（addr map 无 "indexToken"/"market" key） |
| is_long | ✅ 20 true + 7 false |
| size_delta / collateral_delta | ✅ 正常 |
| price / fee | ✅ 正常 |
| entry_tx | ✅ 已填充 |

gmx_positions：12 CLOSED + 15 OPEN

### 未解决问题

**index_token 始终为空** — addressItems 的精确位置未定位。relOff[0] 指向的数据实际是 uintItems（从 addr keys 里能看到 "sizeInUsd" 等 uint key 名）。部署版合约 EventLogData 字段顺序与 GitHub 源码不一致。

排查方向：
1. 在 Arbiscan 上查已验证合约源码，确认 EventLogData struct 实际字段顺序
2. 或 dump 完整 event data hex，手动追踪 "indexToken" 字符串位置

### 编译常见坑（新增）

- BOM：PowerShell WriteAllText 加 BOM → Java 8 报 \ufeff → 用 WriteAllBytes 去头 3 字节
- git checkout：未提交的修改会被还原（V2 代码全丢）→ Python 重写完整文件
- Arbitrum 公共 RPC 限流：Python urllib、Invoke-WebRequest 都 403 → 只能本地日志调试
- Hibernate 映射报错 → @Column 与 @Table 字段对齐

### 运行方式

```powershell
# dev 环境（→ lzb_indexer_dev DB）
$env:SPRING_PROFILES_ACTIVE="dev"
$env:DB_PASSWORD="postgres"
java -jar target/lzb-indexer-1.0.0.jar

# 查库
$env:PGPASSWORD='postgres'
D:\pgsql16\bin\psql.exe -U postgres -d lzb_indexer_dev

# 清数据重扫
D:\pgsql16\bin\psql.exe -U postgres -d lzb_indexer_dev -c "DELETE FROM gmx_positions; DELETE FROM gmx_position_history; DELETE FROM sync_checkpoints;"
```
