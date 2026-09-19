#!/usr/bin/env bash
# =====================================================================
# 知行智学 —— 题库联动 / 学情可视化 / 管理员订单管理 端到端验证脚本
#
# 前置：
#   1) 已执行 sql/init.sql + sql/2026-09-11-exam-and-admin-module.sql + sql/test-data.sql
#   2) 已启动 zx-auth / zx-user / zx-course / zx-exam / zx-learning / zx-trade / zx-insight / zx-gateway
#
# 用法：bash scripts/e2e-verify.sh
# 说明：全部经网关(http://localhost:8080)调用，验证真实链路而非单服务直连。
# =====================================================================
set -u
GW="${GW:-http://localhost:8080}"
PYTHON="${PYTHON:-python}"
OUT_DIR="${OUT_DIR:-./logs}"
mkdir -p "$OUT_DIR"
PASS=0
FAIL=0

c_green() { printf '\033[32m%s\033[0m' "$1"; }
c_red()   { printf '\033[31m%s\033[0m' "$1"; }

ok()  { PASS=$((PASS+1)); printf "  [%s] %s\n" "$(c_green PASS)" "$1"; }
bad() { FAIL=$((FAIL+1)); printf "  [%s] %s  %s\n" "$(c_red FAIL)" "$1" "${2:-}"; }

# jget <json> <python 表达式，变量名为 d>
jget() {
  JQ_EXPR="$2" "$PYTHON" -c "
import json,sys,os
d={}
try:
    raw=sys.stdin.read().strip()
    if raw:
        obj=json.loads(raw)
        if isinstance(obj,dict): d=obj
except Exception:
    d={}
try:
    ns={'__builtins__':{},'d':d,'len':len,'any':any,'all':all,'int':int,'str':str,'round':round,'next':next,'iter':iter,'qid':int(os.environ.get('QID') or 0)}
    print(eval(os.environ['JQ_EXPR'], ns))
except Exception:
    print('')
" <<<"$1"
}

api() { # api <method> <path> <token> [json-body]
  local m="$1" p="$2" t="${3:-}" b="${4:-}"
  local args=(-s -X "$m" "$GW$p" -H 'Content-Type: application/json')
  [ -n "$t" ] && args+=(-H "Authorization: Bearer $t")
  [ -n "$b" ] && args+=(-d "$b")
  curl "${args[@]}"
}

echo "=============================================================="
echo " 知行智学 E2E 验证  网关: $GW"
echo "=============================================================="

# ---------------------------------------------------------------- 0. 健康
echo
echo "【0】网关与认证服务连通性"
code=$(curl -s -o /dev/null -w '%{http_code}' "$GW/accounts/login" -X POST -H 'Content-Type: application/json' -d '{}')
if [ "$code" != "000" ]; then ok "网关可达 (HTTP $code)"; else bad "网关不可达" "请先启动服务"; echo "中止"; exit 1; fi

# ---------------------------------------------------------------- 1. 登录
echo
echo "【1】三角色登录（认证服务）"
login() { api POST /accounts/login "" "{\"cellPhone\":\"$1\",\"password\":\"123456\"}"; }

R_T=$(login 13900000002); T_TEACHER=$(jget "$R_T" "d.get('data',{}).get('accessToken','') if d.get('data') else ''")
R_S=$(login 13900000001); T_STUDENT=$(jget "$R_S" "d.get('data',{}).get('accessToken','') if d.get('data') else ''")
R_A=$(login 13800000001); T_ADMIN=$(jget "$R_A" "d.get('data',{}).get('accessToken','') if d.get('data') else ''")
UID_STUDENT=$(jget "$R_S" "d.get('data',{}).get('userId','') if d.get('data') else ''")
UID_TEACHER=$(jget "$R_T" "d.get('data',{}).get('userId','') if d.get('data') else ''")
USER_ID_ADMIN=$(jget "$R_A" "d.get('data',{}).get('userId','') if d.get('data') else ''")

