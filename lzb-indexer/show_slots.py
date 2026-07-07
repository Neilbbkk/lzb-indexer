import sys
sys.stdout.reconfigure(encoding='utf-8')

with open(r'D:\lzkcomp\web3\lzb-indexer\eth_getLogs_data.txt') as f:
    data = f.read().strip()
h = data[2:] if data.startswith('0x') else data

def b2i(h, co):
    return int(h[co:co+64], 16) if len(h) >= co + 64 else 0

def ris(h, co):
    if co < 0 or len(h) < co + 64: return ''
    b = bytes.fromhex(h[co:co+64])
    end = 0
    while end < len(b) and b[end] != 0: end += 1
    return b[:end].decode('utf-8', errors='replace')

ed_oc = b2i(h, 128) * 2
ro = [b2i(h, ed_oc + i*64) for i in range(7)]

# ===== addressItems =====
addr_ss = ed_oc + ro[0] * 2
ao = b2i(h, addr_ss)
ast = addr_ss + ao * 2
n_addr = b2i(h, ast)
print('=== addressItems ({} items) ==='.format(n_addr))
for i in range(min(n_addr, 3)):
    cur = ast + 64 + i*64
    io = b2i(h, cur)
    ist = ast + io * 2
    print('item[{}]: cur={} ist={}'.format(i, cur, ist))
    print('  slot0 (key):    {}'.format(h[ist:ist+64]))
    print('  slot1 (val_pt): {}'.format(h[ist+64:ist+128]))
    print('  slot2 (addr):   {}'.format(h[ist+128:ist+192]))
    fs = b2i(h, ist)
    k = ris(h, ist) if fs >= 10000 or fs <= 0 else '(offset-key)'
    rv = b2i(h, ist + 64)
    if rv < 10000 and rv > 0:
        vo = (ist // 2 + rv) * 2
        v = '0x' + h[vo+24:vo+64]
    else:
        v = '0x' + h[ist+64+24:ist+128]
    print('  => key={}  addr={}'.format(k, v))
    print()

# ===== uintItems =====
uint_ss = ed_oc + ro[1] * 2
ao = b2i(h, uint_ss)
ast = uint_ss + ao * 2
n_uint = b2i(h, ast)
print('=== uintItems ({} items, showing key fields) ==='.format(n_uint))
cur = ast + 64
for i in range(min(n_uint, 20)):
    io = b2i(h, cur)
    ist = ast + io * 2
    if len(h) < ist + 192: break
    k = ris(h, ist)
    marker = b2i(h, ist + 64)
    val = b2i(h, ist + 128) if (marker < 10000 and marker > 0) else marker
    if k in ('sizeInUsd', 'collateralAmount', 'executionPrice', 'sizeInTokens', 'sizeDeltaUsd'):
        print('item[{}]: ist={}'.format(i, ist))
        print('  slot0 (key={}):   {}'.format(k, h[ist:ist+64]))
        print('  slot1 (marker):   {}  = 0x{:x}'.format(h[ist+64:ist+128], marker))
        print('  slot2 (VALUE):    {}  = {}'.format(h[ist+128:ist+192], val))
        print()
    cur += 64

# ===== boolItems =====
bool_ss = ed_oc + ro[3] * 2
ao = b2i(h, bool_ss)
ast = bool_ss + ao * 2
n_bool = b2i(h, ast)
print('=== boolItems ({} items) ==='.format(n_bool))
cur = ast + 64
for i in range(min(n_bool, 5)):
    io = b2i(h, cur)
    ist = ast + io * 2
    if len(h) < ist + 192: break
    print('item[{}]: ist={}'.format(i, ist))
    print('  slot0 (key):       {}'.format(h[ist:ist+64]))
    print('  slot1 (marker):    {}'.format(h[ist+64:ist+128]))
    print('  slot2 (bool):      {}'.format(h[ist+128:ist+192]))
    slot = h[ist+128:ist+192]
    print('  => {}'.format(slot != '0'*64))
    cur += 64