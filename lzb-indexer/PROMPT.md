# lzb-indexer 项目对话完整摘要 — 2026-06-11 至 2026-06-14

## 用户画像
- UNSW CS 本科，3.5 年 Java 后端开发
- 目标：海外远程区块链后端（非 Solidity，非全栈），北美 $100K+ 或澳洲
- 性格：暴躁老哥，要事实不要安慰，动手前先讲思路让我确认，别自己哼哧一顿改
- 钱包：0x3642287697C85eEB038C04aA00Da55b059B00593
- LZB Token (ERC20, Sepolia)：0x8f15B4F7F145f3F0B17C231746377F76f5771Be8

## 环境约束（铁律）
- 所有软件安装到 D 盘，禁止 C 盘
- 编码 UTF-8 无 BOM（中文用户名"梁智康"踩过坑）
- Java 8 + Maven 3.8 + Spring Boot 2.7.18 + Web3j 4.9.8
- Docker Desktop 在 D:\lzkcomp\Docker，数据卷 D:\pgdata
- PostgreSQL 16 裸机在 D:\lzkcomp\pgsql
- 代理：Clash Verge，mixed 端口 7897，需开启"允许局域网连接"
- Docker Desktop Settings → Resources → Proxies 配 http://127.0.0.1:7897
- 网络不稳：Docker Hub、Sepolia RPC 间歇性被封
- PowerShell 注意中文路径 + 参数引号问题
- 写文件用 [System.IO.File]::WriteAllText，不要用 apply_patch
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
位置：D:\lzkcomp\web3\lzb-indexer
技术栈：Java 8 + Maven 3.8 + Spring Boot 2.7.18 + Web3j 4.9.8 + PostgreSQL + H2(test)

### 核心架构（多链改造后）
```
Spring 容器
  ├── ChainProperties (@ConfigurationProperties) —— 读 app.chains[] 列表
  ├── BlockScannerFactory —— 按 chain 配置创建 BlockScanner 实例
  │     ├── new BlockScanner(sepolia) —— 自建 Web3j、自管状态
  │     └── new BlockScanner(mumbai)   —— 加链只需 YAML 加一项
  ├── ScannerScheduler —— 遍历所有 scanner，@Scheduled 定时触发
  ├── EventDecoder —— ABI 解码 Transfer(address,address,uint256)
  ├── TokenService —— 链上交互（balanceOf, transfer）
  └── 各 Repository —— JPA 数据访问，全部带 chainName 过滤

docker-compose（5 容器）：
  postgres — 5432
  pgadmin — 5050（admin@admin.com / admin）
  prometheus — 9090（5s 刮一次 /actuator/prometheus）
  grafana — 3100（admin / admin，仪表盘 LZB Indexer）
  lzb-indexer — 8080（⚠️ Docker 内 Alpine SSL 有问题，本机 java -jar 跑）
```

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
      page-size: 2000     # ← 10000 太大公共 RPC 超时，2000 刚好
      reorg-depth: 12