[ -n "$T_TEACHER" ] && ok "教师登录成功 (userId=$UID_TEACHER)" || bad "教师登录失败" "$R_T"
[ -n "$T_STUDENT" ] && ok "学员登录成功 (userId=$UID_STUDENT)" || bad "学员登录失败" "$R_S"
[ -n "$T_ADMIN" ]   && ok "管理员登录成功"                    || bad "管理员登录失败" "$R_A"

# ---------------------------------------------------------------- 2. 题库联动
echo
echo "【2】题库联动：教师发布 → 学员接收"
Q_ALL=$(api GET /questions/all "$T_TEACHER")
N_ALL=$(jget "$Q_ALL" "len(d.get('data') or [])")
[ "${N_ALL:-0}" -gt 0 ] 2>/dev/null && ok "教师可见题库 $N_ALL 题" || bad "教师题库为空" "$Q_ALL"

HAS_COURSE=$(jget "$Q_ALL" "'Y' if any(q.get('courseId') for q in (d.get('data') or [])) else 'N'")
[ "$HAS_COURSE" = "Y" ] && ok "题目已关联课程（师生联动锚点）" || bad "题目缺少 courseId"
COURSE_ID=$(jget "$Q_ALL" "next((q.get('courseId') for q in (d.get('data') or []) if q.get('courseId')), '')")

# 新增一道草稿题
NEW_BODY="{\"name\":\"[E2E] Spring 事务传播行为\",\"type\":1,\"options\":[\"REQUIRED\",\"REQUIRES_NEW\",\"NESTED\",\"SUPPORTS\"],\"answer\":\"A\",\"difficulty\":3,\"score\":10,\"analysis\":\"默认 REQUIRED，加入当前事务。\",\"courseId\":${COURSE_ID:-null},\"status\":0}"
R_NEW=$(api POST /questions "$T_TEACHER" "$NEW_BODY")
QID=$(jget "$R_NEW" "d.get('data','') if d.get('data') is not None else ''")
[ -n "$QID" ] && ok "教师新增题目(id=$QID, 草稿)" || bad "新增题目失败" "$R_NEW"

if [ -n "$QID" ]; then
  # 学员此时不应看到草稿
  S_LIST_BEFORE=$(api GET /questions/list "$T_STUDENT")
  BEFORE_CODE=$(jget "$S_LIST_BEFORE" "d.get('code','')")
  if [ "$BEFORE_CODE" != "200" ]; then
    bad "学员题库列表接口不可用" "code=$BEFORE_CODE $S_LIST_BEFORE"
  else
    SEEN_BEFORE=$(QID="$QID" jget "$S_LIST_BEFORE" "'Y' if any(int(q.get('id',0))==qid for q in (d.get('data') or [])) else 'N'")
    [ "$SEEN_BEFORE" = "N" ] && ok "草稿题对学员不可见（发布前）" || bad "草稿题泄漏给学员" "$S_LIST_BEFORE"
  fi

  # 发布
  R_PUB=$(api PUT "/questions/$QID/publish?published=true" "$T_TEACHER")
  PUB_OK=$(jget "$R_PUB" "'Y' if d.get('code')==200 else 'N'")
  [ "$PUB_OK" = "Y" ] && ok "教师发布题目成功" || bad "发布题目失败" "$R_PUB"

  # 学员此时应能看到
  S_LIST=$(api GET /questions/list "$T_STUDENT")
  S_N=$(jget "$S_LIST" "len(d.get('data') or [])")
  SEEN_AFTER=$(QID="$QID" jget "$S_LIST" "'Y' if any(int(q.get('id',0))==qid for q in (d.get('data') or [])) else 'N'")
  [ "$SEEN_AFTER" = "Y" ] && ok "学员已接收到发布题目（共 $S_N 题）" || bad "学员未收到发布题目" "$S_LIST"

  # 学员交卷（服务端判分）
  SUB_BODY="[{\"questionId\":$QID,\"userAnswer\":\"A\"},{\"questionId\":$QID,\"userAnswer\":\"B\"}]"
  R_SUB=$(api POST /question-results "$T_STUDENT" "$SUB_BODY")
  FIRST_CORRECT=$(jget "$R_SUB" "str((d.get('data') or [{}])[0].get('correct'))")
  FIRST_ANS=$(jget "$R_SUB" "str((d.get('data') or [{}])[0].get('correctAnswer'))")
  S_N2=$(jget "$R_SUB" "len(d.get('data') or [])")
  [ "$S_N2" = "2" ] && ok "学员交卷成功，返回逐题判分 $S_N2 条" || bad "交卷失败" "$R_SUB"
  [ "$FIRST_CORRECT" = "True" ] && [ "$FIRST_ANS" = "A" ] && ok "服务端判分正确（答 A 判对，正确答案 A）" || bad "判分异常" "correct=$FIRST_CORRECT answer=$FIRST_ANS"
