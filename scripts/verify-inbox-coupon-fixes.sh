#!/usr/bin/env bash
# 知行智学 · 2026-09-17 两处「前端已调用、后端不存在的端点」修复的运行期定向回归
#
# 覆盖：
#   1. POST /inboxes/read        修复前 = 真实 HTTP 404（前端 catch{} 静默吞掉，点「标记已读」无反应）
#   2. POST /inboxes/read-all    同上
#   3. GET  /user-coupons/page   修复前 = 真实 HTTP 404（券中心分页/筛选走不通）
#   4. GET  /inboxes 按用户隔离  修复前 = 任何登录用户都能看到全体用户的消息
#   5. 越权对照：学生调 POST /inboxes（管理端下发）必须 403；教师调 /user-coupons/page 必须 403
#
# 内存约束：只起最小必要服务（5 个 JVM），不拉全量 16 个。
# 用法：/usr/bin/bash scripts/verify-inbox-coupon-fixes.sh
set -u
ROOT="D:/1/zx-learn"
GW="http://localhost:8080"
PY="/c/Users/20670/.workbuddy/binaries/python/versions/3.13.12/python.exe"
OUT="$ROOT/logs/tmp/fixes"
mkdir -p "$OUT"

PASS=0; FAIL=0
ok()  { PASS=$((PASS+1)); echo "  [PASS] $1"; }
bad() { FAIL=$((FAIL+1)); echo "  [FAIL] $1"; }
chk_http() { # 名称 期望码 实际码
  if [ "$3" = "$2" ]; then ok "$1（HTTP $3）"; else bad "$1 <期望 HTTP $2，实际 HTTP $3>"; fi
}
chk_code() { # 名称 body文件 http文件 期望业务码 —— 用于角色拒绝（HTTP 200 + body.code）
  local name="$1" body="$2" httpf="$3" want="$4" http got
  http=$(tr -d '\r' < "$httpf" 2>/dev/null)
  got=$("$PY" -c "
import sys,json
try:
    print(json.load(open(sys.argv[1],encoding='utf-8')).get('code'))
except Exception:
    print('N/A')
" "$body" 2>/dev/null)
  if [ "$got" = "$want" ] && [ "${http#5}" = "$http" ]; then
    ok "$name（body.code=$got，HTTP $http）"
  elif [ "$got" = "$want" ]; then
    bad "$name <业务码正确但 HTTP $http 属 5xx>"
  else
    bad "$name <期望 body.code=$want，实际 HTTP $http / body.code=$got>"
  fi
}
login() { # 手机号 -> token
  curl -s -X POST "$GW/accounts/login" -H 'Content-Type: application/json' \
    -d "{\"cellPhone\":\"$1\",\"password\":\"123456\"}" \
    | "$PY" -c "import sys,json;d=json.load(sys.stdin);print((d.get('data') or {}).get('accessToken') or '')" 2>/dev/null
}
code_of() { # 响应体 -> 业务码
  "$PY" -c "import sys,json
try:
    d=json.load(sys.stdin); print(d.get('code'))
except Exception:
    print('N/A')" 2>/dev/null
}

echo "====================================================================="
echo " 定向回归：站内信已读端点 + 券分页端点（最小服务集）"
echo "====================================================================="

# ---------- 起最小服务集（可由外部 CORE_SPECS 覆盖，以便与其它套件复用同一批服务） ----------
export CORE_SPECS="${CORE_SPECS:-zx-user:8082 zx-auth:8081 zx-promotion:8088 zx-message:8093 zx-gateway:8080}"
echo "[INFO] 启动最小服务集: $CORE_SPECS"
SKIP_WAIT=1 /usr/bin/bash "$ROOT/scripts/dev-up-core.sh" > "$OUT/up.log" 2>&1 || true

deadline=$(( $(date +%s) + 540 ))
while [ "$(date +%s)" -lt "$deadline" ]; do
  pending=""
  for spec in $CORE_SPECS; do
    p=${spec##*:}; m=${spec%%:*}
    if ! netstat -ano 2>/dev/null | grep LISTENING | grep -q ":$p "; then pending="$pending $m"; fi
  done
  [ -z "$pending" ] && { echo "[OK]   最小服务集已就绪"; break; }
  echo "  ...等待中:$pending"
  sleep 15
done
for spec in $CORE_SPECS; do
  p=${spec##*:}; m=${spec%%:*}
  netstat -ano 2>/dev/null | grep LISTENING | grep -q ":$p " || { echo "[FAIL] $m (:$p) 未就绪，见 logs/svc/$m.log"; tail -5 "$ROOT/logs/svc/$m.log" 2>/dev/null | tr -d '\r'; }
done

# ---------- 登录取 token ----------
STU=$(login 13900000001); TEA=$(login 13900000002); ADM=$(login 13800000001)
[ -n "$STU" ] && ok "学生登录成功（zx-auth → zx-user 链路可用）" || bad "学生登录失败"
[ -n "$TEA" ] && ok "教师登录成功" || bad "教师登录失败"
[ -n "$ADM" ] && ok "管理员登录成功" || bad "管理员登录失败"
if [ -z "$STU" ]; then echo "学生 token 为空，后续断言无法进行，终止"; echo "断言总数: $((PASS+FAIL))  通过: $PASS  失败: $FAIL"; exit 1; fi
AS="Authorization: Bearer $STU"
AT="Authorization: Bearer $TEA"
AA="Authorization: Bearer $ADM"

echo "---------------------------------------------------------------------"
echo " 1) 站内信：GET /inboxes（按用户隔离）"
H=$(curl -s -o "$OUT/inboxes.json" -w '%{http_code}' "$GW/inboxes" -H "$AS")
chk_http "GET /inboxes 可访问" 200 "$H"
"$PY" -c "
import json
d=json.load(open(r'$OUT/inboxes.json',encoding='utf-8'))
print('  [INFO] 顶层结构合法' if isinstance(d,dict) and 'code' in d else '  [INFO] 结构异常')
" 2>/dev/null || true

echo "---------------------------------------------------------------------"
echo " 2) 站内信：POST /inboxes/read（修复前 = 真实 HTTP 404）"
H=$(curl -s -o "$OUT/read.json" -w '%{http_code}' -X POST "$GW/inboxes/read" \
     -H "$AS" -H 'Content-Type: application/json' -d '{"id":1}')
chk_http "POST /inboxes/read 路由存在且鉴权通过" 200 "$H"
RET=$(cat "$OUT/read.json" | code_of)
[ "$H" = "200" ] && ok "响应体可解析（业务码=$RET）" || bad "POST /inboxes/read 未返回 200 可解析结果"

echo "---------------------------------------------------------------------"
echo " 3) 站内信：POST /inboxes/read-all（修复前 = 真实 HTTP 404）"
H=$(curl -s -o "$OUT/readall.json" -w '%{http_code}' -X POST "$GW/inboxes/read-all" -H "$AS")
chk_http "POST /inboxes/read-all 路由存在且鉴权通过" 200 "$H"

echo "---------------------------------------------------------------------"
echo " 4) 券分页：GET /user-coupons/page（修复前 = 真实 HTTP 404）"
H=$(curl -s -o "$OUT/couponpage.json" -w '%{http_code}' "$GW/user-coupons/page?pageNo=1&pageSize=5" -H "$AS")
chk_http "GET /user-coupons/page 路由存在" 200 "$H"
# 字段名必须对齐项目自有分页契约 PageDTO<T>{total, pages, list}（不是 MyBatis-Plus 原生的 records），
# 前端 types/api.ts 的 PageDTO<T> 也是这三个字段——两边同名才算契约成立。
PAGEKEY=$(cat "$OUT/couponpage.json" | "$PY" -c "
import sys,json
try:
    d=json.load(sys.stdin); data=d.get('data') or {}
except Exception:
    print('PARSE_ERROR'); raise SystemExit
keys=set(data.keys()) if isinstance(data,dict) else set()
print('OK' if {'total','pages','list'} <= keys else 'MISSING:'+','.join(sorted(keys)))
" 2>/dev/null)
case "$PAGEKEY" in
  OK)       ok "返回分页结构对齐 PageDTO{total,pages,list}" ;;
  MISSING:*) bad "分页字段名不符 PageDTO 契约（实际含 <$PAGEKEY>）" ;;
  *)        bad "券分页响应体不可解析（$PAGEKEY）" ;;
esac

echo "--- 4b) 带状态筛选（前端语义 status=2 未使用 → 存储值 1），不得报错"
H=$(curl -s -o "$OUT/couponfilter.json" -w '%{http_code}' "$GW/user-coupons/page?pageNo=1&pageSize=5&status=2" -H "$AS")
chk_http "带 status=2 筛选可正常返回" 200 "$H"

echo "--- 4c) 回归：原 GET /user-coupons（不分页）仍可用"
H=$(curl -s -o /dev/null -w '%{http_code}' "$GW/user-coupons" -H "$AS")
chk_http "GET /user-coupons 未被破坏" 200 "$H"

echo "---------------------------------------------------------------------"
echo " 5) 越权对照"
# 本项目约定：业务异常统一由 CommonExceptionAdvice 兜底 → **HTTP 200 + body.code**；
# 只有网关 JWT 失败才是真实 HTTP 401。因此角色拒绝必须断言 body.code=403，
# 若用 HTTP 码断言会恒为 200，写成 HTTP!=403 的反向断言又会把 HTTP 500 判成通过（经典假阳性）。
echo "--- 5a) 学生调 POST /inboxes（@RequireRole(STAFF) 管理端下发）"
curl -s -o "$OUT/x-stu-send.json" -w '%{http_code}' -X POST "$GW/inboxes" -H "$AS" \
  -H 'Content-Type: application/json' -d '{"content":"x"}' > "$OUT/x-stu-send.http"
chk_code "学生越权下发站内信被拒" "$OUT/x-stu-send.json" "$OUT/x-stu-send.http" 403
echo "--- 5a-对照) 管理员调 POST /inboxes 必须放行（证明拒绝来自角色校验而非接口不可用）"
curl -s -o "$OUT/x-adm-send.json" -w '%{http_code}' -X POST "$GW/inboxes" -H "$AA" \
  -H 'Content-Type: application/json' -d '{"content":"定向回归探针"}' > "$OUT/x-adm-send.http"
chk_code "管理员下发站内信放行" "$OUT/x-adm-send.json" "$OUT/x-adm-send.http" 200

echo "--- 5b) 教师调 GET /user-coupons/page（@RequireRole(STUDENT)）"
curl -s -o "$OUT/x-tea-coupon.json" -w '%{http_code}' "$GW/user-coupons/page?pageNo=1&pageSize=5" -H "$AT" > "$OUT/x-tea-coupon.http"
chk_code "非学生访问券分页被拒" "$OUT/x-tea-coupon.json" "$OUT/x-tea-coupon.http" 403
echo "--- 5b-对照) 学生调同一端点必须放行"
curl -s -o "$OUT/x-stu-coupon.json" -w '%{http_code}' "$GW/user-coupons/page?pageNo=1&pageSize=5" -H "$AS" > "$OUT/x-stu-coupon.http"
chk_code "学生访问券分页放行" "$OUT/x-stu-coupon.json" "$OUT/x-stu-coupon.http" 200
echo "--- 5c) 匿名访问 /inboxes（网关鉴权基线，这里才是真实 HTTP 401）"
H=$(curl -s -o /dev/null -w '%{http_code}' "$GW/inboxes")
chk_http "匿名访问被网关拒绝" 401 "$H"
echo "--- 5d) 伪造身份头（网关必须剥离客户端伪造的 user-info/role-info）"
H=$(curl -s -o /dev/null -w '%{http_code}' "$GW/inboxes" -H 'user-info: 1' -H 'role-info: 3')
chk_http "无 token 仅伪造身份头仍被拒" 401 "$H"

echo "---------------------------------------------------------------------"
echo " 6) 收件箱按用户隔离（带对照组，避免只验 HTTP 200 的弱断言）"
# 负向：管理员给 userId=999999999 定向投递 → 该消息绝不能出现在其他人的收件箱
curl -s -o /dev/null -X POST "$GW/inboxes" -H "$AA" -H 'Content-Type: application/json' \
  -d '{"content":"ISOLATION_PROBE_OTHER_USER","title":"t","userId":999999999}'
# 正向对照：管理员全员广播 → 学生必须能看到（证明上面的"看不到"不是因为收件箱整体为空）
curl -s -o /dev/null -X POST "$GW/inboxes" -H "$AA" -H 'Content-Type: application/json' \
  -d '{"content":"BROADCAST_PROBE_VISIBLE_TO_ALL","title":"t"}'
curl -s -o "$OUT/inboxes-isolation.json" "$GW/inboxes" -H "$AS"
ISO=$("$PY" -c "
import json
d=json.load(open(r'$OUT/inboxes-isolation.json',encoding='utf-8'))
txt=json.dumps(d, ensure_ascii=False)
other='ISOLATION_PROBE_OTHER_USER' in txt
bcast='BROADCAST_PROBE_VISIBLE_TO_ALL' in txt
print('LEAK' if other else ('OK' if bcast else 'NO_CONTROL'))
" 2>/dev/null)
case "$ISO" in
  OK)         ok "学生看不到他人定向消息，且能看到全员广播（对照组成立）" ;;
  LEAK)       bad "学生收件箱泄漏了定向给其他用户的消息" ;;
  NO_CONTROL) bad "对照组失效：连全员广播都看不到，隔离断言不可信" ;;
  *)          bad "收件箱隔离断言无法判定（$ISO）" ;;
