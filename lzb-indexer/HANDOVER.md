# lzb-indexer 交接文档（HANDOVER）

> 写给完全没有上下文的新会话。先读这份文档，再动任何代码。

## 0. 三秒速览

- **项目**：多链 EVM 事件索引器（ERC20 / Uniswap V2 / GMX V2），Java 8 + Spring Boot 2.7.18 + Web3j 4.9.8 + PostgreSQL 16。
- **位置**：git 仓库根 `D:\lzkcomp\web3`（注意：不是 lzb-indexer），项目目录 `D:\lzkcomp\web3\lzb-indexer`，当前分支 `master`。
- **状态**：33/33 测试全绿（上次验证）；`master` 领先 `origin/master` 2 个 commit 未 push。
- **用户画像**：3.5 年 Java 后端，目标海外远程 Web3 后端；暴躁老哥，要事实不要安慰；**注释一律英文**。

## 1. 我们正在做什么

把 lzb-indexer 从“能跑的个人项目”打磨成“能扛住面试深挖 + 能公开仓库”的作品，同时准备 Web3 后端求职。

当前主线：**项目硬化（P0/P1/P2 已完成）+ 求职准备（投递/面试）**。

## 2. 已完成（按里程碑）

### P0：解码正确性 + 基础设施修复
- **GMX V2 解码链路修复**：测试合约曾与主网 ABI 不一致（topic0 对不上），重写 `TestGmxVault.sol` 为字节级复刻主网自定义编码；`EventDecoder` 常量对齐官方 ABI（EventLog/EventLog1/EventLog2），删掉只匹配旧测试合约的假哈希，修复 EventLog2 data 偏移（word2 而不是 word1）。
- **reorg 检测修正**：原来查“最早”12 块（永远不检查链头），改为查最近 N 块；GMX 回滚后按事件流重放快照（`GmxPositionService.rebuildPositions`）。
- **GmxPosition.status 改枚举**：String → `@Enumerated(STRING)`，修复“平仓后重开不恢复 OPEN”。

### P1：工程硬化
- 删除半成品 EventHandler 策略层（接口 + 两个 handler 从未被调用），EventDecoder 收敛为单一解码器。
- 批量写入：新增 `ScanEventWriter`（按 tx_hash+log_index+chain_name 去重，500/批 `saveAll`，`@Transactional`），BlockScanner 只采集不逐条写；Hibernate `batch_size=500` + `order_inserts`。
- Flyway 启用：重写 `V1__init_schema.sql`（PostgreSQL DDL），`ddl-auto=validate` + `baseline-on-migrate=true`（已有库基线跳过，新库执行 V1）。
- 修复 `scanned_blocks` 多链复合主键（新增 `ScannedBlockId`，block_number+chain_name）。
- 修复潜伏 bug：`TokenService` 的 `Address.DEFAULT` 类型不兼容（被陈旧编译产物掩盖过）。

### P2：安全加固
- actuator：`/actuator/health` 开放，其余 `/actuator/**` 需 Basic Auth（默认 `admin/admin123`，环境变量 `ACTUATOR_USERNAME`/`ACTUATOR_PASSWORD` 覆盖）。
- WebSocket `/ws` 只允许 `localhost:*` / `127.0.0.1:*`。
- Prometheus 抓取带 `basic_auth`；新增 `SecurityConfigTest`。

### 其他
- RPC 全部改为免 key 公共端点（publicnode），支持环境变量覆盖：`SEPOLIA_RPC_URL` / `ETHEREUM_RPC_URL` / `ARBITRUM_RPC_URL`。
- 仓库卫生：pgdata/node_modules/.env/ROADMAP 移出 git；Infura key 移除；LICENSE 补上；`.env` 已删除（内含已泄露私钥）。
- indexer 只读化：删除 `TokenService.transfer` + `POST /api/token/transfer`，不再持有/使用任何私钥。
- 注释乱码全清 + 约定改英文（AGENTS.md 已更新）。
- trading 模块已迁出到 `D:\lzkcomp\web3\lzb-trading`（独立项目，未建仓）。
- 面试资料：`docs/web3-interview-notes.md`（Web3 八股 + 项目自问清单）、`docs/superpowers/plans/2026-08-15-indexer-p1-hardening.md`（已执行完的计划）。

## 3. 当前卡点 / 待办

### 必须尽快做（用户本人）
1. **push 剩余 2 个 commit**：`92db07b`（RPC 免 key）+ `888f450`（计划文档与 IDE 配置）。
   - 凭据：`credential.helper=store`；push 提示时 Username=`Neilbbkk`，Password=GitHub PAT（**不要让用户把 token 发到聊天里**）。
   - 网络：必要时 `git -c http.proxy=http://127.0.0.1:7897 push origin master`。
2. **撤销旧 GitHub PAT**：`ghp_zYlsv...` 曾明文出现在 remote URL 和聊天记录中，已从 remote URL 移除，必须视为泄露并撤销。
3. **钱包私钥处置（已完成大半）**：钱包 `0x3642287697C85eEB038C04aA00Da55b059B00593` 的私钥 `0xdcc9...bcba` 曾进入 git 历史，`.env` 已删；**不要复用这把私钥**。历史重写（filter-repo）未做，用户知晓并暂缓。

### 项目剩余改进（可选）
- 缺 reorg 回滚的专项集成测试（回滚逻辑目前靠现有测试间接覆盖）。
- `sync_checkpoints.contract_address` 唯一约束不含 `chain_name`：多链同合约地址会冲突（当前三条链地址不同，暂不影响）。
- `pom.xml` 未显式配置 `project.build.sourceEncoding=UTF-8`（建议补，避免 IDE/命令行编码分歧）。
- 公共 RPC 适合 demo，生产建议换 Infura/Alchemy 并用环境变量。
- `blog-gmx-eventemitter-abi.md` 未发布（用户计划发 Medium/Dev.to）。

