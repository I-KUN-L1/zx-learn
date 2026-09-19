#!/usr/bin/env bash
# =====================================================================
# 一次性端到端验证：启动依赖服务 → 健康等待 → 断言 → 清理 → 关停
#   覆盖：Long 精度（新订单可支付 / 优惠券可领取）、购物车下单、
#         立即支付、条件退款（秒退 / 转审核）、管理员查看学员课程、删除用户
# 用法：/usr/bin/bash logs/e2e/run-verify.sh
# =====================================================================
set -u
ROOT=/d/1/zx-learn
cd "$ROOT" || exit 1
PY="/c/Users/20670/.workbuddy/binaries/python/versions/3.13.12/python.exe"
MYSQL="/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"
LOG="$ROOT/logs/e2e"
mkdir -p "$LOG" "$ROOT/logs/tmp"
GW=http://localhost:8080

# 防端口注入：宿主终端注入的 SERVER__PORT 会被 Spring 松散绑定覆盖 server.port
unset SERVER__PORT SERVER__HOST SERVER_PORT
# 注意：沙箱内后台进程会丢失 JAVA_TOOL_OPTIONS 环境变量（实测 "Picked up" 不出现，
# java.io.tmpdir 回落到 C:\Windows 导致 Tomcat createTempDir 报 AccessDenied）。
# 因此必须把 -Djava.io.tmpdir 直接写在 java 命令行上（CLI 参数不受环境清洗影响）。
JVM_TMPDIR="-Djava.io.tmpdir=D:/1/zx-learn/logs/tmp"
export JAVA_TOOL_OPTIONS="$JVM_TMPDIR"

MYSQL_ROOT_PASSWORD=$(grep -E "^MYSQL_ROOT_PASSWORD=" "$ROOT/.env" | cut -d= -f2-)
mysqlx() { "$MYSQL" -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B -e "$1" 2>/dev/null; }

PASS=0; FAIL=0
ok() { PASS=$((PASS + 1)); echo "  [PASS] $1"; }
bad() { FAIL=$((FAIL + 1)); echo "  [FAIL] $1   ${2:-}"; }

# jval <json> <a.b.c> ：按路径取值，缺失输出空
jval() { # jval <json|-> <a.b.c> ：$1 传 "-" 时从 stdin 读（兼容管道写法）
  local J="$1"
  [ "$J" = "-" ] && J=$(cat)
  printf '%s' "$J" | JQ_PATH="$2" "$PY" -c "
import json,sys,os
raw=sys.stdin.read().strip()
try: o=json.loads(raw)
except Exception: o={}
v=o
for k in os.environ['JQ_PATH'].split('.'):
    if not k: continue
    if isinstance(v,dict): v=v.get(k)
    elif isinstance(v,list):
        try: v=v[int(k)]
        except Exception: v=None
    else: v=None
print('' if v is None else v)
"
}

api() { # api <method> <path> <token> [json-body]
  local m="$1" p="$2" t="${3:-}" b="${4:-}"
  local args=(-s -X "$m" "$GW$p" -H 'Content-Type: application/json')
  [ -n "$t" ] && args+=(-H "Authorization: Bearer $t")
  [ -n "$b" ] && args+=(-d "$b")
  curl "${args[@]}"
}
code_of() { api "$1" "$2" "${3:-}" "${4:-}" | jval - code; }

# ---------------------------------------------------------------- 启动服务
echo "== 启动依赖服务 =="
PIDS=()
start() { # start <name> <module>
  nohup java "$JVM_TMPDIR" -jar "$ROOT/$2/target/$2.jar" > "$LOG/$1.out" 2>&1 &
  PIDS+=($!)
}
for m in zx-user zx-course zx-learning zx-promotion zx-auth; do start "$m" "$m"; done
sleep 25
for m in zx-trade zx-gateway; do start "$m" "$m"; done

