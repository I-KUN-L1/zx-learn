# -*- coding: utf-8 -*-
"""
知行智学 · 全量接口矩阵探测引擎（被 scripts/verify-full-suite.sh 调用）

职责：对 logs/tmp/api-inventory.json 里的**全部** Controller 端点做统一探测，覆盖：
  - 可达性：端点是否真的存在、是否返回可解析的统一响应体；
  - 健壮性：参数缺省 / 非法时是否返回业务错误码，而不是 5xx 或空响应；
  - 鉴权矩阵：匿名 / 学员 / 管理员 三种身份下的返回码。

设计约束（与项目既有脚本口径一致）：
  * 用**不存在的哨兵 id**（999999999999）与**非法/缺省 body**做探测 → 不写业务数据、不误删真实数据；
  * 用管理员与学员双身份探测，任一身份返回业务成功即视为端点可达（很多端点按角色分流，
    学员端点用管理员 token 会 403，属预期，不算失败）；
  * 判定失败只看两类硬信号：HTTP 5xx（服务端异常）与「非 JSON / 无法解析」（契约破坏）。
"""
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

GW = os.environ.get("ZX_GW", "http://localhost:8080")
ROOT = os.environ.get("ZX_ROOT", r"D:/1/zx-learn")
INV = os.path.join(ROOT, "logs", "tmp", "api-inventory.json")
DETAIL = os.path.join(ROOT, "logs", "verify-full-suite", "api-matrix-detail.txt")

SENTINEL_ID = "999999999999"
PROBE_COURSE = os.environ.get("ZX_COURSE_ID", "3001")

opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))


def call(method, path, token=None, body=None, timeout=25):
    url = GW + path
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    t0 = time.time()
    try:
        with opener.open(req, timeout=timeout) as resp:
            raw = resp.read()
            code = resp.status
    except urllib.error.HTTPError as e:
        raw = e.read()
        code = e.code
    except Exception as e:  # 连接失败 / 超时
        return {"http": 0, "err": type(e).__name__ + ": " + str(e)[:120], "ms": int((time.time() - t0) * 1000)}
    ms = int((time.time() - t0) * 1000)
    text = raw.decode("utf-8", "replace")
    out = {"http": code, "ms": ms, "raw": text[:600]}
    try:
        j = json.loads(text)
    except Exception:
        out["biz"] = None
        out["json"] = False
        return out
    out["json"] = True
    if isinstance(j, dict):
        out["biz"] = j.get("code", "NO_CODE")
        out["msg"] = str(j.get("msg", ""))[:120]
    else:
        out["biz"] = "NON_DICT"
        out["msg"] = ""
    return out


def login(cell, pwd):
    # 注意：不能用 call() 的 raw（被截断到 600 字符，accessToken 可能超出）→ 重新取完整响应体
    url = GW + "/accounts/login"
    data = json.dumps({"cellPhone": cell, "password": pwd}).encode()
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"}, method="POST")
    with opener.open(req, timeout=25) as resp:
        j = json.loads(resp.read().decode("utf-8"))
    return j["data"]["accessToken"], j["data"].get("userId")


# ---------- 探针参数推导 ----------
PATH_VARS = {
    "courseId": PROBE_COURSE,
    "id": SENTINEL_ID,
    "userId": "1",          # 稍后按登录用户覆盖
    "sessionId": "probe-no-such-session",
    "roleId": SENTINEL_ID,
    "menuId": "1",
    "pid": "0",
    "couponId": SENTINEL_ID,
    "questionId": SENTINEL_ID,
    "bizOrderId": SENTINEL_ID,
    "lessonId": "1",
    "mediaId": "1",
    "isStaff": "true",
    "status": "1",
    "key": "probe-nonexistent.png",
    "bizIds": "1",
    "ids": "1",
}


def param_value(name):
    n = name.lower()
    if n in ("ids", "bizids"):
        return "1,2"
    if n == "courseid":
        return PROBE_COURSE
    if n in ("text", "query", "keyword", "name", "title"):
        return "probe"
    if n in ("cellphone", "phone"):
        return "13900000001"
    if n in ("topk", "top", "limit", "pageno", "pagesize", "size", "page"):
        return "1"
    if n == "status":
        return "1"
    if n in ("id", "orderid", "couponid", "userid", "questionid", "mediaid", "bizorderid"):
        return SENTINEL_ID
    if n == "sessionid":
        return "probe-no-such-session"
    if n in ("published",):
        return "true"
    if n in ("isasc",):
        return "false"
    if n in ("sortby",):
        return "id"
    return "probe"


def parse_sig(sig):
    """从方法签名里抽出必填的 @RequestParam 名（无 defaultValue / required=false 的）"""
    names = []
    for m in re.finditer(r'@RequestParam\s*\(([^)]*)\)\s*(?:\w+(?:<[^>]*>)?\s+)?(\w+)', sig):
        args, var = m.group(1), m.group(2)
        if "defaultValue" in args or "required = false" in args or "required=false" in args:
            continue
        vname = re.search(r'(?:value|name)\s*=\s*"([^"]+)"', args)
        names.append(vname.group(1) if vname else var)
    # 无括号的 @RequestParam
    for m in re.finditer(r'@RequestParam\s+(?!\()\s*(?:\w+(?:<[^>]*>)?\s+)?(\w+)', sig):
        names.append(m.group(1))
    return names


