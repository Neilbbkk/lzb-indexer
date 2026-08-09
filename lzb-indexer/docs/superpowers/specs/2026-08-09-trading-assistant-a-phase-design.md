# LZB 交易助手 A 阶段设计

日期：2026-08-09
状态：待实现

## 背景

- Java 版交易监控模块已存在，但 `TradingScheduler` 的 `@Scheduled(fixedDelayString = "")` 会导致 `trading.enabled=true` 时启动崩溃。
- 绩效复盘只把超时单标记为 `EXPIRED`，从不判定 `WIN/LOSS`，胜率统计失真。
- Binance `api-secret` 为空，账户监控/强平预警实际不可用。
- Whale Alert 请求未带 API key，LunarCrush 代码硬编码占位 key，数据源未接通。
- Python 遗留已隔离到 `D:\lzkcomp\web3\.python-removed-2026-08-08\`，临时 Java 文件已隔离到同目录下 `tmp-java-cleanup\`。

## 目标

1. `trading.enabled=true` 时应用能正常启动，调度器按配置间隔运行。
2. 绩效结算正确：`WIN/LOSS/EXPIRED`，含 3% 浮亏提前止损和 48 小时过期。
3. 止损提醒可用：接近止损提前推一次，触及止损再推一次，按 journal id 去重。
4. 数据源 key 可配置；缺失时启动 WARN + 对应功能降级，不阻止启动。
5. `trading.html` 独立页面展示绩效统计、最近决策、止损提醒、配置状态。

## 范围（A 阶段）

- 修复 `TradingScheduler` 调度注解。
- 新增 `PerformanceReviewService`。
- 扩展 `RiskManagementService`，新增止损提醒。
- 新增 `TradingConfigValidator` 启动校验。
- 扩展 `TradingConfig`：`WhaleAlert`、`LunarCrush`、`StopLoss` 配置块。
- 接线 `WhaleWatcherService`、`SocialMediaSentimentService`。
- 扩展 `TradingController`，新增 `trading.html`。
- 密钥迁移到环境变量 + 本地忽略文件。
- 补充 `.gitignore`，隔离临时文件。
- 新增 JUnit 测试。

## 非目标（B/C 阶段再做）

- 情绪周期阶段识别。
- 基本面分析服务。
- 独立高频监控调度器。
- 新增行情/费率/OI 前端展示。

## 架构与组件

### TradingScheduler

- `@Scheduled(fixedDelayString = "${trading.intervals.quick-ms:30000}")`。
- `@Scheduled(fixedDelayString = "${trading.intervals.decision-ms:900000}")`。
- 每 5 个 tick（约 2.5 分钟）调用 `checkStopLossReminders()`。
- 每 10 个 tick（约 5 分钟）调用 `reviewPending()`。
- 决策周期逻辑保持不变。

### PerformanceReviewService（新增）

- 依赖 `TradeJournalRepository` + `DataCollectionService`。
- 方法：`List<SettlementResult> reviewPending()`。
- 遍历 `PENDING` 日志，按规则结算并保存。
- 不直接推送，只返回结果供调度器记日志。

### RiskManagementService（扩展）

- 保留现有持仓风险/强平检查。
- 新增 `checkStopLossReminders()`。
- 按 journal id + 类型去重；journal 结算后释放去重。

### TradingConfigValidator（新增）

- 启动时检查 Binance/Whale/LunarCrush 配置。
- 缺失只打 WARN，不抛异常。

### TradingConfig（扩展）

```yaml
trading:
  stop-loss:
    enabled: true
    alert-distance-pct: 0.5
  whale-alert:
    api-key: ""
  lunarcrush:
    api-key: ""