else
  bad "跳过题库发布/交卷链路" "题目创建失败"
fi

# 错题本
R_WRONG=$(api GET /question-results/mine/wrong "$T_STUDENT")
W_N=$(jget "$R_WRONG" "len(d.get('data') or [])")
[ "${W_N:-0}" -ge 1 ] 2>/dev/null && ok "学员错题本 $W_N 条（含题干/我的作答/正确答案）" || bad "错题本为空" "$R_WRONG"

# 教师端答题正确率总览
R_OV=$(api GET /question-results/teacher/overview "$T_TEACHER")
OV_TOTAL=$(jget "$R_OV" "d.get('data',{}).get('totalRecords','') if d.get('data') else ''")
[ -n "$OV_TOTAL" ] && ok "教师端答题总览可用（总记录 $OV_TOTAL）" || bad "教师端答题总览失败" "$R_OV"

# ---------------------------------------------------------------- 3. 学情可视化数据
echo
echo "【3】学情可视化：图表数据源接口"
R_PROF=$(api GET /insight/profiles/mine "$T_STUDENT")
AB_N=$(jget "$R_PROF" "len((d.get('data') or {}).get('abilities') or [])")
[ "${AB_N:-0}" -ge 1 ] 2>/dev/null && ok "学员能力画像 $AB_N 个维度（雷达/柱状图数据源）" || bad "学员画像无能力维度" "$R_PROF"

TR_N=$(jget "$R_PROF" "len((d.get('data') or {}).get('trends') or [])")
[ "${TR_N:-0}" -ge 1 ] 2>/dev/null && ok "学员学习趋势 $TR_N 个数据点（折线图数据源）" || bad "学员趋势无数据" "$R_PROF"

R_TP=$(api GET /insight/teacher/students/2101 "$T_TEACHER")
TP_N=$(jget "$R_TP" "len((d.get('data') or {}).get('abilities') or [])")
[ "${TP_N:-0}" -ge 1 ] 2>/dev/null && ok "教师查看学员画像可用（$TP_N 维度）" || bad "教师查学员画像失败" "$R_TP"

# ---------------------------------------------------------------- 4. 管理员订单
echo
echo "【4】管理员端订单管理"
R_PAGE=$(api GET "/orders/admin/page?pageNo=1&pageSize=5" "$T_ADMIN")
P_TOTAL=$(jget "$R_PAGE" "(d.get('data') or {}).get('total','')")
P_N=$(jget "$R_PAGE" "len((d.get('data') or {}).get('list') or [])")
[ "${P_TOTAL:-0}" -gt 0 ] 2>/dev/null && ok "订单分页可用（共 $P_TOTAL 单，本页 $P_N 条）" || bad "订单分页失败" "$R_PAGE"

R_SEARCH=$(api GET "/orders/admin/page?pageNo=1&pageSize=5&status=0" "$T_ADMIN")
S_TOTAL=$(jget "$R_SEARCH" "(d.get('data') or {}).get('total','')")
[ -n "$S_TOTAL" ] && ok "按状态筛选可用（待支付 $S_TOTAL 单）" || bad "状态筛选失败" "$R_SEARCH"

R_STAT=$(api GET /orders/admin/statistics "$T_ADMIN")
ST_ALL=$(jget "$R_STAT" "(d.get('data') or {}).get('totalCount','')")
ST_SALES=$(jget "$R_STAT" "(d.get('data') or {}).get('totalSales',0)")
[ -n "$ST_ALL" ] && ok "订单统计可用（$ST_ALL 单 / 销售额 ¥$("$PYTHON" -c "print(round(${ST_SALES:-0}/100,2))")）" || bad "订单统计失败" "$R_STAT"