```

### 数据库表
- sync_checkpoints — 扫块进度（chain_name + contract_address 联合唯一）
- scanned_blocks — 区块 hash 记录（reorg 检测用，chain_name 隔离）
- token_transfers — Transfer 事件（chain_name 隔离，tx_hash+log_index+chain_name 去重）
- sync_errors — 异常记录

### Prometheus 指标（按链前缀）
- scanner.{chain}.blocks.processed — Counter，累计扫块数
- scanner.{chain}.transfers.found — Counter，累计 Transfer 数
- scanner.{chain}.scan.duration — Timer，每次 scan 耗时
- scanner.{chain}.last.block — Gauge，最新已扫块号
- scanner.{chain}.chain.tip — Gauge，链当前高度

### REST API
- GET /api/indexer/status[?chain=sepolia] — 返回所有（或指定）链的扫描状态
- GET /api/transfers?address=0x...&chain=sepolia&page=0&size=20
- GET /api/token/info、/api/token/balance/{address}、/api/token/my-balance
- POST /api/token/transfer?to=...&amount=...
- GET /actuator/prometheus — Prometheus 刮取端点

### 已修复的技术坑（别再踩）
1. **pom.xml 格式** — AOP 依赖不能和 actuator 挤同一行，重写干净
2. **AOP pointcut** — Spring AOP 匹配 void 方法要用 `execution(public void ...)`，不能用 `execution(* ...)`
3. **Docker 代理** — Clash 必须开"允许局域网连接"，否则 Docker 内部连不上
4. **application.yml vs application-docker.yml** — 区别在 datasource.url（localhost vs postgres 容器名），通过 SPRING_PROFILES_ACTIVE=docker 切换
5. **Alpine JRE SSL** — eclipse-temurin:8-jre-alpine 缺 Java cacerts，curl 能通 Java 不通。暂用本机跑回避
6. **ethGetLogs 超时** — 公共 RPC 扫 10000 块太慢/超时，page-size 降到 2000
7. **多链改造后 Web3jConfig** — 旧 YAML 的 web3j.rpc-url 不存在了，删掉 Web3jConfig，TokenService 改 ChainProperties 自建 Web3j
8. **SyncCheckpoint 构造函数** — 多链后加了 chainName 参数，new SyncCheckpoint(contractAddress, startBlock, chainName)
9. **ScannedBlock 构造函数** — 同 new ScannedBlock(blockNumber, blockHash, chainName)
10. **EventDecoder.decode** — 新签名 decode(Log logEntry, String chainName)，不再返回 Optional

### 已修复的 Dockerfile
```dockerfile
FROM eclipse-temurin:8-jre-alpine
RUN apk add --no-cache ca-certificates tzdata curl
WORKDIR /app
COPY target/lzb-indexer-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Djava.net.preferIPv4Stack=true", "-jar", "/app/app.jar"]
```

### 协作规则
1. 动手前先讲思路，等我确认，别自己一顿改
2. 每一学习阶段结束出题考察我
3. 改完代码必须编译验证（mvn compile）
4. 安装文件到 D 盘，禁止 C 盘
5. 实话实说，不要安慰，别废话

## 当前进度与能力地图
```
Java 后端基础        ████████████████████  100%
Foundry/Solidity     ██████████░░░░░░░░░░   50%
Web3j 索引器         ████████████████████   95%
链上数据索引         ████████████████░░░░   80%
多链 + Reorg 保护    ████████████████████  100%
Docker/生产化        ████████████░░░░░░░░   65%
Prometheus/Grafana   ████████████████████  100%
集成测试 (Anvil)     ████████████████████  100%  ← 2026-06-15 修好 @DynamicPropertySource 冲突，5/5 通过
CI/CD                ░░░░░░░░░░░░░░░░░░░░    0%
DeFi 链上数据深入    ██░░░░░░░░░░░░░░░░░░   10%
第二个项目           ░░░░░░░░░░░░░░░░░░░░    0%
简历包装             ░░░░░░░░░░░░░░░░░░░░    0%
```

距可投简历还差：Anvil 测试跑通 → 链上衍生品数据 → 第二个项目(Keeper Bot) → 简历

## 学习路线
C（生产化改造）→ A（多链支持）已完成。下一步：
- B（链上数据深入）：永续合约资金费率/爆仓/持仓量
- 第二个项目：链下 Keeper Bot 或价格预言机消费者

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
| application-test.yml | src/test/resources/application-test.yml |
| Dockerfile | D:\lzkcomp\web3\lzb-indexer\Dockerfile |
| docker-compose.yml | D:\lzkcomp\web3\lzb-indexer\docker-compose.yml |
| .env | D:\lzkcomp\web3\lzb-indexer\.env |
| prometheus.yml | D:\lzkcomp\web3\lzb-indexer\prometheus.yml |
| grafana-datasources.yml | D:\lzkcomp\web3\lzb-indexer\grafana-datasources.yml |
| Grafana 仪表盘 | D:\lzkcomp\web3\lzb-indexer\grafana-dashboards\lzb-indexer.json |
| 集成测试 | src/test/java/com/lzb/indexer/BlockScannerIntegrationTest.java |
| 测试 Solidity | src/test/solidity/ |
| PROMPT.md | D:\lzkcomp\web3\lzb-indexer\PROMPT.md |
| AGENTS.md | D:\lzkcomp\web3\lzb-indexer\AGENTS.md |
| PostgreSQL 裸机 | D:\lzkcomp\pgsql |
| Docker CLI | D:\lzkcomp\Docker\resources\bin\docker.exe |

## 本次对话核心成果（2026-06-11 ~ 06-14）
1. pom.xml 格式修复 + spring-boot-starter-aop 正确加入
2. AOP 切面 pointcut 修复（execution(public void ...)）
3. Docker 代理配置（Clash LAN + Docker Desktop Proxies）
4. Prometheus + Grafana 可视化（7 面板仪表盘）
5. lzb-indexer Docker 化（Alpine SSL 未彻底解决，暂本机跑）
6. 多链支持完整改造：
   - ChainProperties 配置类
   - BlockScanner 去 @Component，per-chain 实例
   - BlockScannerFactory 组装
   - ScannerScheduler 遍历多 Scanner
   - 所有实体/Repository/Controller 加 chainName
   - 指标按链前缀（scanner.sepolia.*）
7. 测试修复（适配多链 + chainName）
8. 本机验证通过：page-size=2000，Sepolia 扫块正常