```

### 数据源接线

- `WhaleWatcherService`：从配置读 key，拼 `api_key` 参数；key 为空时跳过，不发 401。
- `SocialMediaSentimentService`：从配置读 LunarCrush key；key 为空时走兜底方案。

### TradingController（扩展）

- `/api/trading/stats`：增加平均 RR、盈亏因子、按品种统计。
- `/api/trading/stop-loss-alerts`：返回最近止损提醒。
- `/api/trading/config-status`：返回各数据源配置状态。

### trading.html（新增）

- 独立页面，每 30 秒轮询：
  - `/api/trading/stats`
  - `/api/trading/decisions`
  - `/api/trading/stop-loss-alerts`
  - `/api/trading/config-status`
- 展示：绩效统计卡片、最近决策表、止损提醒列表、配置状态徽标。

## 行为规则

### 绩效结算

对每条 `PENDING`：

- LONG：
  - 现价 ≤ SL → `LOSS`，PnL = `(SL - entry) / entry * 100`
  - 现价 ≥ TP → `WIN`，PnL = `(TP - entry) / entry * 100`
  - 现价 ≤ entry × 0.97 → `LOSS`（提前止损），PnL = `(current - entry) / entry * 100`
  - 超过 48 小时 → `EXPIRED`，PnL = `(current - entry) / entry * 100`
- SHORT：
  - 现价 ≥ SL → `LOSS`，PnL = `(entry - SL) / entry * 100`
  - 现价 ≤ TP → `WIN`，PnL = `(entry - TP) / entry * 100`
  - 现价 ≥ entry × 1.03 → `LOSS`（提前止损），PnL = `(entry - current) / entry * 100`
  - 超过 48 小时 → `EXPIRED`，PnL = `(entry - current) / entry * 100`

结算时写入 `outcome`、`outcomePrice`、`outcomePnlPct`、`outcomeTime`。

统计口径：

- 平均 RR：已结算日志的 `riskReward` 平均值。
- 盈亏因子：`sum(正 PnL%) / |sum(负 PnL%)|`，无亏损时为 0。
- 按品种统计：total / wins / losses / winRate / avgRR。

### 止损提醒

- `trading.stop-loss.enabled` 默认 `true`。
- `trading.stop-loss.alert-distance-pct` 默认 `0.5`。
- LONG：现价距 SL 在阈值内且未触达 → 推“接近止损”；现价 ≤ SL → 推“已止损”。
- SHORT：现价距 SL 在阈值内且未触达 → 推“接近止损”；现价 ≥ SL → 推“已止损”。
- 按 journal id + 类型去重；journal 结算后释放去重。
- 推送走 `NotificationService`，级别 `warn`。

### 配置缺省与降级

- Whale Alert key 为空：启动 WARN，巨鲸监控禁用。
- LunarCrush key 为空：启动 WARN，社媒情绪走兜底。
- Binance key 或 secret 为空：启动 WARN，账户监控/强平预警降级为空；日志止损提醒不受影响。
- `application-trading.yml` 使用环境变量占位符。
- `application-trading-local.yml` 通过 `optional:` 引入，不提交。

## 数据流

1. 启动：`TradingConfigValidator` 校验配置。
2. 每 30 秒 `quickTick`：更新行情/K 线。
3. 每 2.5 分钟：止损提醒。
4. 每 5 分钟：绩效结算 + 持仓/强平/巨鲸检查。
5. 每 15 分钟 `decisionTick`：技术面 + 情绪 + 双人格决策 + 推送。
6. `trading.html` 每 30 秒轮询 4 个接口。

## 错误处理

- 外部 API 失败：返回空、记日志，主循环不崩。
- 结算/提醒拿不到当前价：跳过该 journal，下轮再试。
- 推送失败：记 WARN，不重试、不阻塞。
- key 缺失：启动 WARN + 功能降级。

## 测试计划

- `PerformanceReviewServiceTest`：WIN / LOSS / EXPIRED / 3% 浮亏 / 48h 过期。
- `RiskManagementServiceTest`：接近止损、触及止损、去重、结算后释放。
- `WhaleWatcherServiceTest`：有 key 拼 URL，无 key 跳过。
- `TradingConfigValidatorTest`：缺失 key 出 WARN，不缺不出。
- 回归：`mvn test` 全绿。

## 验收标准

1. `mvn test` 全绿。
2. `mvn package` 成功。
3. `trading.enabled=true` 启动不崩，日志出现调度器启动信息和配置校验 WARN（如有缺失）。
4. `trading.html` 可打开，接口返回正常。

## 配置与安全

- `application-trading.yml` 改为：
  - `${DEEPSEEK_API_KEY:}`
  - `${BARK_KEY:}`
  - `${BINANCE_API_KEY:}`
  - `${BINANCE_API_SECRET:}`
  - `${WHALE_ALERT_API_KEY:}`
  - `${LUNARCRUSH_API_KEY:}`
- 新增 `application-trading-local.yml` 放真实 key，加入 `.gitignore`。
- `application.yml` 的 `spring.config.import` 改为 `application-trading.yml,optional:application-trading-local.yml`。
- `.gitignore` 补：
  - `pgdata/`
  - `node_modules/`
  - `target/`
  - `tmp_*.java`
  - `application-trading-local.yml`