esac
curl -s -o "$OUT/inboxes-tea.json" "$GW/inboxes" -H "$AT"
ISO2=$("$PY" -c "
import json
d=json.load(open(r'$OUT/inboxes-tea.json',encoding='utf-8'))
print('LEAK' if 'ISOLATION_PROBE_OTHER_USER' in json.dumps(d, ensure_ascii=False) else 'OK')
" 2>/dev/null)
[ "$ISO2" = "OK" ] && ok "教师收件箱同样不含他人定向消息" || bad "教师收件箱泄漏他人定向消息"

echo "---------------------------------------------------------------------"
echo " 7) zx-aigc（WebFlux/Netty 栈）角色强制 —— 修复前 @RequireRole 在该服务完全失效"
# 背景：zx-aigc 排除了 spring-boot-starter-web，跑在 Netty 上；公共库的 MvcConfig 带
# @ConditionalOnWebApplication(SERVLET) 条件，Servlet 拦截器回调在响应式栈根本不执行，
# 且 WebFlux 没有 HandlerInterceptor 机制 → 注解形同虚设（学员实测拿到业务码 200 + 数据）。
# 修复：新增 RoleGuardWebFilter（WebFilter，注解为主 + /admin/** 路径兜底）。
KNOW='{"query":"测试","topK":1}'
echo "--- 7a) 学员访问管理端知识库检索（修复前 = body.code 200 且拿到数据）"
curl -s -o "$OUT/x-stu-know.json" -w '%{http_code}' -X POST "$GW/admin/knowledge/search" -H "$AS" \
  -H 'Content-Type: application/json' -d "$KNOW" > "$OUT/x-stu-know.http"
chk_code "学员检索内部知识库被拒" "$OUT/x-stu-know.json" "$OUT/x-stu-know.http" 403
echo "--- 7b) 学员访问知识库入库（写操作，数据完整性风险）"
curl -s -o "$OUT/x-stu-up.json" -w '%{http_code}' -X POST "$GW/admin/knowledge/upload" -H "$AS" \
  -H 'Content-Type: application/json' -d '{"title":"probe","content":"probe"}' > "$OUT/x-stu-up.http"
chk_code "学员写入知识库被拒" "$OUT/x-stu-up.json" "$OUT/x-stu-up.http" 403
echo "--- 7c) 员工访问同一端点必须放行（证明拒绝来自角色校验而非接口不可用）"
curl -s -o "$OUT/x-adm-know.json" -w '%{http_code}' -X POST "$GW/admin/knowledge/search" -H "$AA" \
  -H 'Content-Type: application/json' -d "$KNOW" > "$OUT/x-adm-know.http"
chk_code "员工检索内部知识库放行" "$OUT/x-adm-know.json" "$OUT/x-adm-know.http" 200
echo "--- 7d) 对照组：无 @RequireRole 的学员侧端点不得被误伤（/session/hot）"
H=$(curl -s -o "$OUT/x-hot.json" -w '%{http_code}' "$GW/session/hot" -H "$AS")
chk_http "学员侧 /session/hot 未被角色过滤器误伤" 200 "$H"
echo "--- 7e) 现状确认（待产品决策）：/embedding/search/all 无角色注解，登录即可访问"
curl -s -o "$OUT/x-emb.json" -w '%{http_code}' "$GW/embedding/search/all?text=probe" -H "$AS" > "$OUT/x-emb.http"
chk_code "登录用户访问 /embedding/search/all 现状为放行" "$OUT/x-emb.json" "$OUT/x-emb.http" 200

echo "====================================================================="
echo "断言总数: $((PASS + FAIL))   通过: $PASS   失败: $FAIL"
if [ "$FAIL" = "0" ]; then echo "结论: 全部通过"; else echo "结论: 存在 $FAIL 项失败"; fi
echo "响应样本目录: $OUT"
exit $([ "$FAIL" = "0" ] && echo 0 || echo 1)
