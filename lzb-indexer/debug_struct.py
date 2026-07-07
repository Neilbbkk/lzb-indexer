import json, sys, io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

with open("D:/lzkcomp/web3/lzb-indexer/verify_tx1_data.txt") as f:
    h = f.read().strip()
if h.startswith("0x"): h = h[2:]

def b2i(h, co):
    if len(h) < co + 64: return 0
    return int(h[co:co+64], 16)

# EventLogData offsets
ed_oc = b2i(h, 128) * 2
print("edOffChar:", ed_oc)

rel_off = [b2i(h, ed_oc + i*64) for i in range(7)]
print("relOff (bytes from EventLogData start):", rel_off)

# Check what's ACTUALLY at each data position
for i, name in enumerate(["addrItems","uintItems","intItems","boolItems","bytes32Items","bytesItems","stringItems"]):
    struct_start = ed_oc + rel_off[i] * 2
    first_word = h[struct_start:struct_start+64]
    val = int(first_word, 16)
    print("\n{} at char {}: {}".format(name, struct_start, first_word))
    print("  First word as int: {}".format(val))
    if val > 0 and val < 5000:
        # This could be a length
        print("  (could be length = {})".format(val))
    elif val == 0x40:
        print("  (= 0x40 = offset 64 bytes)")

# For addrItems, manually show item 0, 1, 2
print("\n\n=== addrItems detailed ===")
addr_ss = ed_oc + rel_off[0] * 2
print("addrItems start:", addr_ss)
print("First word (length?):", h[addr_ss:addr_ss+64], "=", int(h[addr_ss:addr_ss+64], 16))
# Show items at addr_ss + 64 (item 0), addr_ss + 128 (item 1), etc
for i in range(5):
    pos = addr_ss + 64 + i*128
    if len(h) < pos + 128: break
    key_raw = h[pos:pos+64]
    val_raw = h[pos+64:pos+128]
    # Try to read key as inline string
    kb = bytes.fromhex(key_raw)
    end = 0
    while end < len(kb) and kb[end] != 0: end += 1
    key_str = kb[:end].decode("utf-8", errors="replace")
    print("  Item {}: key='{}' raw_val={}".format(i, key_str, val_raw))
