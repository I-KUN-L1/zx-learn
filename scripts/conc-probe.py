# -*- coding: utf-8 -*-
"""并发探测（被 scripts/verify-full-suite.sh 阶段 10 调用）

为什么不用 for 循环起 200 个 curl 进程：Windows 下进程创建本身就要几十毫秒，
200 个并发进程的调度开销会淹没被测服务，测出来的是"测试机能起多少进程"，不是服务端能力。
本脚本用线程池固定并发数，压的是**同一个已建立的 HTTP 通路**，结果更接近真实并发。
"""
import collections
import concurrent.futures
import os
import sys
import time
import urllib.error
import urllib.request

GW = os.environ.get("ZX_GW", "http://localhost:8080")
PATH = os.environ.get("ZX_CONC_PATH", "/courses/page?pageNo=1&pageSize=10")
TOKEN = os.environ.get("ZX_CONC_TOKEN", "")
TOTAL = int(os.environ.get("ZX_CONC_N", "200"))
CONC = int(os.environ.get("ZX_CONC_C", "20"))

opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def one(_):
    headers = {"Accept": "application/json"}
    if TOKEN:
        headers["Authorization"] = "Bearer " + TOKEN
    req = urllib.request.Request(GW + PATH, headers=headers)
    t0 = time.time()
    try:
        with opener.open(req, timeout=30) as resp:
            resp.read()
            return resp.status, time.time() - t0
    except urllib.error.HTTPError as e:
        e.read()
        return e.code, time.time() - t0
    except Exception:
        return 0, time.time() - t0


t0 = time.time()
with concurrent.futures.ThreadPoolExecutor(max_workers=CONC) as pool:
    results = list(pool.map(one, range(TOTAL)))
wall = time.time() - t0

codes = collections.Counter(r[0] for r in results)
lat = sorted(r[1] for r in results if r[0] != 0)
p95 = lat[min(len(lat) - 1, int(0.95 * len(lat)) - 1)] * 1000 if lat else 0
avg = (sum(lat) / len(lat) * 1000) if lat else 0

print("CONC_PATH=%s" % PATH)
print("CONC_TOTAL=%d" % TOTAL)
print("CONC_OK=%d" % codes.get(200, 0))
print("CONC_5XX=%d" % sum(v for k, v in codes.items() if 500 <= k < 600))
print("CONC_FAIL_CONN=%d" % codes.get(0, 0))
print("CONC_CODES=%s" % ",".join("%s:%d" % (k, v) for k, v in sorted(codes.items())))
print("CONC_RPS=%.1f" % (TOTAL / wall if wall > 0 else 0))
print("CONC_AVG_MS=%.0f" % avg)
print("CONC_P95_MS=%.0f" % p95)
