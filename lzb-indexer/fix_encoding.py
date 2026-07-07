# -*- coding: utf-8 -*-
"""Single-pass fix for EventDecoder.java — preserves UTF-8, no BOM."""
import os

PATH = r"D:\lzkcomp\web3\lzb-indexer\src\main\java\com\lzb\indexer\scanner\EventDecoder.java"

with open(PATH, "r", encoding="utf-8") as f:
    text = f.read()

# ── 1. Add getTransferEventHash() ──
old1 = '    private static final String TRANSFER_EVENT_HASH = EventEncoder.encode(TRANSFER_EVENT);'
new1 = old1 + '\n\n    /** 获取 Transfer 事件签名哈希（供 BlockScanner 设置 eth filter） */\n    public static String getTransferEventHash() {\n        return TRANSFER_EVENT_HASH;\n    }'
assert old1 in text, "FATAL: TRANSFER_EVENT_HASH not found"
text = text.replace(old1, new1, 1)

# ── 2. Add decodeLiquidatePosition() ──
old2 = '''    /** 清算检测：DecreasePosition 事件中 isLiquidation flag 为 true 时判定为清算 */
    public boolean isLiquidatePositionEvent(Log logEntry) {
        return false;
    }'''
new2 = old2 + '\n\n    /** 解码清算事件（暂未实现，返回 null） */\n    public GmxPositionHistory decodeLiquidatePosition(Log logEntry, String chainName) {\n        log.debug("LiquidatePosition event detected but not yet supported, tx={}", logEntry.getTransactionHash());\n        return null;\n    }'
assert old2 in text, "FATAL: isLiquidatePositionEvent not found"
text = text.replace(old2, new2, 1)

# ── 3. Fix parseUintKV ──
# Boundary
text = text.replace(
    '            if (hex.length() < itemStart + 128) break;\n            BigInteger firstSlot = bytesToBigInt(hex, itemStart);\n            String key;\n            if (firstSlot.compareTo(BigInteger.valueOf(10000)) < 0 && firstSlot.signum() > 0) {\n                int keyOff = itemStart + firstSlot.intValue() * 2;\n                key = readString(hex, keyOff);\n            } else {\n                key = readInlineString(hex, itemStart);\n            }\n            BigInteger val = bytesToBigInt(hex, itemStart + 64);',
    '            if (hex.length() < itemStart + 192) break;\n            BigInteger firstSlot = bytesToBigInt(hex, itemStart);\n            String key;\n            if (firstSlot.compareTo(BigInteger.valueOf(10000)) < 0 && firstSlot.signum() > 0) {\n                int keyOff = itemStart + firstSlot.intValue() * 2;\n                key = readString(hex, keyOff);\n            } else {\n                key = readInlineString(hex, itemStart);\n            }\n            // 链上数据：item 占 4 槽（key + 0x40 标记 + 实际值），实际值在第 3 槽\n            BigInteger marker = bytesToBigInt(hex, itemStart + 64);\n            BigInteger val;\n            if (marker.compareTo(BigInteger.valueOf(10000)) < 0 && marker.signum() > 0) {\n                val = bytesToBigInt(hex, itemStart + 128);\n            } else {\n                val = marker;\n            }',
    1  # only replace first occurrence (parseUintKV, not parseAddrKV)
)

