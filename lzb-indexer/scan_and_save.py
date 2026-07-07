import json, urllib.request, psycopg2, sys
sys.stdout.reconfigure(encoding="utf-8")

def rpc(method, params):
    body = json.dumps({"jsonrpc":"2.0","method":method,"params":params,"id":1}).encode()
    req = urllib.request.Request("https://arb1.arbitrum.io/rpc", body, {"Content-Type":"application/json", "User-Agent":"Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read())["result"]

def b2i(h, co):
    return int(h[co:co+64], 16) if len(h) >= co + 64 else 0

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
