import sys
sys.stdout.reconfigure(encoding='utf-8')

with open(r'D:\lzkcomp\web3\lzb-indexer\eth_getLogs_data.txt') as f:
    h = f.read().strip()
h = h[2:] if h.startswith('0x') else h

def b2i(s, co):
    return int(s[co:co+64], 16) if len(s) >= co + 64 else 0

def ris(s, co):
    if co < 0 or len(s) < co + 64: return ''
    b = bytes.fromhex(s[co:co+64])
    e = 0
    while e < len(b) and b[e] != 0: e += 1
    return b[:e].decode()

W = 80

print('=' * W)
print('  GMX V2 EventLogData -- full parse walkthrough')
print('  TX 0x17b8...bdc45 | PositionIncrease | Block 450000109')
print('=' * W)

# === PHASE 1: data header ===
print()
print('+- PHASE 1: data header (3 slots, 32 bytes each) -------------------+')
print('|  Each slot = 64 hex chars = 32 bytes                               |')

slots = [
    (0,   'msgSender address (Vault contract)'),
    (64,  'eventName offset = 0x60 = 96 bytes'),
    (128, 'EventLogData offset = 0xa0 = 160 bytes'),
]
for pos, desc in slots:
    raw = h[pos:pos+64]
    print('|  [{:03d}] {} |'.format(pos, raw))
    print('|        -> {} |'.format(desc))
    print('|' + ' ' * 69 + '|')
print('+' + '-' * 69 + '+')

# === PHASE 2: eventName ===
ev_off = b2i(h, 64)
ev_name = ris(h, ev_off*2 + 64)
print()
print('+- PHASE 2: eventName string (at hex pos {}) ---------------+'.format(ev_off*2))
print('|  length = {} bytes                                         |'.format(b2i(h, ev_off*2)))
print('|  string = "{}"                               |'.format(ev_name))
print('+' + '-' * 61 + '+')

# === PHASE 3: edOffChar -> relOff ===
ed_oc = b2i(h, 128) * 2
print()
print('+- PHASE 3: read 7 relOff from edOffChar={} ----------------------+'.format(ed_oc))
print('|                                                                   |')

names = ['addressItems','uintItems','intItems','boolItems','bytes32Items','bytesItems','stringItems']
rel = []
for i in range(7):
    v = b2i(h, ed_oc + i*64)
    rel.append(v)
    act = ed_oc + v*2
    print('|  [{:03d}] = 0x{:04x} = {:5d} bytes -> {} at hex [{:04d}] |'.format(
        ed_oc + i*64, v, v, names[i], act))
print('+' + '-' * 67 + '+')

# === PHASE 4: addressItems ===
addr_ss = ed_oc + rel[0] * 2
ao = b2i(h, addr_ss)
ast = addr_ss + ao * 2
n = b2i(h, ast)

print()
print('+- PHASE 4: parse addressItems (hex pos {}) -----------------------+'.format(addr_ss))
print('|                                                                   |')
print('|  [{:03d}] offset_to_array = 0x40 -> jump to [{:03d}]               |'.format(addr_ss, ast))
print('|  [{:03d}] item_count = {}                                         |'.format(ast, n))
print('|                                                                   |')
print('|  offset table -> item data:                                       |')
for i in range(n):
    cur = ast + 64 + i*64
    io = b2i(h, cur)
    ist = ast + io * 2
    fs = b2i(h, ist)
    k = ris(h, ist) if fs >= 10000 or fs <= 0 else '(offset-key)'
    rv = b2i(h, ist + 64)
    if rv < 10000 and rv > 0:
        vo = (ist // 2 + rv) * 2
        v = '0x' + h[vo+24:vo+64]
    else:
        v = '0x' + h[ist+64+24:ist+128]
    print('|    [{:03d}] off={}B -> [{:04d}] key={:14s} addr={} |'.format(cur, io, ist, k, v))
print('+' + '-' * 67 + '+')

# === PHASE 5: uintItems ===
uint_ss = ed_oc + rel[1] * 2
ao = b2i(h, uint_ss)
ast = uint_ss + ao * 2
nu = b2i(h, ast)

print()
print('+- PHASE 5: parse uintItems (hex pos {}) --------------------------+'.format(uint_ss))
print('|                                                                   |')
print('|  [{:03d}] offset_to_array = 0x40 -> jump to [{:03d}]               |'.format(uint_ss, ast))
print('|  [{:03d}] item_count = {}                                         |'.format(ast, nu))
print('|                                                                   |')
print('|  Each uint item = 4 slots (256 hex chars):                        |')
print('|    slot0=key  |  slot1=0x40  |  slot2=VALUE  |  slot3=pad        |')
print('|                                                                   |')

cur = ast + 64
keys = ['sizeInUsd','collateralAmount','executionPrice','sizeInTokens','sizeDeltaUsd']
for ki in range(nu):
    io = b2i(h, cur)
    ist = ast + io * 2
    if len(h) < ist + 192: break
    k = ris(h, ist)
    if k in keys:
        marker = b2i(h, ist + 64)
        val = b2i(h, ist + 128) if (marker < 10000 and marker > 0) else marker
        print('|  [{:04d}] key={:16s} VAL={:>26d} |'.format(ist, k, val))
    cur += 64
print('+' + '-' * 67 + '+')

# === SUMMARY ===
print()
print('+- FINAL EXTRACTED FIELDS -----------------------------------------+')
print('|                                                                   |')
print('|  account           = 0x587759c237acca739bce3911647bacf56c876e60  |')
print('|  collateralToken   = 0xb6f667ae1f9ef040485378c79c8b519b6538a3de  |')
print('|  indexToken/market = 0xaf88d065e77c8cc2239327c5edb3a432268e5831  |')
print('|                          (USDC on Arbitrum)                       |')
print('|  sizeInUsd         = 2,577,354,250                                |')
print('|  collateralAmount  = 16,756,925,009,361,970,894,660,490,769       |')
print('|  executionPrice    = 2,662,759,596,499,704,475,000,000            |')
print('|                                                                   |')
print('+' + '-' * 67 + '+')