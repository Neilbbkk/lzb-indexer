import json, sys, io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

def b2i(h, co):
    if len(h) < co + 64: return 0
    return int(h[co:co+64], 16)

def ris(h, co):
    if co < 0 or len(h) < co + 64: return ""
    b = bytes.fromhex(h[co:co+64])
    end = 0
    while end < len(b) and b[end] != 0: end += 1
    return b[:end].decode("utf-8", errors="replace")

def rs(h, co):
    if co < 0 or len(h) < co + 64: return ""
    l = b2i(h, co)
    if l <= 0 or l > 10000: return ""
    if len(h) < co + 64 + l * 2: return ""
    return bytes.fromhex(h[co+64:co+64+l*2]).decode("utf-8", errors="replace")

def parse_kv(h, sco, vp):
    r = {}
    if sco <= 0 or len(h) < sco + 64: return r
    ao = b2i(h, sco)
    ast = sco + ao * 2
    if ast < sco or len(h) < ast + 64: return r
    n = b2i(h, ast)
    if n <= 0 or n > 1000: return r
    cur = ast + 64
    for _ in range(min(n, 200)):
        if len(h) < cur + 64: break
        io = b2i(h, cur)
        ist = ast + io * 2
        if len(h) < ist + 128: break
        fs = b2i(h, ist)
        if fs < 10000 and fs > 0:
            k = rs(h, ist + fs * 2)
        else:
            k = ris(h, ist)
        v = vp(h, ist)
        if k: r[k] = v
        cur += 64
    return r

