# lzb-indexer → 远程 Web3 后端 offer · 激进版路线图

**目标：2026年10月，$3,000-4,500 USDT/月，远程 Web3 后端**

---

## 每日时间分配

| 时段 | 内容 |
|------|------|
| **通勤/碎片** | 英语听力/播客 30min + 技术博客/推文 20min |
| **晚上（核心）** | 项目硬活 2-3h（雷打不动） |
| **周末** | 项目核心 5-6h + 面试准备 2h |
| **摸鱼时间** | 刷 Web3 招聘 JD、行业新闻 |

---

## 零碎时间资源池（长期循环用）

### 英语听力（通勤路上）

| 资源 | 说明 |
|------|------|
| **Bankless Podcast** | Web3 叙事 + 协议深度，语速适中，术语密度高 |
| **Lex Fridman Podcast** | 技术和思想深度，练长听力 |
| **The Defiant** | DeFi 新闻，5-10分钟/期，信息密度高 |
| **a16z Podcast** | VC 视角看 Web3，了解市场叙事 |
| **YouTube: Finematics** | DeFi 协议可视化讲解，10分钟一个协议 |

### 技术博客（午休/排队）

| 博客 | 为什么看 |
|------|----------|
| **[ethereum.org 开发者文档](https://ethereum.org/en/developers/docs/)** | 圣经，EVM/账户/交易/共识，全看一遍 |
| **[OpenZeppelin Blog](https://blog.openzeppelin.com/)** | 合约安全 + 升级模式，面 DeFi 岗必问 |
| **[Paradigm Research](https://www.paradigm.xyz/portfolio)** | Uniswap V3 数学原理、MEV，面试装逼利器 |
| **[Ethereum Engineering Group](https://ethresear.ch/)** | PBS / MEV / Danksharding，了解前沿 |
| **[Nansen Research](https://www.nansen.ai/research)** | 链上数据分析方法论，跟你的 indexer 直接相关 |
| **[Messari Reports](https://messari.io/research)** | 协议分析报告，面特定协议岗必看 |
| **[Baeldung Java](https://www.baeldung.com/)** | Java 并发/JPA/Spring，面试基础题查缺补漏 |
| **[Martin Kleppmann 博客](https://martin.kleppmann.com/)** | DDIA 作者，分布式系统，系统设计面必读 |

### 英语输出训练（必须练说！）

| 动作 | 频率 |
|------|------|
| 每天用英语口述你今天做了什么事（30秒，录音自己听） | 每天 |
| 用英语写 commit message + PR description + README | 日常 |
| 准备 5 个 STAR 故事英文版，录音反复练到脱稿 | 8月开始 |

---

## 阶段一：项目硬活（6/29 – 7/31，5 周）

### W1（6/29 – 7/5）

| 日 | 核心任务 | 碎片阅读 |
|----|---------|---------|
| 一 | dump 3 条 event data hex → Python 脚本追踪 EventLogData 7元组偏移 | ethereum.org: ABI encoding 章节 |
| 二 | 定位 `addressItems` 精确 relOff 索引，修复 `index_token` | ethereum.org: Events/Logs 章节 |
| 三 | 修复 `collateral_token` 地址错位（同根因），27条验证全对 | OpenZeppelin: Proxy patterns |
| 四 | 重扫 450M-452M，手对 Arbiscan 验证 5 条 | Baeldung: Hibernate best practices |
| 五 | 写 `EventDecoder` 单元测试（mock log data，assert 解码结果） | Paradigm: AMM 数学 |
| 六 | 写 `GmxPositionService` 集成测试（INCREASE→OPEN, DECREASE→CLOSED） | — |
| 日 | 回顾 + `mvn test` 全绿 | Bankless 某一期 |

### W2（7/6 – 7/12）

| 日 | 核心任务 | 碎片阅读 |
|----|---------|---------|
| 一 | 配置 Sepolia sync checkpoint，启动 Sepolia 全量扫描 | Nansen: 链上分析基础 |
| 二 | 配置 Arbitrum 全量扫描（450M→最新），监控 RPC 限流 | Baeldung: Spring Scheduling |
| 三 | 扫描中，优化 BlockScanner：批量 save（每次 commit 500 条而非逐条） | JPA batch insert 最佳实践 |
| 四 | 检查扫描进度，处理 RPC 超时重试逻辑 | OkHttp retry 机制 |
| 五 | 加 `SyncError` 记录：RPC 超时、解码异常、DB 写入失败 | Messari: GMX 协议分析 |
| 六 | 扫描完成，验证 `token_transfers` / `gmx_position_history` 数据量合理 | — |
| 日 | 写 SQL 数据校验脚本（Sepolia Transfer 数 vs Etherscan 对比） | Bankless 某一期 |

### W3（7/13 – 7/19）

| 日 | 核心任务 | 碎片阅读 |
|----|---------|---------|
| 一 | 加 Uniswap V2 Swap 事件解码（`src`/`amount0In`/`amount1Out`...） | Uniswap V2 白皮书（30分钟速读） |
| 二 | 注册 UniswapV2Router `0x7a250d5630B4...`，加链配置 | ethereum.org: DApps 架构 |
| 三 | 跑通 Uniswap 扫描，验证 Swap 数据入库 | Martin Kleppmann: Event Sourcing |
| 四 | Refactor `EventDecoder`：提取公共抽象（`EventHandler` 接口） | Baeldung: Strategy Pattern |
| 五 | 写 `TokenService` 测试（balanceOf mock），`BlockScanner` 测试 | — |
| 六 | 全量测试覆盖率 `mvn jacoco:report`，目标 > 60% | — |
| 日 | 回顾 + 代码 review 自己 | Bankless 某一期 |

### W4（7/20 – 7/26）

| 日 | 核心任务 | 碎片阅读 |
|----|---------|---------|
| 一 | Docker Compose：postgres + lzb-indexer + grafana + prometheus | Docker 多阶段构建最佳实践 |
| 二 | Grafana 仪表盘：扫块速度(rate) / 事件累计(counter) / 错误率 | PromQL 入门 |
| 三 | 加 `/actuator/health` + `/actuator/info`，验证 Prometheus 指标 | Spring Actuator 安全配置 |
| 四 | 解决 Docker 内 Alpine SSL 问题（之前 PROMPT.md 提到） | OkHttp SSL 配置 |
| 五 | `docker-compose up` 一键启动全栈，截图 Grafana 面板 | — |
| 六 | 写英文 README：Mermaid 架构图 + Quick Start + API 文档 | 看 5 个优秀 GitHub 项目的 README |
| 日 | 录 3 分钟英文 Demo 视频（终端操作 + Grafana 演示） | — |

### W5（7/27 – 7/31）

| 日 | 核心任务 | 碎片阅读 |
|----|---------|---------|
| 一 | 写技术博文 "Reverse-Engineering GMX V2 EventEmitter ABI" 英文版 | — |
| 二 | 博文第二部分 + 代码片段 + 架构图 | — |
| 三 | 博文发布 Medium + Dev.to，同步到 Twitter/LinkedIn | — |
| 四 | GitHub repo 设 public，加 topics，检查敏感信息（私钥已脱敏？） | — |
| 五 | 回顾阶段一成果，整理面试 STAR 故事 | — |

---

## 阶段二：简历 + 投递（8/1 – 8/31，4 周）

### 每日额外任务

| 动作 | 频率 | 说明 |
|------|------|------|
| 看 5 个 JD | 每天 | web3.career 搜 "backend java remote"，了解雇主在找什么 |
| 投 2-3 个岗位 | 周一到周五 | 不要海投，针对 JD 改 cover letter |
| 英语 STAR 故事练 1 个 | 每天 | 录音→听→改→再录，直到能不看稿讲顺 |
| 区块链八股 1 个主题 | 每天 | 轮转：EVM / Gas / ABI / Merkle Trie / DeFi / MEV |

### W6-W7（8/1 – 8/14）：简历 + 面试八股

| 类别 | 死磕清单 |
|------|----------|
| **EVM 深度** | Storage Slot 计算、delegatecall vs call、CREATE vs CREATE2、合约部署字节码结构 |
| **交易机制** | Nonce 管理、Gas 估算（estimateGas vs gasLimit）、EIP-1559 优先费、交易池 pending tx |
| **ABI 编码** | static vs dynamic types、tuple 编码、你的项目里 EventLogData 7元组逆向过程 |
| **DeFi** | AMM 恒定乘积公式、无常损失、滑点、闪电贷原理、GMX V2 资金费率 |
| **系统设计** | 设计区块链浏览器 / 设计 DEX 聚合器 / 设计链上数据 Indexer |

### W8-W9（8/15 – 8/31）：大量投递

| 渠道 | 策略 |
|------|------|
| **web3.career** | 筛选 remote + backend/blockchain + junior/mid |
| **cryptojobslist.com** | 按日期排序，投最新的 |
| **remoteok.com** | blockchain 标签，每天刷 |
| **LinkedIn** | 搜 "web3 java" "blockchain backend remote"，主动联系猎头 |
| **电鸭社区** | 远程工作节点，偶尔有 Web3 岗 |
| **Discord** | GMX / Uniswap / Arbitrum / Optimism 官方 Discord，看 #jobs 频道 |
| **Twitter/X** | 关注 @web3career @cryptojobslist @remote_ok |

**目标：8月31日前投出 50 份以上简历，拿到 3-5 个面试邀请。**

---

## 阶段三：面试冲刺（9/1 – 10/31，8 周）

### 面试节奏

| 周 | 动作 |
|----|------|
| W10-W11 | 面试第一波（8月投的），每场面完立刻复盘录音 |
| W12-W13 | 改简历（针对面试反馈），继续投 20-30 个 |
| W14 | ETHOnline 黑客松（可选——有时间就周末搞，没时间算了） |
| W15-W16 | 面试密集期，预计 5-8 场 |
| W17 | 终面 + offer + 谈薪 |

### 面试逐轮准备

| 轮次 | 内容 | 你主打什么 |
|------|------|-----------|
| **HR 面** | 自我介绍 + 为什么转 Web3 + 薪资期望 | lzb-indexer STAR 故事 + $4500 起要 |
| **技术面 1** | Java 基础（并发/JPA/Spring） | 3.5 年 Java 是强项，别掉链子 |
| **技术面 2** | 区块链深度（EVM/DeFi/你的项目） | 项目细节全掌握：EventLogData 逆向、多链架构、Reorg 处理 |
| **系统设计** | 设计一个 Indexer / 浏览器 / DEX 后端 | DDIA 前 5 章 + 你的项目架构 |
| **Take-home** | 可能让你写个小型合约交互脚本 | Python/JS 调链上合约读数据 |
| **终面/CTO** | 技术视野 + 文化契合 | 你对 Web3 的理解，为什么看好 |

### 薪资谈判剧本

```
Them: "What's your expected salary?"
You: "Based on my 3.5 years of backend experience and this production indexer
      I built independently, I'm looking at $4,000-4,500 per month."
Them: "We were thinking more like $3,000."
You: "I understand. For the first 3 months I'm open to $3,500 with a review
      at month 3. If I deliver, adjusted to $4,500. Does that work?"
```

底线 $3,000，不要破。低于 $3,000 的工作不会让你碰主网核心，没成长价值。

---

## 总检查清单

```
□ 7/5   index_token + collateral_token 修完，27条 100% 正确
□ 7/12  Arbitrum + Sepolia 全量扫描完成
□ 7/19  mvn test 全绿，Uniswap V2 解析正常
□ 7/31  Docker一键启动 + Grafana + README + 博文 + repo public
□ 8/15  投出 25 份以上，收到 ≥1 个面试
□ 8/31  投出 50 份以上，收到 ≥3 个面试
□ 9/15  完成 ≥5 场面试，复盘报告
□ 9/30  进 ≥2 个终面
□ 10/15 ≥1 个 offer
□ 10/31 签 offer
```