R_REF=$(api GET "/orders/admin/refunds?pageNo=1&pageSize=5" "$T_ADMIN")
REF_N=$(jget "$R_REF" "len((d.get('data') or {}).get('list') or [])")
[ "${REF_N:-0}" -ge 1 ] 2>/dev/null && ok "退款申请列表可用（$REF_N 条）" || bad "退款列表失败" "$R_REF"

# 导出
EXP_FILE="$OUT_DIR/zx-orders-export.csv"
EXP_CODE=$(curl -s -o "$EXP_FILE" -w '%{http_code}' "$GW/orders/admin/export?pageSize=50" -H "Authorization: Bearer $T_ADMIN")
EXP_SIZE=$(wc -c < "$EXP_FILE" 2>/dev/null | tr -d ' ')
if [ "$EXP_CODE" = "200" ] && [ "${EXP_SIZE:-0}" -gt 100 ]; then ok "订单导出可用（CSV $EXP_SIZE 字节）"; else bad "订单导出失败" "HTTP $EXP_CODE size=$EXP_SIZE"; fi

# 修改订单状态（取第一条待支付订单）
OID=$(jget "$R_SEARCH" "next((o.get('id') for o in ((d.get('data') or {}).get('list') or [])), '')")
if [ -n "$OID" ]; then
  R_UPD=$(api PUT "/orders/admin/$OID/status?status=1" "$T_ADMIN")
  UPD_OK=$(jget "$R_UPD" "'Y' if d.get('code')==200 else 'N'")
  [ "$UPD_OK" = "Y" ] && ok "管理员修改订单状态可用（#$OID 待支付 → 已支付）" || bad "改状态失败" "$R_UPD"
else
  bad "未取到待支付订单" "跳过改状态验证"
fi

# 退款审核（若有待审核）
RID=$(jget "$R_REF" "next((r.get('id') for r in ((d.get('data') or {}).get('list') or []) if r.get('status')==0), '')")
if [ -n "$RID" ]; then
  R_AUD=$(api PUT "/orders/admin/refund/audit" "$T_ADMIN" "{\"refundId\":$RID,\"approved\":true,\"remark\":\"E2E 审核通过\"}")
  AUD_OK=$(jget "$R_AUD" "'Y' if d.get('code')==200 else 'N'")
  [ "$AUD_OK" = "Y" ] && ok "退款审核可用（#$RID 通过）" || bad "退款审核失败" "$R_AUD"
fi

# ---------------------------------------------------------------- 4.5 本轮新增能力
echo
echo "【4.5】购物车下单 / 立即支付 / 退款分级 / 管理员新能力"

# --- 雪花 id 精度（旧缺陷：新订单无法支付 / 领券报「优惠券不存在」）---
R_ORD=$(api GET "/orders/page?pageNo=1&pageSize=5" "$T_STUDENT")
OID0=$(jget "$R_ORD" "next((o.get('id') for o in ((d.get('data') or {}).get('list') or [])), '')")
ONO0=$(jget "$R_ORD" "next((o.get('orderNo') for o in ((d.get('data') or {}).get('list') or [])), '')")
if [ "${#OID0}" -ge 17 ]; then
  ok "订单 id 以字符串下发（长度 ${#OID0}），规避 JS 安全整数精度丢失"
else
  bad "订单 id 未做精度保护" "id=$OID0"
fi
R_ONE=$(api GET "/orders/$OID0" "$T_STUDENT")
[ "$(jget "$R_ONE" "d.get('data',{}).get('orderNo','')")" = "$ONO0" ] \
  && ok "订单 id 原样回查命中（$ONO0）" || bad "订单 id 回查失败（精度丢失）" "$R_ONE"

# --- 雪花 id 优惠券可领取（联调满减券）---
SNOW_COUPON=2095442582518628353
R_CLAIM=$(api POST /user-coupons/claim "$T_STUDENT" "{\"couponId\":\"$SNOW_COUPON\"}")
CL_CODE=$(jget "$R_CLAIM" "d.get('code','')")
if [ "$CL_CODE" = "200" ]; then
  ok "雪花 id 优惠券领取成功（$SNOW_COUPON）"
