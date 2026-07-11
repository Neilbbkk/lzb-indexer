# lzb-indexer 上下文交接（截至 2026-07-11）

## 项目状态
- 仓库：https://github.com/Neilbbkk/lzb-indexer
- 分支：master
- 数据库：PostgreSQL lzb_indexer_dev

## 路线图当前进度
- ✅ W1 周一~周二：index_token/collateral_token bug 修复 + 注释清理
- ✅ W1 周三（7/2）：手对 Arbiscan 验证 → uint 解码 bug 修复
- ✅ W1 周四~六（7/3-7/5）：写测试
  - EventDecoderTest（9 tests）：mock Log + 真实链上 hex data
  - GmxPositionServiceTest（7 tests）：H2 内存库
  - mvn test: 16/16 全绿
- ✅ W1 周日/周一（7/5-7/8）：isLong bool key 乱码修复
  - 新增 readItemKey()，偏移<128 从 slot3+4 读 key
  - Python 验证：key="isLong", value=False
- ⏳ W2+：全量扫描 Arbitrum + Sepolia

## 编译与测试
- mvn clean compile → BUILD SUCCESS
- mvn test -Dtest=EventDecoderTest,GmxPositionServiceTest → 16/16
- 集成测试 GmxBlockScannerIntegrationTest → 待修复（ABI 签名不匹配）

## 核心修复（EventDecoder.java）
1. uint/bool/bytes32 值解码：读 slot2 而非 slot1
2. isLong bool key：readItemKey() 小偏移逻辑
3. parse*KV key 读取统一用 readItemKey()

## 新增文件
- src/test/.../scanner/EventDecoderTest.java
- src/test/.../service/GmxPositionServiceTest.java
- W1_LEARNING_NOTES.md
- TestGmxVault.sol（重写为 EventEmitter 格式）

## 已知问题
- Git push 卡死：没有 GitHub 凭证
- Cursor Java Test Runner 不显示绿色三角（命令行跑正常）
- 集成测试失败：Solidity topic hash 与主网不匹配
- Clash Verge 代理不稳定

## 下一步
1. 配 GitHub 凭证 → git push
2. W2：全量扫描（需配 RPC）
3. 修复或搁置集成测试

## 环境
- Java 8 / Maven 3.8 / Spring Boot 2.7.18 / Web3j 4.9.8
- PostgreSQL 16 / Foundry+Anvil
- DB: lzb_indexer_dev, 用户 postgres
- 项目：D:/lzkcomp/web3/lzb-indexer