def build_path(raw_path, user_id):
    def rep(m):
        var = m.group(1).split(":")[0].strip()
        if var.startswith("*"):
            return "/probe-nonexistent.png"
        if var == "userId":
            return str(user_id)
        return PATH_VARS.get(var, "1")

    return re.sub(r'\{([^}]+)\}', rep, raw_path)


def main():
    rows = json.load(open(INV, encoding="utf-8"))
    stu_tok, stu_id = login("13900000001", "123456")
    adm_tok, _ = login("13800000001", "123456")

    detail = open(DETAIL, "w", encoding="utf-8")
    detail.write("模块 | 方法 | 路径 | 匿名 | 学员 | 管理员 | 判定\n")
    detail.write("-" * 110 + "\n")

    hard_fail = []        # 5xx 或契约破坏
    not_reachable = []    # 两种身份都拿不到业务码（404 路由缺失等）
    ok_count = 0
    anon_open = []        # 匿名可访问（HTTP != 401）
    modules = {}

    for r in rows:
        path = build_path(r["path"], stu_id)
        qs = []
        for pname in parse_sig(r["sig"]):
            qs.append(pname + "=" + urllib.parse.quote(param_value(pname)))
        if qs:
            path = path + ("&" if "?" in path else "?") + "&".join(qs)

        body = None
        if r["verb"] in ("POST", "PUT", "PATCH"):
            body = {}

        anon = call(r["verb"], path)
        stu = call(r["verb"], path, stu_tok, body)
        adm = call(r["verb"], path, adm_tok, body)

        # --- 判定 ---
        verdict = "PASS"
        note = ""
        # 二进制/图片/文档类端点天然不返回 JSON，属预期（文件不存在时可能返回 403/404）
        #   * /files/view          —— 图片字节流
        #   * /orders/admin/export —— CSV 导出（带 UTF-8 BOM 的附件流）
        #     该端点是 @NoWrapper 的，刻意不套统一响应体；若它返回 R（JSON）反而是缺陷
        #     （曾被全局包装器污染成 ClassCastException 500）。
        bin_ok = any(p in r["path"] for p in ("/files/view", "/orders/admin/export"))
        for tag, rsp in (("anon", anon), ("stu", stu), ("adm", adm)):
            if rsp.get("err"):
                verdict = "FAIL"
                note += " [%s连接失败:%s]" % (tag, rsp["err"][:60])
            elif rsp["http"] >= 500:
                verdict = "FAIL"
                note += " [%s HTTP5xx=%s]" % (tag, rsp["http"])
            elif not rsp.get("json"):
                if bin_ok and rsp["http"] in (200, 403, 404):
                    continue
                verdict = "FAIL"
                note += " [%s非JSON]" % tag

        reachable = any(
            isinstance(x.get("biz"), int) and x["http"] == 200
            and x["biz"] not in (404,)
            for x in (stu, adm)
        )
        if verdict == "PASS" and not reachable:
            # 双方都拿不到业务码。注意区分两种情况：
            #   * 真正的路由缺失 → 网关/Spring 返回 **HTTP 404**；
            #   * 业务码 404（统一响应体 code=404，但 HTTP 200）→ 正常业务语义
            #     （如 /pay-orders/{id}/status 查不到支付单），不能算路由问题。
            codes = {str(x.get("biz")) for x in (stu, adm)}
            http_404 = any(x.get("http") == 404 for x in (stu, adm))
            if codes <= {"403", "404"} and http_404:
                verdict = "FAIL"
                note += " [两种身份均 404：网关路由未命中]"
                not_reachable.append(r)

        if verdict == "FAIL":
            hard_fail.append((r, note))
        else:
            ok_count += 1
        if not anon.get("err") and anon["http"] == 200:
            anon_open.append(r)

        mod = r["mod"]
        modules.setdefault(mod, [0, 0, 0])   # total, pass, fail
        modules[mod][0] += 1
        if verdict == "PASS":
            modules[mod][1] += 1
        else:
            modules[mod][2] += 1

        detail.write("%s | %s | %s | %s | %s | %s | %s %s\n" % (
            mod, r["verb"], r["path"],
            anon.get("http", anon.get("err", "-")),
            "%s/%s" % (stu.get("http"), stu.get("biz")),
            "%s/%s" % (adm.get("http"), adm.get("biz")),
            verdict, note))

    detail.write("-" * 110 + "\n")
    detail.close()

    # ---------- 输出（供 bash 收集进报告） ----------
    total = len(rows)
    print("接口总数 %d，探测正常 %d，异常 %d" % (total, ok_count, len(hard_fail)))
    print("")
    print("--- 分模块统计 ---")
    for mod in sorted(modules):
        t, p, f = modules[mod]
        print("  %-14s 端点 %3d  正常 %3d  异常 %3d" % (mod, t, p, f))
    print("")
    print("--- 匿名可访问端点（HTTP 200，共 %d 个） ---" % len(anon_open))
    for r in anon_open:
        print("  [ANON] %-6s %s" % (r["verb"], r["path"]))
    print("")
    print("--- 异常端点明细 ---")
    if not hard_fail:
        print("  （无）")
    for r, note in hard_fail:
        print("  [FAIL] %-6s %-52s %s" % (r["verb"], r["path"], note))
    print("")
    print("MATRIX_TOTAL=%d" % total)
    print("MATRIX_OK=%d" % ok_count)
    print("MATRIX_FAIL=%d" % len(hard_fail))
    print("MATRIX_ANON_OPEN=%d" % len(anon_open))


if __name__ == "__main__":
    main()
