# Week 1 学习笔记：逆向 GMX V2 EventEmitter ABI 编码

> 2026.06.29 - 2026.07.05 | lzb-indexer

---

## 一、什么是 EventEmitter？

GMX V2 不像普通合约那样直接 emit 具名事件。所有事件通过一个叫 **EventEmitter** 的代理合约发出。

```
普通合约：  emit PositionIncrease(account, sizeInUsd, ...)
GMX V2：   EventEmitter.emitEventLog(eventName, EventLogData)
```

事件签名永远是这两个：
```
emitEventLog  → keccak("emitEventLog(address,string,EventLogData)")  → 0x137a4406...
emitEventLog2 → keccak("emitEventLog2(address,EventLogData)")        → 0x468a25a7...
```

**真实事件类型（PositionIncrease / PositionDecrease）藏在 topic[1] 里**，是 `keccak256("PositionIncrease")` 的哈希。

这是 GMX 的 Gas 优化手段——用一个通用事件签名覆盖所有业务事件，减少合约代码中 event 声明的数量。

---

## 二、emitEventLog data 布局

以一条真实的 PositionIncrease 事件为例（Arbitrum 区块 450,275,053，tx `0x17b8...`）：

```
slot  | 字节偏移 | 内容
────────────────────────────────────────────────────────
  0   |  0-31   | msgSender 地址           → 直接读
  1   |  32-63  | eventName 偏移指针 0x60  → "去字节96找"
  2   |  64-95  | EventLogData 偏移 0xa0  → "去字节160找"
────────────────────── 跳转到字节96 ──────────────────
  3   |  96-127 | eventName 长度 = 0x10
  4   | 128-159 | "PositionIncrease" 16字节 + 零填充
────────────────────── 跳转到字节160 ──────────────────
  5+  |  160+   | EventLogData 结构体入口
```

**关键公式：**

```java
edOffChar = bytesToBigInt(hex, 128).intValue() * 2;
// = 0xa0 * 2 = 160 * 2 = 320（字符偏移）
```

- 为什么读 hex 偏移 128？因为槽2（字节64-95）存的就是 EventLogData 的偏移指针
- 为什么乘 2？hex 编码每字节 = 2 字符

---

## 三、EventLogData 内部结构

EventLogData 是一个包含 7 个动态数组的结构体：

```solidity
struct EventLogData {
    AddressItem[]   addressItems;    // 地址键值对
    UintItem[]      uintItems;       // 大整数键值对
    IntItem[]       intItems;        // 有符号整数键值对
    BoolItem[]      boolItems;       // 布尔键值对
    Bytes32Item[]   bytes32Items;    // bytes32 键值对
    BytesItem[]     bytesItems;      // 动态 bytes 键值对
    StringItem[]    stringItems;     // 字符串键值对
}
```

因**所有成员都是动态数组**，EventLogData 整体被当作动态类型。头部是 7 个指针槽：

```
槽0: addressItems 指针   → relOff[0] = 224
槽1: uintItems 指针      → relOff[1] = 832
槽2: intItems 指针       → relOff[2] = 3584
槽3: boolItems 指针      → relOff[3] = 4192
槽4: bytes32Items 指针   → relOff[4] = 4480
槽5: bytesItems 指针     → relOff[5] = 4928
槽6: stringItems 指针    → relOff[6] = 5056
```

---

## 四、Item 的四槽编码

每个 item（不管是哪种类型）都占 **4 个 slot = 256 hex 字符**：

```
slot0: key 编码
slot1: 0x40 标记
slot2: 实际值
slot3: 预留
```

### 以 uint item "sizeInUsd" 为例：

```
slot0: 73697a65496e5573640000000000000000000000000000000000000000000000  → "sizeInUsd"
slot1: 0000000000000000000000000000000000000000000000000000000000000040  → 0x40 标记
slot2: 00000000000000000000000000000000000000000000000000000000999f4e0a  → 2577354250
slot3: 000000000000000000000000000000000000000000000000000000000000000c  → 补齐
```

### Key 的两种编码方式：

| firstSlot 值 | 含义 | key 在哪 |
|-------------|------|---------|
| 内联 ASCII | short key，直接读 slot0 | slot0 本身 |
| < 10000 的正整数 | 偏移指针 | itemStart + offset |

例如：
- `"sizeInUsd"` → 直接塞在 slot0 里（12字节 + 零填充）
- 有些长 key → slot0 存偏移量，跳到尾部去读