# ── 4. Fix parseBoolKV ──
text = text.replace(
    '            if (hex.length() < itemStart + 128) break;\n            BigInteger firstSlot = bytesToBigInt(hex, itemStart);\n            String key;\n            if (firstSlot.compareTo(BigInteger.valueOf(10000)) < 0 && firstSlot.signum() > 0) {\n                int keyOff = itemStart + firstSlot.intValue() * 2;\n                key = readString(hex, keyOff);\n            } else {\n                key = readInlineString(hex, itemStart);\n            }\n            boolean val = !"0000000000000000000000000000000000000000000000000000000000000000"\n                    .equals(hex.substring(itemStart + 64, itemStart + 128));',
    '            if (hex.length() < itemStart + 192) break;\n            BigInteger firstSlot = bytesToBigInt(hex, itemStart);\n            String key;\n            if (firstSlot.compareTo(BigInteger.valueOf(10000)) < 0 && firstSlot.signum() > 0) {\n                int keyOff = itemStart + firstSlot.intValue() * 2;\n                key = readString(hex, keyOff);\n            } else {\n                key = readInlineString(hex, itemStart);\n            }\n            // 链上数据：item 占 4 槽，bool 值在第 3 槽\n            BigInteger boolMarker = bytesToBigInt(hex, itemStart + 64);\n            boolean val;\n            if (boolMarker.compareTo(BigInteger.valueOf(10000)) < 0 && boolMarker.signum() > 0) {\n                val = !"0000000000000000000000000000000000000000000000000000000000000000"\n                        .equals(hex.substring(itemStart + 128, itemStart + 192));\n            } else {\n                val = !"0000000000000000000000000000000000000000000000000000000000000000"\n                        .equals(hex.substring(itemStart + 64, itemStart + 128));\n            }',
    1
)

# ── 5. Fix parseKV32 ──
text = text.replace(
    '            if (hex.length() < itemStart + 128) break;\n            BigInteger firstSlot = bytesToBigInt(hex, itemStart);\n            String key;\n            if (firstSlot.compareTo(BigInteger.valueOf(10000)) < 0 && firstSlot.signum() > 0) {\n                int keyOff = itemStart + firstSlot.intValue() * 2;\n                key = readString(hex, keyOff);\n            } else {\n                key = readInlineString(hex, itemStart);\n            }\n            String val = "0x" + hex.substring(itemStart + 64, itemStart + 128);',
    '            if (hex.length() < itemStart + 192) break;\n            BigInteger firstSlot = bytesToBigInt(hex, itemStart);\n            String key;\n            if (firstSlot.compareTo(BigInteger.valueOf(10000)) < 0 && firstSlot.signum() > 0) {\n                int keyOff = itemStart + firstSlot.intValue() * 2;\n                key = readString(hex, keyOff);\n            } else {\n                key = readInlineString(hex, itemStart);\n            }\n            // 链上数据：item 占 4 槽，bytes32 值在第 3 槽\n            BigInteger b32Marker = bytesToBigInt(hex, itemStart + 64);\n            String val;\n            if (b32Marker.compareTo(BigInteger.valueOf(10000)) < 0 && b32Marker.signum() > 0) {\n                val = "0x" + hex.substring(itemStart + 128, itemStart + 192);\n            } else {\n                val = "0x" + hex.substring(itemStart + 64, itemStart + 128);\n            }',
    1
)

# ── Write back: UTF-8, NO BOM ──
with open(PATH, "w", encoding="utf-8", newline="") as f:
    f.write(text)

# ── Verify ──
with open(PATH, "r", encoding="utf-8") as f:
    verify = f.read()

checks = [
    ("getTransferEventHash", "public static String getTransferEventHash()" in verify),
    ("decodeLiquidatePosition", "public GmxPositionHistory decodeLiquidatePosition" in verify),
    ("parseUintKV 192", 'itemStart + 192) break;' in verify and 'BigInteger marker = bytesToBigInt' in verify),
    ("parseBoolKV 192", 'BigInteger boolMarker = bytesToBigInt' in verify),
    ("parseKV32 192", 'BigInteger b32Marker = bytesToBigInt' in verify),
    ("Chinese comment intact", '链上数据：item 占 4 槽' in verify),
    ("No BOM", not verify.startswith('\ufeff')),
]

all_ok = True
for name, ok in checks:
    flag = "OK" if ok else "FAIL"
    if not ok: all_ok = False
    print(f"  [{flag}] {name}")

print(f"\n{'ALL CHECKS PASSED' if all_ok else 'SOME CHECKS FAILED'}")

# Quick UTF-8 sanity: check that key Chinese phrases are readable
for phrase in ['事件解码器', '链上数据', '清算检测']:
    if phrase in verify:
        print(f"  UTF-8 OK: {phrase}")
    else:
        print(f"  UTF-8 MISSING: {phrase}")