## 4. 下一步计划

1. push 剩余 commit（用户输入 token）。
2. 用户通读代码（数据流顺序：ChainProperties → BlockScanner → EventDecoder → ScanEventWriter → GmxPositionService → Scheduler → Controller → WebSocket/Security），手画架构图，对照 `docs/web3-interview-notes.md` 末尾的“项目自问清单”自测。
3. 发博文、写 SQL 校验脚本、每天投 2-3 份简历、练 STAR 故事 + 英语口述 + 真人 mock 面试。

## 5. 环境与常用命令

- JDK 8：`C:\PROGRA~1\Java\jdk1.8.0_311`；Maven 3.8.4（D:\Maven）；Foundry（forge/anvil）在 `D:\lzkcomp\foundry`。
- 编译：`mvn -q clean test-compile`（**有诡异报错先 clean**）。
- 全量测试：先重启 Anvil，再 `mvn test`：
  ```powershell
  Get-Process -Name anvil -ErrorAction SilentlyContinue | Stop-Process -Force
  Start-Sleep -Milliseconds 800
  Start-Process -FilePath 'D:\lzkcomp\foundry\anvil.exe' -ArgumentList '--port','8545','--chain-id','31337' -WindowStyle Hidden
  ```
  或一键脚本：`powershell -File scripts/run-integration-test.ps1`。
- 运行：`java -jar target/lzb-indexer-1.0.0.jar`（默认 8080；PostgreSQL 数据在仓库内 pgdata，已 gitignore，PostgreSQL 16 裸机正在跑）。

## 6. 踩过的坑，绝对不要再踩

### 环境/工具
1. **跑集成测试前必须重启 Anvil**。Anvil 不重启区块高度会累积，超过测试 page-size=100 导致扫不到新交易（“testScanFindsMint 红”经典症状）。
2. **编译诡异报错先 `mvn clean`**。本项目多次被陈旧 target 产物坑（`Unresolved compilation problem`、`Address.DEFAULT` 类型错误都是这么被掩盖/暴露的）。
3. **PowerShell 里 curl 传 JSON 引号会被吃**。用临时文件：`[System.IO.File]::WriteAllText($tmp, $json)` + `curl -d "@$tmp"`。
4. **PowerShell 会把 `-Djava.net.preferIPv4Stack=true` 拆坏**（报“找不到主类 .net.preferIPv4Stack=true”）。要么去掉该参数，要么加引号。
5. **不要用复杂 PowerShell 脚本批量改文件**（嵌套 here-string / 引号地狱必翻车）。文件编辑用 apply_patch；纯机械替换（如按行号换注释）用简单脚本。
6. **策略拦截**：`Remove-Item -Recurse`、`Stop-Process` 混在复杂命令里会被拒。删文件用 apply_patch；起/停进程拆成单条命令。
7. **子代理通道是坏的（环境 bug）**：`spawn_agent` / `followup_task` / `send_message` 的任务正文 DeepSeek 收不到（MultiAgentV2 的 `encrypted_content` 字段第三方模型读不懂）。**别浪费时间派子代理**；要修就改 `C:\Users\梁智康\.codex\models.json` 把 `multi_agent_version` 从 `v2` 改 `v1` 再重启 Codex。GitHub issue：#36493 / #36321 / #36586。

### Git/安全
8. **不要把 token/私钥嵌进 remote URL**（会明文泄露进 git 配置和聊天记录）。用凭据管理器或 store helper；token 只输入终端。
9. **不要把私钥/PAT 发到聊天里**。私钥 `0xdcc9...bcba` 已经这么泄露过一次。
10. **仓库根是 `D:\lzkcomp\web3`，不是 lzb-indexer**。git 命令在项目目录跑时路径要带 `lzb-indexer/` 前缀（rev:path 语法），或者直接切到项目目录用相对路径。
11. **在 master 上执行计划前先开分支**（`codex/` 前缀），除非用户明确同意。

### Spring/测试
12. **`@SpringBootTest` 默认禁用 metrics export**：测试里 `/actuator/prometheus` 会 404，必须加 `@AutoConfigureMetrics` + 显式 exposure 属性。
13. **EventDecoderTest 里局部变量 `Log log` 会遮蔽 logger**：该文件 logger 用 `LOGGER`。
14. **测试顺序依赖**：集成测试查询区块范围不要写死 0~10（GMX 测试先跑会推高 Anvil 高度），按当前链高查询。
15. **LF/CRLF warning 无害**，忽略。

### 领域
16. **GMX EventLogData 不是标准 ABI 编码**（嵌套 items/arrayItems、内联 bytes32 key、偏移以 length 字为基准），别试图用 Solidity 标准结构体编码去模拟主网日志。
17. **`scanned_blocks` 主键必须是 (block_number, chain_name)**，单 block_number 会多链冲突。
18. **Flyway 已启用 + baseline**：已有库会基线到 V1 跳过迁移，新库执行 V1；别手改 `flyway_schema_history`。

## 7. 快速上手（新会话第一步）

```powershell
cd D:\lzkcomp\web3\lzb-indexer
git status            # 预期：2 个未 push commit + 若干未跟踪个人文件
git log --oneline -5
mvn -q clean test-compile   # 确认能编译
```

读代码顺序：`ChainProperties` → `BlockScanner` → `EventDecoder` → `ScanEventWriter` → `GmxPositionService` → `ScannerScheduler` → `controller/*` → `config/WebSocketConfig` + `SecurityConfig`。
