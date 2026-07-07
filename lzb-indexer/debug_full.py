import sys, io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

with open("D:/lzkcomp/web3/lzb-indexer/verify_tx_real.txt") as f:
    h = f.read().strip()
if h.startswith("0x"): h = h[2:]

def b2i(h, co):
    if len(h) < co + 64: return 0
    return int(h[co:co+64], 16)

def ris(h, co):
    if co < 0 or len(h) < co + 64: return ""
    b = bytes.fromhex(h[co:co+64])
    end = 0
    while end < len(b) and b[end] != 0: end += 1
    return b[:end].decode("utf-8", errors="replace")

ed_oc = b2i(h, 128) * 2
ro = [b2i(h, ed_oc + i*64) for i in range(7)]

# uintItems data
uint_ss = ed_oc + ro[1] * 2  # = structCharOff in Java
print("uintItems structCharOff (hex):", uint_ss)

ao = b2i(h, uint_ss)  # = 0x40
ast = uint_ss + ao * 2  # arrStart
print("arrOff:", ao, "arrStart:", ast)

n = b2i(h, ast)
print("count:", n)

# Dump raw data around arrStart and first few items
print("\n=== RAW DATA DUMP ===")
# The offset table entries (each 64 hex chars = 32 bytes = one offset)
cur = ast + 64
for i in range(min(n, 20)):
    io = b2i(h, cur)
    ist = ast + io * 2
    
    # Show the offset table entry
    print(f"\nOffset[{i}]: cur={cur}, offset={io}, itemStart={ist}")
    
    # Show raw key + value at itemStart
    print(f"  Key slot:   {h[ist:ist+64]}")
    print(f"  Value slot: {h[ist+64:ist+128]}")
    
    # Try to decode
    fs = b2i(h, ist)
    if fs < 10000 and fs > 0:
        k = f"OFFSET({fs})"
    else:
        k = ris(h, ist)
    v = b2i(h, ist + 64)
    print(f"  Decoded: key='{k}' value={v}")
    
    cur += 64

# Also show what's before/after the items data area
print("\n=== BOUNDARY CHECK ===")
print(f"Items data area: {ast+64} to {ast+64 + n*128}")
# Show what's at the value offset positions
for i in range(min(n, 5)):
    io = b2i(h, ast + 64 + i*64)
    ist = ast + io * 2
    val = b2i(h, ist + 64)
    if val < 10000 and val > 0:
        # Follow offset
        vo = (ist // 2 + val) * 2
        print(f"Item {i}: value_offset={val}, resolved_pos={vo}, raw={h[vo:vo+64]}, decoded={b2i(h, vo)}")
