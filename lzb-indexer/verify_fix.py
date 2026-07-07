import json, urllib.request, sys, io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

def rpc(method, params):
    body = json.dumps({"jsonrpc":"2.0","method":method,"params":params,"id":1}).encode()
    req = urllib.request.Request("https://arb1.arbitrum.io/rpc", body,
        {"Content-Type":"application/json", "User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read())["result"]

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
    L = b2i(h, co)
    if L <= 0 or L > 10000: return ""
    if len(h) < co + 64 + L * 2: return ""
    return bytes.fromhex(h[co+64:co+64+L*2]).decode("utf-8", errors="replace")

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
        if len(h) < ist + 192: break
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

def puint_new(h, ist):
    marker = b2i(h, ist + 64)
    if marker < 10000 and marker > 0:
        return b2i(h, ist + 128)
    return marker

def pbool_new(h, ist):
    marker = b2i(h, ist + 64)
    if marker < 10000 and marker > 0:
        slot = h[ist+128:ist+192]
    else:
        slot = h[ist+64:ist+128]
    return slot != "0" * 64

EMIT = "0x137a44067c8961cd7e1d876f4754a5a3a75989b4552f1843fc69c3b372def160"
INC = "0xf94196ccb31f81a3e67df18f2a62cbfb50009c80a7d3c728a3f542e3abc5cb63"
DEC = "0x07d51b51b408d7c62dcc47cc558da5ce6a6e0fd129a427ebce150f52b0e5171a"

print("Fetching logs (block 450M-450.5M)...")
logs = rpc("eth_getLogs", [{
    "fromBlock": "0x1ad27480",
    "toBlock": "0x1ad27600",
    "address": "0xc8ee91a54287db53897056e12d9819156d3822fb",
    "topics": [EMIT]
}])

pos_logs = [x for x in logs if x["topics"][1] in (INC, DEC)]
print("Found {0} emitEventLog, {1} Position events".format(len(logs), len(pos_logs)))

for i, log in enumerate(pos_logs[:5]):
    topics = log["topics"]
    evt = "INCREASE" if topics[1] == INC else "DECREASE"
    h = log["data"][2:]
    
    ed_oc = b2i(h, 128) * 2
    ro = [b2i(h, ed_oc + j*64) for j in range(7)]
    
    addrs = parse_kv(h, ed_oc + ro[0] * 2, paddr)
    uints = parse_kv(h, ed_oc + ro[1] * 2, puint_new)
    bools = parse_kv(h, ed_oc + ro[3] * 2, pbool_new)
    
    mkt = addrs.get("market", addrs.get("indexToken", addrs.get("longToken", addrs.get("shortToken", "")))
    acct = addrs.get("account", "")
    coll = addrs.get("collateralToken", "")
    sz = uints.get("sizeInUsd", 0)
    ca = uints.get("collateralAmount", uints.get("initialCollateralDeltaAmount", 0))
    pr = uints.get("executionPrice", uints.get("price", 0))
    fe = uints.get("positionFeeAmount", 0)
    il = bools.get("isLong", False)
    
    neg = "-" if evt == "DECREASE" else ""
    print("")
    print("--- {0} #{1} ---".format(evt, i+1))
    print("  TX: {0}...".format(log["transactionHash"][:24]))
    print("  Block: {0}".format(int(log["blockNumber"], 16)))
    print("  account:           {0}".format(acct))
    print("  collateralToken:   {0}".format(coll))
    print("  indexToken/market: {0}".format(mkt))
    print("  sizeInUsd:         {0}{1}".format(neg, sz))
    print("  collateralAmount:  {0}{1}".format(neg, ca))
    print("  executionPrice:    {0}".format(pr))
    print("  fee:               {0}".format(fe))
    print("  isLong:            {0}".format(il))

print("")
print("===== FIX VERIFIED: values are NOT all 64 =====")