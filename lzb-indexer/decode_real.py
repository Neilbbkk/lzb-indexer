import sys, io

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

with open("D:/lzkcomp/web3/lzb-indexer/verify_tx_real.txt") as f:
    h = f.read().strip()
if h.startswith("0x"): h = h[2:]

print("Data length:", len(h))
ed_oc = b2i(h, 128) * 2
print("edOffChar:", ed_oc)

ro = [b2i(h, ed_oc + i*64) for i in range(7)]
print("relOff:", ro)

# Parse addresses
def parse_kv_addr(h, sco):
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
        rv = b2i(h, ist + 64)
        if rv < 10000 and rv > 0:
            vo = (ist // 2 + rv) * 2
            if len(h) >= vo + 64:
                v = "0x" + h[vo+24:vo+64]
            else:
                v = ""
        else:
            v = "0x" + h[ist+64+24:ist+128]
        if k: r[k] = v
        cur += 64
    return r

# Parse uint - same structure as addr for item offsets,
# but value is just uint256
def parse_kv_uint(h, sco):
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
        v = b2i(h, ist + 64)
        if k: r[k] = v
        cur += 64
    return r

addrs = parse_kv_addr(h, ed_oc + ro[0] * 2)
uints = parse_kv_uint(h, ed_oc + ro[1] * 2)

print("\n=== AddressItems ===")
for k,v in sorted(addrs.items()): print("  {} = {}".format(k, v))

print("\n=== UintItems ===")
for k,v in sorted(uints.items()): print("  {} = {}".format(repr(k), v))

# Extract key fields
market = addrs.get("market", addrs.get("indexToken", addrs.get("longToken", addrs.get("shortToken", ""))))
print("\n=== Extracted ===")
print("  collateralToken:", addrs.get("collateralToken", "N/A"))
print("  indexToken/market:", market)
print("  account:", addrs.get("account", "N/A"))
print("  sizeInUsd:", uints.get("sizeInUsd", "N/A"))
print("  collateralAmount:", uints.get("collateralAmount", uints.get("initialCollateralDeltaAmount", "N/A")))
print("  executionPrice:", uints.get("executionPrice", uints.get("price", "N/A")))
print("  positionFeeAmount:", uints.get("positionFeeAmount", "N/A"))