else
  CL_MSG=$(jget "$R_CLAIM" "d.get('msg','')")
  # 重复执行脚本时券已领取，按幂等处理
  case "$CL_MSG" in *已领取*) ok "雪花 id 优惠券已领取（幂等）" ;; *) bad "雪花 id 优惠券领取失败" "$R_CLAIM" ;; esac
fi
R_MINE=$(api GET /user-coupons "$T_STUDENT")
MINE_CN=$(jget "$R_MINE" "sum(1 for r in (d.get('data') or []) if str(r.get('couponId'))=='$SNOW_COUPON' and r.get('status')==1)")
[ "${MINE_CN:-0}" -ge 1 ] 2>/dev/null \
  && ok "我的优惠券含「已领取且未使用」券（前端据此显示「去使用」）" || bad "我的券状态异常" "$R_MINE"

# --- 购物车加购（支持单独购买 / 多选下单）---
api DELETE /carts "$T_STUDENT" > /dev/null
api POST /carts "$T_STUDENT" '{"courseId":3012}' > /dev/null
api POST /carts "$T_STUDENT" '{"courseId":3001}' > /dev/null
R_CART=$(api GET /carts "$T_STUDENT")
CART_N=$(jget "$R_CART" "len(d.get('data') or [])")
[ "${CART_N:-0}" -ge 2 ] 2>/dev/null && ok "购物车加购可用（$CART_N 门课程）" || bad "购物车加购失败" "$R_CART"

# --- 下单 → 立即支付 → 未学习秒退 ---
R_NEW=$(api POST /orders/placeOrder "$T_STUDENT" '{"courseId":3012,"totalFee":42900}')
NEW_ORDER=$(jget "$R_NEW" "d.get('data','')")
if [ "${#NEW_ORDER}" -ge 17 ]; then ok "下单成功并返回雪花订单 id（字符串）"; else bad "下单失败" "$R_NEW"; fi
R_PAY=$(api POST "/orders/pay/mock/$NEW_ORDER" "$T_STUDENT")
[ "$(jget "$R_PAY" "d.get('code','')")" = "200" ] && ok "新订单支付成功（修复前：新订单无法支付）" || bad "新订单支付失败" "$R_PAY"
R_RF=$(api POST "/orders/$NEW_ORDER/refund" "$T_STUDENT" '{"reason":"E2E 未学习自动退款"}')
RF_MODE=$(jget "$R_RF" "d.get('data',{}).get('mode','')")
[ "$RF_MODE" = "INSTANT" ] && ok "未开始学习 → 直接退款成功（mode=INSTANT）" || bad "未走自动退款" "$R_RF"
R_AF=$(api GET "/orders/$NEW_ORDER" "$T_STUDENT")
[ "$(jget "$R_AF" "d.get('data',{}).get('status','')")" = "6" ] && ok "秒退后订单状态=已退款(6)" || bad "秒退状态异常" "$R_AF"

# --- 下单 → 支付 → 已学习转人工审核 → 管理员审批 ---
R_NEW2=$(api POST /orders/placeOrder "$T_STUDENT" '{"courseId":3001,"totalFee":19900}')
AUDIT_ORDER=$(jget "$R_NEW2" "d.get('data','')")
api POST "/orders/pay/mock/$AUDIT_ORDER" "$T_STUDENT" > /dev/null
R_RF2=$(api POST "/orders/$AUDIT_ORDER/refund" "$T_STUDENT" '{"reason":"E2E 已学习转审核"}')
RF2_MODE=$(jget "$R_RF2" "d.get('data',{}).get('mode','')")
RF2_ID=$(jget "$R_RF2" "d.get('data',{}).get('refundId','')")
[ "$RF2_MODE" = "AUDIT" ] && ok "已学习订单 → 生成待审核退款单（refundId=$RF2_ID）" || bad "未转人工审核" "$R_RF2"
if [ -n "$RF2_ID" ]; then
  R_AUD2=$(api PUT /orders/admin/refund/audit "$T_ADMIN" "{\"refundId\":\"$RF2_ID\",\"approved\":true,\"remark\":\"E2E 审核通过\"}")
  [ "$(jget "$R_AUD2" "d.get('code','')")" = "200" ] && ok "管理员人工审批通过" || bad "退款审批失败" "$R_AUD2"
  R_AF2=$(api GET "/orders/$AUDIT_ORDER" "$T_STUDENT")
  [ "$(jget "$R_AF2" "d.get('data',{}).get('status','')")" = "6" ] && ok "审批通过后订单=已退款(6)" || bad "审批后状态异常" "$R_AF2"