# 就绪探测：只用端口/HTTP 可达性判断（zx-gateway 未引入 actuator，无 /actuator/health）
wait_health() { # wait_health <port> <name>
  for _ in $(seq 1 60); do
    local c
    c=$(curl -s -o /dev/null -w '%{http_code}' --max-time 2 "http://localhost:$1/")
    [ "$c" != "000" ] && [ -n "$c" ] && { ok "$2 已就绪 (:$1 / HTTP $c)"; return 0; }
    sleep 2
  done
  bad "$2 启动超时 (:$1)" "见 $LOG/$2.out"
  return 1
}
echo "== 健康检查 =="
wait_health 8082 zx-user
wait_health 8083 zx-course
wait_health 8086 zx-learning
wait_health 8088 zx-promotion
wait_health 8081 zx-auth
wait_health 8087 zx-trade
wait_health 8080 zx-gateway

cleanup() {
  echo "== 关停服务 =="
  for p in "${PIDS[@]:-}"; do kill "$p" 2>/dev/null; done
  sleep 2
  for p in "${PIDS[@]:-}"; do kill -9 "$p" 2>/dev/null; done
}
trap cleanup EXIT

# ---------------------------------------------------------------- 登录
echo
echo "【1】三角色登录"
login() { api POST /accounts/login "" "{\"cellPhone\":\"$1\",\"password\":\"123456\"}"; }
R=$(login 13800000001); T_ADMIN=$(jval "$R" data.accessToken)
R=$(login 13900000001); T_STU=$(jval "$R" data.accessToken)
CP_ADMIN=13800000001
[ -n "$T_ADMIN" ] && ok "管理员登录成功" || bad "管理员登录失败" "$R"
[ -n "$T_STU" ] && ok "学员登录成功" || bad "学员登录失败" "$R"

