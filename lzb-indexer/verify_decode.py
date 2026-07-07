import json, sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

def bytes_to_bigint(h, co):
    if len(h) < co + 64: return 0
    return int(h[co:co+64], 16)

def read_inline_string(h, co):
    if co < 0 or len(h) < co + 64: return ""
    b = bytes.fromhex(h[co:co+64])
    end = 0
    while end < len(b) and b[end] != 0: end += 1
    return b[:end].decode("utf-8", errors="replace")

def read_string(h, co):
    if co < 0 or len(h) < co + 64: return ""
    l = bytes_to_bigint(h, co)
    if l <= 0 or l > 10000: return ""
    if len(h) < co + 64 + l * 2: return ""
    return bytes.fromhex(h[co+64:co+64+l*2]).decode("utf-8", errors="replace")

def parse_kv(h, sco, vp):
    r = {}
    if sco <= 0 or len(h) < sco + 64: return r
    ao = bytes_to_bigint(h, sco)
    ast = sco + ao * 2
    if ast < sco or len(h) < ast + 64: return r
    n = bytes_to_bigint(h, ast)
    if n <= 0 or n > 1000: return r
    cur = ast + 64
    for _ in range(min(n, 200)):
        if len(h) < cur + 64: break
        io = bytes_to_bigint(h, cur)
        ist = ast + io * 2
        if len(h) < ist + 128: break
        fs = bytes_to_bigint(h, ist)
        if fs < 10000 and fs > 0:
            k = read_string(h, ist + fs * 2)
        else:
            k = read_inline_string(h, ist)
        v = vp(h, ist)
        if k: r[k] = v
        cur += 64
    return r

def paddr(h, ist):
    rv = bytes_to_bigint(h, ist + 64)
    if rv < 10000 and rv > 0:
        vo = (ist // 2 + rv) * 2
        if len(h) >= vo + 64:
            return "0x" + h[vo+24:vo+64]
    return "0x" + h[ist+64+24:ist+128]

def puint(h, ist):
    return bytes_to_bigint(h, ist + 64)

def pbool(h, ist):
    return h[ist+64:ist+128] != "0" * 64

def pb32(h, ist):
    return "0x" + h[ist+64:ist+128]

with open("D:/lzkcomp/web3/lzb-indexer/verify_tx1_data.txt") as f:
    hex_str = f.read().strip()
if hex_str.startswith("0x"): hex_str = hex_str[2:]

ed_oc = bytes_to_bigint(hex_str, 128) * 2
rel_off = [bytes_to_bigint(hex_str, ed_oc + i*64) for i in range(7)]

addrs = parse_kv(hex_str, ed_oc + rel_off[0] * 2, paddr)
uints = parse_kv(hex_str, ed_oc + rel_off[1] * 2, puint)
bools = parse_kv(hex_str, ed_oc + rel_off[3] * 2, pbool)
b32s  = parse_kv(hex_str, ed_oc + rel_off[4] * 2, pb32)

print("=== AddressItems ({}):".format(len(addrs)))
for k,v in sorted(addrs.items()): 
    print("  {} = {}".format(k, v))

print("\n=== UintItems ({}):".format(len(uints)))
for k,v in sorted(uints.items()): 
    print("  {} = {}".format(repr(k), v))

print("\n=== BoolItems ({}):".format(len(bools)))
for k,v in sorted(bools.items()): 
    print("  {} = {}".format(repr(k), v))

print("\n=== Bytes32Items ({}):".format(len(b32s)))
for k,v in sorted(b32s.items()): 
    print("  {} = {}".format(repr(k), v))

# Extract fields
market = addrs.get("market", addrs.get("indexToken", addrs.get("longToken", addrs.get("shortToken", ""))))
collateral_token = addrs.get("collateralToken", "")
account = addrs.get("account", "")
size_in_usd = uints.get("sizeInUsd", 0)
collateral_amount = uints.get("collateralAmount", uints.get("initialCollateralDeltaAmount", 0))
price = uints.get("executionPrice", uints.get("price", 0))
fee = uints.get("positionFeeAmount", 0)
is_long = bools.get("isLong", False)

print("\n=== EXTRACTED (per EventDecoder logic) ===")
print("  market/indexToken:  ", market)
print("  collateralToken:     ", collateral_token)
print("  account:             ", account)
print("  sizeInUsd:           ", size_in_usd)
print("  collateralAmount:    ", collateral_amount)
print("  price:               ", price)
print("  fee:                 ", fee)
print("  isLong:              ", is_long)

print("\n=== DB VALUES ===")
print("  collateral_token:  0x1064b9d788314d6bafd5a318c6f15bd3366b67a6")
print("  index_token:       0xaf88d065e77c8cc2239327c5edb3a432268e5831")
print("  account:           0x47c031236e19d024b42f8ae6780e44a573170703")
print("  collateral_delta:  -64 (Chain decoded collateralAmount negated)")
print("  size_delta:        -64 (Chain decoded sizeInUsd negated)")
print("  price:             64")
print("  fee:               0")
print("  is_long:           false")