fi

# --- 管理员查看学员相关课程（退款审批辅助）---
R_UC=$(api GET "/orders/admin/users/2001/courses" "$T_ADMIN")
UC_CODE=$(jget "$R_UC" "d.get('code','')")
UC_N=$(jget "$R_UC" "len((d.get('data') or {}).get('courses') or [])")
UC_DUR=$(jget "$R_UC" "next((c.get('learnDuration') for c in ((d.get('data') or {}).get('courses') or []) if str(c.get('courseId'))=='3001'), '')")
if [ "$UC_CODE" = "200" ] && [ "${UC_N:-0}" -ge 1 ] 2>/dev/null; then
  ok "学员课程视图可用（学员 2001 共 $UC_N 条订单课程，含学习进度/时长）"
  [ -n "$UC_DUR" ] && ok "课程 3001 学习时长可见（${UC_DUR}s），支撑退款人工审批" || bad "学习时长缺失" "$R_UC"
else
  bad "学员课程视图失败" "$R_UC"
fi
R_UC2=$(api GET "/orders/admin/users/2001/courses" "$T_STUDENT")
C_UC=$(jget "$R_UC2" "d.get('code','')")
if [ "$C_UC" = "403" ] || [ "$C_UC" = "401" ]; then ok "学员访问学员课程视图 → 拒绝($C_UC)"; else bad "越权未拦截" "code=$C_UC"; fi