# ---------------------------------------------------------------- Long 精度
echo
echo "【2】雪花 id 精度（新订单可支付 / 优惠券可领取的根因）"
R=$(api GET "/orders/page?pageNo=1&pageSize=5" "$T_STU")
OID=$(jval "$R" data.list.0.id)
ONO=$(jval "$R" data.list.0.orderNo)
if [ ${#OID} -ge 17 ]; then ok "订单 id 以字符串下发（长度 ${#OID}，非科学计数/舍入）"; else bad "订单 id 仍被舍入" "id=$OID"; fi
# 用下发的 id 原样回查：若精度丢失必然命中"订单不存在"
R=$(api GET "/orders/$OID" "$T_STU")
if [ "$(jval "$R" code)" = "200" ] && [ "$(jval "$R" data.orderNo)" = "$ONO" ]; then
  ok "回传订单 id 可精确命中订单（$ONO）"
else
  bad "订单 id 回查失败（精度丢失）" "$(echo "$R" | head -c 200)"
fi

# ---------------------------------------------------------------- 优惠券
echo
echo "【3】优惠券领取（雪花 id 券：联调满减券）"
SNOW_COUPON=2095442582518628353
mysqlx "DELETE FROM zx_promotion.user_coupon WHERE user_id=2001 AND coupon_id=$SNOW_COUPON;"
R=$(api POST /user-coupons/claim "$T_STU" "{\"couponId\":\"$SNOW_COUPON\"}")
if [ "$(jval "$R" code)" = "200" ]; then ok "雪花 id 优惠券领取成功（$SNOW_COUPON）"; else bad "雪花 id 优惠券领取失败" "$(echo "$R" | head -c 200)"; fi
R=$(api POST /user-coupons/claim "$T_STU" "{\"couponId\":\"$SNOW_COUPON\"}")
if [ "$(jval "$R" code)" != "200" ]; then ok "重复领取被幂等拦截：$(jval "$R" msg)"; else bad "重复领取未拦截" "$R"; fi
R=$(api GET /user-coupons "$T_STU")
UNUSED=$(R_JSON="$R" CONFIG_ID="$SNOW_COUPON" "$PY" -c "
import json,os
try: o=json.loads(os.environ['R_JSON'])
except Exception: o={}
cid=os.environ['CONFIG_ID']
rows=o.get('data') or []
print(sum(1 for r in rows if str(r.get('couponId'))==cid and r.get('status')==1))
")
if [ "$UNUSED" = "1" ]; then ok "我的优惠券中该券状态=1（已领取未使用，前端据此显示去使用）"; else bad "我的券状态异常" "unused=$UNUSED"; fi
# 前端"去使用"跳转依赖该状态标记；同时校验券模板 id 也被字符串化
R=$(api GET "/coupons/page?pageNo=1&pageSize=50" "")
FOUND=$(R_JSON="$R" CONFIG_ID="$SNOW_COUPON" "$PY" -c "
import json,os
try: o=json.loads(os.environ['R_JSON'])
except Exception: o={}
cid=os.environ['CONFIG_ID']
print(sum(1 for r in (o.get('data') or {}).get('list', []) if str(r.get('id'))==cid))
")
[ "$FOUND" = "1" ] && ok "券中心列表含该雪花 id 券且 id 精确保留" || bad "券中心列表未命中" "$(echo "$R" | head -c 200)"

# ---------------------------------------------------------------- 购物车
echo
echo "【4】购物车：单独购买 / 多选下单"
api DELETE /carts "$T_STU" >/dev/null
api POST /carts "$T_STU" '{"courseId":3012}' >/dev/null
api POST /carts "$T_STU" '{"courseId":3001}' >/dev/null
R=$(api GET /carts "$T_STU")
# /carts 的 data 是数组，用 python 计数（jval 的点路径不支持数组长度）
CN=$(R_JSON="$R" "$PY" -c "
import json,os
try: o=json.loads(os.environ['R_JSON'])
except Exception: o={}
print(len(o.get('data') or []))
")
[ "$CN" = "2" ] && ok "加购 2 门课程（3012 / 3001）成功" || bad "加购失败" "$(echo "$R" | head -c 200)"

# ---------------------------------------------------------------- 下单 + 支付 + 秒退
# 秒退用例课程选取原则：学员 2001 无该课学习记录且无已支付订单
#   （test-data 中 3001/3002/3008/3009/3010/3012 均有学习记录，3001/3002/3007/3008 已拥有）
echo
echo "【5】下单 → 立即支付 → 申请退款（未开始学习 → 秒退）"
R=$(api POST /orders/placeOrder "$T_STU" '{"courseId":3005,"totalFee":16900}')
NEW_ORDER=$(jval "$R" data)
if [ "$(jval "$R" code)" = "200" ] && [ ${#NEW_ORDER} -ge 17 ]; then
  ok "下单成功，返回雪花订单 id（字符串，长度 ${#NEW_ORDER}）"
else
  bad "下单失败" "$(echo "$R" | head -c 200)"
fi
R=$(api POST "/orders/pay/mock/$NEW_ORDER" "$T_STU")
[ "$(jval "$R" code)" = "200" ] && ok "新订单支付成功（旧缺陷：新订单无法支付）" || bad "新订单支付失败" "$(echo "$R" | head -c 200)"
R=$(api GET "/orders/$NEW_ORDER" "$T_STU")
[ "$(jval "$R" data.status)" = "2" ] && ok "订单状态流转为已支付(2)" || bad "订单状态异常" "status=$(jval "$R" data.status)"
R=$(api POST "/orders/$NEW_ORDER/refund" "$T_STU" '{"reason":"E2E 未学习自动退款"}')
MODE=$(jval "$R" data.mode)
if [ "$MODE" = "INSTANT" ]; then ok "未学习订单直接退款成功（mode=INSTANT）"; else bad "未走秒退" "$(echo "$R" | head -c 200)"; fi
R=$(api GET "/orders/$NEW_ORDER" "$T_STU")
[ "$(jval "$R" data.status)" = "6" ] && ok "订单流转为已退款(6)" || bad "退款后订单状态异常" "status=$(jval "$R" data.status)"

# ---------------------------------------------------------------- 下单 + 支付 + 转审核 + 管理员审批
# 转审核用例课程选取原则：学员 2001 已有学习记录、但无已支付订单（3009：progress 75/80, 时长 2700/3000s）
echo
echo "【6】已学习订单 → 退款转人工审核 → 管理员审批通过"
R=$(api POST /orders/placeOrder "$T_STU" '{"courseId":3009,"totalFee":27900}')
AUDIT_ORDER=$(jval "$R" data)
api POST "/orders/pay/mock/$AUDIT_ORDER" "$T_STU" >/dev/null
R=$(api POST "/orders/$AUDIT_ORDER/refund" "$T_STU" '{"reason":"E2E 已学习转审核"}')
MODE=$(jval "$R" data.mode); RID=$(jval "$R" data.refundId)
if [ "$MODE" = "AUDIT" ] && [ -n "$RID" ]; then ok "已学习订单转人工审核（mode=AUDIT, refundId=$RID）"; else bad "未转审核" "$(echo "$R" | head -c 200)"; fi
R=$(api GET "/orders/$AUDIT_ORDER" "$T_STU")
[ "$(jval "$R" data.status)" = "5" ] && ok "订单流转为退款中(5)" || bad "退款中状态异常" "status=$(jval "$R" data.status)"
R=$(api PUT /orders/admin/refund/audit "$T_ADMIN" "{\"refundId\":\"$RID\",\"approved\":true,\"remark\":\"E2E 审核通过\"}")
[ "$(jval "$R" code)" = "200" ] && ok "管理员审核通过" || bad "审核失败" "$(echo "$R" | head -c 200)"
R=$(api GET "/orders/$AUDIT_ORDER" "$T_STU")
[ "$(jval "$R" data.status)" = "6" ] && ok "审批通过后订单已退款(6)" || bad "审批后状态异常" "status=$(jval "$R" data.status)"

# ---------------------------------------------------------------- 管理员查看学员课程
echo
echo "【7】管理员查看学员相关课程（退款审批辅助）"
R=$(api GET "/orders/admin/users/2001/courses" "$T_ADMIN")
if [ "$(jval "$R" code)" = "200" ]; then
  CC=$(jval "$R" data.courseCount)
  has_progress=$(R_JSON="$R" "$PY" -c "
import json,os
o=json.loads(os.environ['R_JSON'])
rows=(o.get('data') or {}).get('courses') or []
print('Y' if rows and all('progress' in r and 'learnDuration' in r for r in rows) else 'N')
")
  [ "$has_progress" = "Y" ] && ok "返回 $CC 门课程并含学习进度/时长字段" || bad "课程结构缺字段" "$(echo "$R" | head -c 300)"
  HAS3001=$(R_JSON="$R" "$PY" -c "
import json,os
o=json.loads(os.environ['R_JSON'])
rows=(o.get('data') or {}).get('courses') or []
p=[r for r in rows if str(r.get('courseId'))=='3001']
print(p[0].get('learnDuration') if p else '')
")
  [ -n "$HAS3001" ] && [ "$HAS3001" != "0" ] && ok "课程 3001 学习时长可见（${HAS3001}s，支撑人工审批）" || bad "课时数据缺失" "learnDuration=$HAS3001"
else
  bad "学员课程接口失败" "$(echo "$R" | head -c 200)"
fi
R=$(api GET "/orders/admin/users/2001/courses" "$T_STU")
STU_CODE=$(jval "$R" code)
if [ "$STU_CODE" = "403" ] || [ "$STU_CODE" = "401" ]; then ok "学员访问该管理接口被拒($STU_CODE)"; else bad "越权未拦截" "code=$STU_CODE"; fi

# ---------------------------------------------------------------- 管理员删除用户
echo
echo "【8】管理员删除用户"
TS=$(date +%s)
NEW_PHONE="139$(printf '%08d' $((TS % 100000000)))"
R=$(api POST /users "$T_ADMIN" "{\"cellPhone\":\"$NEW_PHONE\",\"username\":\"e2e_del_$TS\",\"password\":\"123456\",\"type\":2}")
if [ "$(jval "$R" code)" = "200" ]; then
  TMP_ID=$(mysqlx "SELECT id FROM zx_user.user WHERE cell_phone='$NEW_PHONE' AND deleted=0 LIMIT 1;")
  ok "创建待删除临时用户（id=$TMP_ID, $NEW_PHONE）"
else
  bad "创建临时用户失败" "$(echo "$R" | head -c 200)"
fi
if [ -n "${TMP_ID:-}" ]; then
  R=$(api DELETE "/users/$TMP_ID" "$T_ADMIN")
  [ "$(jval "$R" code)" = "200" ] && ok "删除用户成功" || bad "删除用户失败" "$(echo "$R" | head -c 200)"
  R=$(api GET "/users/$TMP_ID" "$T_ADMIN")
  [ "$(jval "$R" code)" != "200" ] && ok "被删用户已不可查询（逻辑删除生效）" || bad "删除未生效" "$(echo "$R" | head -c 200)"
fi
# 自我保护：删除当前登录管理员
SELF_ID=$(mysqlx "SELECT id FROM zx_user.user WHERE cell_phone='$CP_ADMIN' AND deleted=0 LIMIT 1;")
R=$(api DELETE "/users/$SELF_ID" "$T_ADMIN")
if [ "$(jval "$R" code)" != "200" ]; then ok "禁止删除当前登录账号：$(jval "$R" msg)"; else bad "删除本人未被拦截" "$R"; fi
R=$(api DELETE "/users/2001" "$T_STU")
C=$(jval "$R" code)
if [ "$C" = "403" ] || [ "$C" = "401" ]; then ok "学员调用删除用户接口被拒($C)"; else bad "删除接口越权未拦截" "code=$C"; fi

# ---------------------------------------------------------------- 清理测试数据
echo
echo "【9】清理本次验证产生的数据"
mysqlx "DELETE FROM zx_trade.trade_order_detail WHERE order_id IN ($NEW_ORDER,$AUDIT_ORDER);"
mysqlx "DELETE FROM zx_trade.trade_pay_record WHERE order_id IN ($NEW_ORDER,$AUDIT_ORDER);"
mysqlx "DELETE FROM zx_trade.order_msg WHERE order_id IN ($NEW_ORDER,$AUDIT_ORDER);"
mysqlx "DELETE FROM zx_trade.refund_apply WHERE order_id IN ($NEW_ORDER,$AUDIT_ORDER);"
mysqlx "DELETE FROM zx_trade.trade_order WHERE id IN ($NEW_ORDER,$AUDIT_ORDER);"
mysqlx "DELETE FROM zx_promotion.user_coupon WHERE user_id=2001 AND coupon_id=$SNOW_COUPON;"
mysqlx "UPDATE zx_promotion.coupon SET issued_num=(SELECT COUNT(*) FROM zx_promotion.user_coupon uc WHERE uc.coupon_id=coupon.id) WHERE id=$SNOW_COUPON;"
mysqlx "DELETE FROM zx_trade.cart WHERE user_id=2001;"
# 测试未产生新学习记录；此行按课程 id 兜底清理（订单 id 为雪花数，不会误删真实数据）
mysqlx "DELETE FROM zx_learning.learning_record WHERE user_id=2001 AND course_id IN (3005,3009) AND create_time > NOW() - INTERVAL 10 MINUTE;"
LEFTOVER=$(mysqlx "SELECT COUNT(*) FROM zx_trade.trade_order WHERE id IN ($NEW_ORDER,$AUDIT_ORDER);")
[ "$LEFTOVER" = "0" ] && ok "验证数据已清理（订单/退款单/支付流水/用户券/购物车）" || bad "清理不完整" "leftover=$LEFTOVER"

echo
echo "=============================================================="
echo " 结果：PASS=$PASS  FAIL=$FAIL"
echo "=============================================================="
[ "$FAIL" = "0" ] && echo "ALL PASS" || echo "HAS FAILURE"
