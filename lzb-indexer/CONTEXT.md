# lzb-indexer 上下文交接（2026-06-29 ~ 2026-06-30）

## 项目状态
- 仓库：https://github.com/Neilbbkk/lzb-indexer（已推送，私钥已脱敏）
- 分支：master
- 数据库：PostgreSQL lzb_indexer_dev，27条 GMX V2 事件已验证

## 本轮完成
1. **EventDecoder bug 修复** — index_token + collateral_token 全部正确
   - boolItems 从 relOff[2] 改到 relOff[3]
   - 四个 parse*KV 方法加了 struct 偏移间接引用（arrOff→arrStart→count）
   - 加了 readInlineString 处理短 key 内联编码（"market"、"account"）
2. **全部中文注释修复** — EventDecoder/GmxPosition/GmxPositionService 乱码清零
3. **代码已推送** — 4 次 commit，master 分支
4. **路线图** — ROADMAP.md 已生成，目标 10月拿到 $3K-4.5K USDT 远程 Web3 后端 offer

## 路线图当前进度
- ✅ W1 周一~周二：index_token/collateral_token bug 修复 + 注释清理
- ⏳ W1 周三（6/30）：手对 Arbiscan 验证 5 条
- ⏳ W1 周四~周六：写测试 + mvn test 全绿
- ⏳ W1 周日：回顾
- ⏳ W2：全量扫描 Arbitrum（450M→最新）+ Sepolia

## 下一步（新对话里说）
用户需要继续路线图 W1 的剩余任务：
- 写 EventDecoder + GmxPositionService 测试
- 跑通 mvn test
- 启动 Arbitrum 全量扫描

## 环境
- Java 8 + Maven 3.8 + Spring Boot 2.7.18 + Web3j 4.9.8
- PostgreSQL 16（D:\pgsql16\bin\psql.exe）
- DB: lzb_indexer_dev, 用户 postgres
- 代理：Clash Verge 127.0.0.1:7897
- Git 代理已配好
- 项目路径：D:\lzkcomp\web3\lzb-indexer

## 注意事项
- 所有文件用 UTF-8 无 BOM（别再炸乱码）
- application.yml 私钥已改为 ${PRIVATE_KEY:} 环境变量
- deepseek-v4-pro 上下文窗口上限 258K（API 没返回 max_input_tokens，ccx 代理需配置）