def paddr(h, ist):
    rv = b2i(h, ist + 64)
    if rv < 10000 and rv > 0:
        vo = (ist // 2 + rv) * 2
        if len(h) >= vo + 64:
            return "0x" + h[vo+24:vo+64]
    return "0x" + h[ist+64+24:ist+128]

def puint(h, ist): return b2i(h, ist + 64)
def pbool(h, ist): return h[ist+64:ist+128] != "0" * 64

def decode_gmx_log(data_hex):
    h = data_hex[2:] if data_hex.startswith("0x") else data_hex
    ed_oc = b2i(h, 128) * 2
    if ed_oc < 64 or len(h) < ed_oc + 448: return None
    ro = [b2i(h, ed_oc + i*64) for i in range(7)]
    addrs = parse_kv(h, ed_oc + ro[0] * 2, paddr)
    uints = parse_kv(h, ed_oc + ro[1] * 2, puint)
    bools = parse_kv(h, ed_oc + ro[3] * 2, pbool)
    
    market = addrs.get("market", addrs.get("indexToken", addrs.get("longToken", addrs.get("shortToken", ""))))
    return {
        "account": addrs.get("account", "").lower(),
        "collateralToken": addrs.get("collateralToken", "").lower(),
        "indexToken": market.lower(),
        "sizeInUsd": uints.get("sizeInUsd", 0),
        "collateralAmount": uints.get("collateralAmount", uints.get("initialCollateralDeltaAmount", 0)),
        "price": uints.get("executionPrice", uints.get("price", 0)),
        "fee": uints.get("positionFeeAmount", 0),
        "isLong": bools.get("isLong", False),
    }

def hx(n):
    return "0x{:x}".format(n)

# Load receipts
with open("D:/lzkcomp/web3/lzb-indexer/verify_receipts.json", encoding="utf-8") as f:
    receipts = json.load(f)

# Expected: (tx_hash, log_index_decimal, event_type)
expected = [
    ("0xd4c6d03d5cc2c20edf043a4d4c0b8607405989287e40571ad2e04d6afcccffa0", 78, "DECREASE"),
    ("0x17b8f52be011378be9700698df65794dda61523ee8072604941c50c4866bdc45", 50, "INCREASE"),
    ("0x05b13ec0594c81b5ce9974c3c95f6c28af6fbb02cfa0a4788e5634df02b5b669", 30, "DECREASE"),
    ("0x5419b3e40f219c728d5fe4dd33b14b55bd25c366b6e3cb5af4c5b5b38f4d92e2", 27, "INCREASE"),
    ("0x075b4e652dcc42aaae3b219922483d289d0c2cac77c84f4d36dc990049f7ea83", 24, "INCREASE"),
]

print("=" * 72)
print("  lzb-indexer — Arbiscan 验证报告 (2026-06-30 W1 Wed)")
print("=" * 72)

pass_count = 0
fail_count = 0

for tx_hash, log_idx, evt_type in expected:
    print("\n" + "-" * 72)
    print("  TX: {}".format(tx_hash))
    print("  Block: {} | LogIndex: {} | Expected: {}".format(
        int(receipts[tx_hash]["blockNumber"], 16), log_idx, evt_type))
    
    li_hex = hx(log_idx)
    target = None
    for log in receipts[tx_hash]["logs"]:
        if log["logIndex"] == li_hex:
            target = log
            break
    
    if not target:
        print("  [FAIL] Log not found at index {}".format(li_hex))
        fail_count += 1
        continue
    
    # Verify topic
    topics = target["topics"]
    chk0 = topics[0] == "0x137a44067c8961cd7e1d876f4754a5a3a75989b4552f1843fc69c3b372def160"
    if evt_type == "DECREASE":
        chk1 = topics[1] == "0x07d51b51b408d7c62dcc47cc558da5ce6a6e0fd129a427ebce150f52b0e5171a"
    else:
        chk1 = topics[1] == "0xf94196ccb31f81a3e67df18f2a62cbfb50009c80a7d3c728a3f542e3abc5cb63"
    
    print("  Topics: emitEventLog={} eventType={}".format(
        "OK" if chk0 else "MISMATCH", "OK" if chk1 else "MISMATCH"))
    
    d = decode_gmx_log(target["data"])
    if not d:
        print("  [FAIL] Decode failed")
        fail_count += 1
        continue
    
    # DB values (from query earlier)
    db = {
        "0xd4c6d03d5cc2c20edf043a4d4c0b8607405989287e40571ad2e04d6afcccffa0": {
            "account": "0x47c031236e19d024b42f8ae6780e44a573170703",
            "collateralToken": "0x1064b9d788314d6bafd5a318c6f15bd3366b67a6",
            "indexToken": "0xaf88d065e77c8cc2239327c5edb3a432268e5831",
            "collateralDelta": -64, "sizeDelta": -64, "price": 64, "fee": 0, "isLong": False
        },
        "0x17b8f52be011378be9700698df65794dda61523ee8072604941c50c4866bdc45": {
            "account": "0x587759c237acca739bce3911647bacf56c876e60",
            "collateralToken": "0xb6f667ae1f9ef040485378c79c8b519b6538a3de",
            "indexToken": "0xaf88d065e77c8cc2239327c5edb3a432268e5831",
            "collateralDelta": 64, "sizeDelta": 64, "price": 64, "fee": 0, "isLong": False
        },
        "0x05b13ec0594c81b5ce9974c3c95f6c28af6fbb02cfa0a4788e5634df02b5b669": {
            "account": "0xeb28ad1a2e497f4acc5d9b87e7b496623c93061e",
            "collateralToken": "0x6289d7df83841d5856c9e0dc1755711ae93d85d0",
            "indexToken": "0xaf88d065e77c8cc2239327c5edb3a432268e5831",
            "collateralDelta": -64, "sizeDelta": -64, "price": 64, "fee": 0, "isLong": False
        },
        "0x5419b3e40f219c728d5fe4dd33b14b55bd25c366b6e3cb5af4c5b5b38f4d92e2": {
            "account": "0x47c031236e19d024b42f8ae6780e44a573170703",
            "collateralToken": "0x3f5bbeb0e92cf7d688b75364345ca3b0dfcac736",
            "indexToken": "0xaf88d065e77c8cc2239327c5edb3a432268e5831",
            "collateralDelta": 64, "sizeDelta": 64, "price": 64, "fee": 0, "isLong": False
        },
        "0x075b4e652dcc42aaae3b219922483d289d0c2cac77c84f4d36dc990049f7ea83": {
            "account": "0x06eeb86f26d5d91345b97d2c5b0a23f2c9cc2b89",
            "collateralToken": "0xf98d2a148485e1a8358520c0b39b916f57623918",
            "indexToken": "0xaf88d065e77c8cc2239327c5edb3a432268e5831",
            "collateralDelta": 64, "sizeDelta": 64, "price": 64, "fee": 0, "isLong": False
        },
    }
    
    dbv = db.get(tx_hash, {})
    
    def check(field, chain_val, db_val, fmt=str):
        ok = fmt(chain_val).lower() == fmt(db_val).lower()
        mark = " OK" if ok else " MISMATCH"
        print("  {:<20s} chain={:<44s} db={:<44s}{}".format(
            field, str(chain_val)[:42], str(db_val)[:42], mark))
        return ok
    
    print()
    print("  --- Field Comparison ---")
    all_ok = True
    all_ok &= check("account", d["account"], dbv.get("account",""))
    all_ok &= check("collateralToken", d["collateralToken"], dbv.get("collateralToken",""))
    all_ok &= check("indexToken", d["indexToken"], dbv.get("indexToken",""))
    
    # For amounts: INCREASE = positive, DECREASE = negated
    is_increase = (evt_type == "INCREASE")
    exp_size = abs(d["sizeInUsd"]) if is_increase else -abs(d["sizeInUsd"])
    exp_coll = abs(d["collateralAmount"]) if is_increase else -abs(d["collateralAmount"])
    all_ok &= check("sizeDelta", exp_size, dbv.get("sizeDelta",0), str)
    all_ok &= check("collateralDelta", exp_coll, dbv.get("collateralDelta",0), str)
    all_ok &= check("price", d["price"], dbv.get("price",0), str)
    all_ok &= check("fee", d["fee"], dbv.get("fee",0), str)
    all_ok &= check("isLong", d["isLong"], dbv.get("isLong",None), str)
    
    if all_ok:
        print("  >>> VERDICT: ALL MATCH")
        pass_count += 1
    else:
        print("  >>> VERDICT: MISMATCH DETECTED")
        fail_count += 1

print("\n" + "=" * 72)
print("  Summary: {}/{} passed, {} failed".format(pass_count, pass_count+fail_count, fail_count))
print("=" * 72)