---

## 五、本周修的最大的 Bug：uint 值全读成 64

**现象：** 所有 uint 值（sizeInUsd, collateralAmount, executionPrice...）都显示为 64。

**根因：** 代码把 slot1 的 `0x40` 标记当成了值来读。

```
修复前：读 slot1 (0x40 = 64) → 所有值都等于 64
修复后：读 slot2 (实际值)    → 数据正确
```

**修复逻辑：**
```java
// parseUintKV 中
BigInteger marker = bytesToBigInt(hex, itemStart + 64);
if (marker < 10000 && marker > 0) {
    // slot1 是指针 → slot2 才是值
    value = bytesToBigInt(hex, itemStart + 128);
} else {
    // slot1 本身就是值（兼容旧版数据）
    value = marker;
}
```

parseBoolKV 和 parseKV32 也用了同样的修复。

---

## 六、定长 vs 动态：决定性因素

**不是看槽是不是 32 字节，而是看值能不能塞进一个槽。**

| 类型 | 一个槽装得下？ | 编码 |
|------|:---:|------|
| address, uint256, bool, bytes32 | ✅ | 直接存值 |
| string, bytes, 数组, 结构体含动态成员 | ❌ | 头部存指针 → 尾部存实际数据 |

EventLogData 的 7 个成员全是动态数组，所以整体是动态类型，在 emitEventLog 的 data 头部只能存一个指针。

---

## 七、解码完整流程

```
eth_getLogs → Log { topics: [...], data: "0x..." }
  │
  ├─ 1. 识别事件类型
  │     topic[0] == emitEventLog hash → 是 GMX V2 事件
  │     topic[1] == keccak("PositionIncrease") → 是加仓
  │
  ├─ 2. 去除 "0x" 前缀
  │
  ├─ 3. 读 slot2 的 EventLogData 指针 → edOffChar
  │
  ├─ 4. 从 edOffChar 读 7 个 relOff
  │
  ├─ 5. 解析 addrItems  → account, collateralToken, market
  │    解析 uintItems  → sizeInUsd, collateralAmount, executionPrice, fee
  │    解析 boolItems  → isLong
  │    解析 bytes32Items → orderKey (position_key)
  │
  └─ 6. 组装 GmxPositionHistory 对象
        INCREASE: delta 为正
        DECREASE: delta 取负
```

---

## 八、测试验证

### EventDecoderTest（9 个）
- 事件类型识别 ×5：isGmxV2Event / isIncrease / isDecrease / 非GMX / null
- 解码正确性 ×1：用真实链上 hex data，断言 sizeInUsd=2,577,354,250
- 边界情况 ×3：空 data / 过短 / 非 Increase 事件

### GmxPositionServiceTest（7 个）
- INCREASE → OPEN
- INCREASE + 全平 DECREASE → CLOSED
- 部分平仓 → 仍 OPEN
- LIQUIDATE → LIQUIDATED + 清零
- 多次加仓累加
- 查询方法 / countByChain

**mvn test：16 tests, 0 failures, 0 errors ✅**

---

## 九、待解决问题

| 问题 | 状态 | 说明 |
|------|:--:|------|
| isLong 解码 | ❌ | bool item 的 key 槽是 `0x20` 而非内联字符串，解析出来是乱码 |
| RPC 超时 | ⚠️ | 公开 RPC 扫描大范围超时，需专用节点或重试逻辑 |
| 合约地址 | ✅ | 已改为 EventEmitter `0xC8ee...` |
| GmxPositionService 注释乱码 | ⚠️ | 不影响功能，后续批量修复 |

---

## 十、关键资源

| 文件 | 用途 |
|------|------|
| `eth_getLogs_data.txt` | 一条 PositionIncrease 的完整 data hex（10690 字符） |
| `eth_getLogs_full.json` | 同上事件的完整 JSON |
| `show_full_parse.py` | 分阶段打印解析流程 |
| `verify_fix.py` | 修复前后对比验证 |

---

## 十一、核心收获

1. **ABI 编码是 Web3 后端的看家本领**——不懂 ABI 编码就没法写 indexer
2. **不要假设数据格式**——每个槽的值都可能是指针，先判断再读
3. **用 Python 验证比肉眼盯 hex 快 100 倍**——先写脚本确认，再改 Java 代码
4. **测试用真实链上数据**——mock 数据发现不了 slot 偏移 bug