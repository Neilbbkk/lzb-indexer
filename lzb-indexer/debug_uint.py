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

with open("D:/lzkcomp/web3/lzb-indexer/verify_receipts.json", encoding="utf-8-sig") as f:
    receipts = json.load(f)

tx = "0x17b8f52be011378be9700698df65794dda61523ee8072604941c50c4866bdc45"
log_data = None
for log in receipts[tx]["logs"]:
    if log["logIndex"] == "0x32":  # 50
        log_data = log["data"]
        break

h = log_data[2:] if log_data.startswith("0x") else log_data
print("Data length:", len(h))

ed_oc = b2i(h, 128) * 2
print("edOffChar:", ed_oc)

rel_off = [b2i(h, ed_oc + i*64) for i in range(7)]
print("relOff:", rel_off)

# Dump raw hex around uint items header (rel_off[1] = offset of uintItems in EventLogData)
uint_struct_off = ed_oc + rel_off[1] * 2
print("\nuintItems struct at char offset:", uint_struct_off)
print("uintItems header (offset + count):", h[uint_struct_off:uint_struct_off+128])

arr_off = b2i(h, uint_struct_off)
arr_start = uint_struct_off + arr_off * 2
print("uintItems array start:", arr_start)

n = b2i(h, arr_start)
print("uintItems count:", n)

# Dump first 3 items
cur = arr_start + 64
for i in range(min(n, 5)):
    io = b2i(h, cur)
    ist = arr_start + io * 2
    print("\n--- Item {} ---".format(i))
    print("  offset in array: {} ({} chars)".format(io, io*2))
    print("  itemStart:", ist)
    print("  key slot raw:  ", h[ist:ist+64])
    print("  value slot raw:", h[ist+64:ist+128])
    
    # Try decoding key
    fs = b2i(h, ist)
    print("  firstSlot as int:", fs)
    if fs < 10000 and fs > 0:
        k = rs(h, ist + fs * 2)
    else:
        k = ris(h, ist)
    print("  key decoded:", repr(k))
    
    # Try decoding value
    val = b2i(h, ist + 64)
    print("  value decoded:", val)
    
    cur += 64