# --- 管理员删除用户 ---
NEW_PHONE="139$(date +%H%M%S)00"
R_ADD=$(api POST /users "$T_ADMIN" "{\"cellPhone\":\"$NEW_PHONE\",\"username\":\"e2e_del_$NEW_PHONE\",\"password\":\"123456\",\"type\":2}")
if [ "$(jget "$R_ADD" "d.get('code','')")" = "200" ]; then
  # 通过用户分页反查新建用户 id（按 id 倒序，新用户在最前）
  R_UP=$(api GET "/users/page?pageNo=1&pageSize=200" "$T_ADMIN")
  TMP_UID=$(PHONE="$NEW_PHONE" "$PYTHON" -c "
import json,os,sys
raw=sys.stdin.read().strip()
try: o=json.loads(raw)
except Exception: o={}
rows=(o.get('data') or {}).get('list') or []
print(next((str(u.get('id')) for u in rows if u.get('cellPhone')==os.environ['PHONE']), ''))
" <<< "$R_UP")
  if [ -n "$TMP_UID" ]; then
    R_DEL=$(api DELETE "/users/$TMP_UID" "$T_ADMIN")
    [ "$(jget "$R_DEL" "d.get('code','')")" = "200" ] && ok "删除用户可用（id=$TMP_UID）" || bad "删除用户失败" "$R_DEL"
    R_GONE=$(api GET "/users/$TMP_UID" "$T_ADMIN")
    [ "$(jget "$R_GONE" "d.get('code','')")" != "200" ] && ok "被删用户已不可查询（逻辑删除生效）" || bad "删除未生效" "$R_GONE"
  else
    bad "未取到新建用户 id" "$R_ADD"
  fi
else
  bad "创建临时用户失败（跳过删除验证）" "$R_ADD"
fi
R_SELF=$(api DELETE "/users/$USER_ID_ADMIN" "$T_ADMIN")
if [ "$(jget "$R_SELF" "d.get('code','')")" != "200" ]; then
  ok "禁止删除当前登录账号：$(jget "$R_SELF" "d.get('msg','')")"
else
  bad "删除本人未被拦截" "$R_SELF"
fi
R_DELBYSTU=$(api DELETE "/users/2001" "$T_STUDENT")
C_DBSTU=$(jget "$R_DELBYSTU" "d.get('code','')")
if [ "$C_DBSTU" = "403" ] || [ "$C_DBSTU" = "401" ]; then ok "学员调用删除用户接口 → 拒绝($C_DBSTU)"; else bad "删除接口越权未拦截" "code=$C_DBSTU"; fi

# ---------------------------------------------------------------- 5. 权限隔离
echo
echo "【5】权限隔离（越权必须被拒）"
# 说明：本项目统一异常处理对业务异常返回 HTTP 200 + body.code（403/401），
#       网关 JWT 校验失败才返回真实 HTTP 401。故需同时看 HTTP 状态与 body.code。
deny_code() { # deny_code <token> <path>
  local resp http body bcode
  if [ -n "${1:-}" ]; then
    resp=$(curl -s -w '
%{http_code}' "$GW$2" -H "Authorization: Bearer $1")
  else
    resp=$(curl -s -w '
%{http_code}' "$GW$2")
  fi
  http=$(printf '%s' "$resp" | tail -n1)
  body=$(printf '%s' "$resp" | sed '$d')
  bcode=$(jget "$body" "d.get('code','')")
  if [ "$http" = "401" ] || [ "$http" = "403" ]; then echo "$http"; else echo "$bcode"; fi
}

C1=$(deny_code "$T_STUDENT" "/orders/admin/page?pageNo=1&pageSize=1")
if [ "$C1" = "403" ] || [ "$C1" = "401" ]; then ok "学员访问管理员订单接口 → 拒绝($C1)"; else bad "越权未拦截" "code=$C1"; fi

C2=$(deny_code "$T_STUDENT" "/questions/all")
if [ "$C2" = "403" ] || [ "$C2" = "401" ]; then ok "学员访问教师题库全量接口 → 拒绝($C2)"; else bad "越权未拦截" "code=$C2"; fi

C3=$(deny_code "" "/orders/admin/page?pageNo=1&pageSize=1")
if [ "$C3" = "401" ] || [ "$C3" = "403" ]; then ok "未登录访问管理员接口 → 拒绝($C3)"; else bad "匿名访问未拦截" "code=$C3"; fi

C4=$(deny_code "$T_STUDENT" "/question-results/teacher/overview")
if [ "$C4" = "403" ] || [ "$C4" = "401" ]; then ok "学员访问教师答题总览 → 拒绝($C4)"; else bad "越权未拦截" "code=$C4"; fi

C5=$(deny_code "$T_TEACHER" "/orders/admin/page?pageNo=1&pageSize=1")
if [ "$C5" = "403" ] || [ "$C5" = "401" ]; then ok "教师访问管理员订单接口 → 拒绝($C5)"; else bad "越权未拦截" "code=$C5"; fi

C6=$(deny_code "$T_ADMIN" "/questions/all")
if [ "$C6" = "403" ] || [ "$C6" = "401" ]; then ok "管理员访问教师专属题库接口 → 拒绝($C6)"; else bad "越权未拦截" "code=$C6"; fi

# ---------------------------------------------------------------- 6. 清理
echo
echo "【6】清理本次验证产生的临时数据"
if [ -n "${QID:-}" ]; then
  api DELETE "/questions/$QID" "$T_TEACHER" > /dev/null
  ok "已删除本次新增的测试题目(id=$QID)"
fi
# 购物车清空；订单不提供删除接口，本轮下单的 2 笔订单（3001/3012）已自动退款或审批通过，
# 保留为「已退款」历史记录，便于在管理端复核退款链路。
api DELETE /carts "$T_STUDENT" > /dev/null
ok "已清空本次验证使用的购物车"

# ---------------------------------------------------------------- 汇总
echo
echo "=============================================================="
printf " 结果：通过 %s 项，失败 %s 项\n" "$(c_green $PASS)" "$([ $FAIL -gt 0 ] && c_red $FAIL || echo 0)"
echo "=============================================================="
[ "$FAIL" -eq 0 ] && exit 0 || exit 1
