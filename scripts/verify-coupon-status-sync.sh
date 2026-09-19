#!/usr/bin/env bash
# =====================================================================
# 知行智学 · 优惠券使用状态同步 端到端验证
# ---------------------------------------------------------------------
# 症状（用户反馈）：
#   在课程界面用券购买时，系统提示"该优惠券已达领取上限"；
#   而优惠券界面里这张券依旧显示"未使用" —— 两端状态不一致。
#
# 根因：
#   用券核销只写入了 zx_trade.coupon_use_record（核销流水），
#   zx_promotion.user_coupon.status 从未被回写。于是：
#     · 券列表读 user_coupon → 永远显示"未使用"（券界面不刷新）；
#     · 再次使用时被 Redis 限用计数（coupon:used:{couponId}:{userId}）拦下，
#       提示语还是误导性的"已达领取上限"（其实是已经用过了）。
#
# 修复：券状态回写三通道（全部幂等）+ 提示语纠正 + 存量数据修复
#   1) 下单 / 关单本地事务提交后同步回写（Feign afterCommit）→ 券列表立即更新；
#   2) MQ 核销流水消费端兜底；
#   3) CouponReconcileJob 定时对账重放。
#
# 验证点：
#   A 下单用券 → 券状态**立即**变为「已使用」（不依赖 MQ）；
#   B 优惠券列表接口（前端读的就是它）同步返回「已使用」；
#   C 重复使用同一张券被拦截，且提示语准确（不再误报"已达领取上限"）；
#   D 订单关单后券状态退回「未使用」，券可再次使用；
#   E 状态回写幂等：重复调用 / 过期订单号 不产生副作用；
#   F 内部回写接口鉴权：外部经网关调用一律 403，无 token 401，服务间放行；
#   G 全库存量一致性：券状态与核销流水漂移数必须为 0；
#   H 前端产物：券中心与下单页共用同一份全局状态（用券后实时联动）。
#
# 用法（zh-promotion 不在核心链路里，需用 EXTRA_SPECS 额外拉起）：
#   cd "D:/1/zx-learn" && EXTRA_SPECS="zx-promotion:8088" /usr/bin/bash scripts/dev-up-core.sh >/dev/null 2>&1; \
#     REUSE=1 /usr/bin/bash scripts/verify-coupon-status-sync.sh 2>&1 | tail -75
#   （沙箱内服务不跨工具调用存活，必须"启动 + 断言"写在同一条命令里）
#
# 沙箱注意：脚本内禁用 sort/find；sql() 必须去掉 mysql 输出的 \r；IN(...) 必须逗号分隔。
# =====================================================================
set -u

ROOT="D:/1/zx-learn"
PY="C:/Users/20670/.workbuddy/binaries/python/versions/3.13.12/python.exe"
MYSQL="/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"
REDIS_CLI="/d/1/Redis/redis-cli.exe"
GW="http://localhost:8080"
PROMOTION_DIRECT="http://localhost:8088"
OUT_DIR="$ROOT/logs/verify-coupon-status"
REPORT="$ROOT/docs/verify-coupon-status-report.txt"

cd "$ROOT" || exit 1
mkdir -p "$OUT_DIR" "$ROOT/logs/tmp" "$ROOT/docs"

PASS=0
FAIL=0
declare -a LINES=()

ok()     { PASS=$((PASS + 1)); LINES+=("  [PASS] $1"); }
bad()    { FAIL=$((FAIL + 1)); LINES+=("  [FAIL] $1"); }
info()   { LINES+=("  [INFO] $1"); }
chk()    { if [ "$2" = "$3" ]; then ok "$1"; else bad "$1  <期望=$3 实际=$2>"; fi; }
chk_ne() { if [ "$2" != "$3" ]; then ok "$1"; else bad "$1  <不应等于=$3>"; fi; }
chk_ge() { if [ "$2" -ge "$3" ] 2>/dev/null; then ok "$1"; else bad "$1  <期望>=$3 实际=$2>"; fi; }

MYSQL_PWD_VAL="$(grep -E '^MYSQL_ROOT_PASSWORD=' .env | cut -d= -f2-)"
REDIS_PWD_VAL="$(grep -E '^REDIS_PASSWORD=' .env | cut -d= -f2-)"
# Windows 版 mysql.exe 多行输出是 CRLF：命令替换只去 \n，残留 \r 会污染
# for 循环里的每个元素（踩坑记录）→ 统一 tr -d '\r'
sql() { "$MYSQL" -uroot -p"$MYSQL_PWD_VAL" -N --default-character-set=utf8mb4 -e "$1" 2>/dev/null | tr -d '\r'; }

# Redis 键清理（券限用计数 / 余量），保证脚本可重复执行
redis_del() { MSYS_NO_PATHCONV=1 "$REDIS_CLI" -a "$REDIS_PWD_VAL" --no-auth-warning DEL "$@" >/dev/null 2>&1; }

req() {  # req <base> <method> <path> <token|-> <bodyfile|-> <outfile>
  local base=$1 m=$2 path=$3 tok=$4 bf=$5 out=$6
  local args=(-s -X "$m" "$base$path" -o "$out" -w "%{http_code}" --max-time 30)
  [ "$tok" != "-" ] && args+=(-H "Authorization: Bearer $tok")
  [ "$bf" != "-" ] && args+=(-H "Content-Type: application/json" --data-binary "@$bf")
  MSYS_NO_PATHCONV=1 curl.exe "${args[@]}"
}
http() { req "$GW" "$@"; }

J() {  # J <jsonfile> <python-expr using d>
  "$PY" -c "
import json, sys
try:
    d = json.load(open(sys.argv[1], encoding='utf-8'))
except Exception:
    print('PARSE_ERR'); sys.exit(0)
try:
    print(eval(sys.argv[2]))
except Exception as e:
    print('EVAL_ERR:' + str(e))
" "$1" "$2"
}

body() { printf '%s' "$2" > "$OUT_DIR/$1"; echo "$OUT_DIR/$1"; }

# ---------------------------------------------------------------------
# 合成夹具（与真实数据、雪花 id 段完全隔离）
# ---------------------------------------------------------------------
TUID=9000000002
TUPHONE=13999900002
CPN=9900000000000021          # 合成优惠券模板
CID_A=9900000000000011        # 合成课程 A（首次用券）
CID_B=9900000000000012        # 合成课程 B（重复用券 / 退回后再用）
UC_STATUS_SQL="USE zx_promotion; SELECT status FROM user_coupon WHERE id=\$UID AND deleted=0;"

cleanup_fixtures() {
  redis_del "coupon:used:$CPN:$TUID" "coupon:stock:$CPN"
  sql "USE zx_trade; DELETE FROM order_msg WHERE order_id IN (SELECT id FROM trade_order WHERE user_id=$TUID);" >/dev/null
  sql "USE zx_trade; DELETE FROM coupon_use_record WHERE user_id=$TUID;" >/dev/null
  sql "USE zx_trade; DELETE FROM trade_order_detail WHERE order_id IN (SELECT id FROM trade_order WHERE user_id=$TUID);" >/dev/null
  sql "USE zx_trade; DELETE FROM trade_pay_record WHERE order_id IN (SELECT id FROM trade_order WHERE user_id=$TUID);" >/dev/null
  sql "USE zx_trade; DELETE FROM trade_order WHERE user_id=$TUID;" >/dev/null
  sql "USE zx_learning; DELETE FROM lesson WHERE user_id=$TUID;" >/dev/null
  sql "USE zx_promotion; DELETE FROM user_coupon WHERE user_id=$TUID;" >/dev/null
  sql "USE zx_promotion; DELETE FROM coupon WHERE id=$CPN;" >/dev/null
  sql "USE zx_course; DELETE FROM course_quota WHERE course_id IN ($CID_A,$CID_B);" >/dev/null
  sql "USE zx_course; DELETE FROM course WHERE id IN ($CID_A,$CID_B);" >/dev/null
  sql "USE zx_user; DELETE FROM user WHERE id=$TUID;" >/dev/null
}

uc_status()  { sql "USE zx_promotion; SELECT status FROM user_coupon WHERE id=$1 AND deleted=0;"; }
uc_order()   { sql "USE zx_promotion; SELECT IFNULL(order_id,0) FROM user_coupon WHERE id=$1;"; }
uc_use_time(){ sql "USE zx_promotion; SELECT IF(use_time IS NULL,0,1) FROM user_coupon WHERE id=$1;"; }

# ---------------------------------------------------------------------
echo "=== 阶段 0：准备（合成学员 / 课程 / 优惠券 + 领取） ==="
LINES+=("")
LINES+=("=== 0. 准备：合成学员 + 优惠券 + 领取 ===")

# 预检：服务可达性。若没起服务就断言，会全线 PARSE_ERR/rc=000，把真实原因埋掉
GW_CODE=$(MSYS_NO_PATHCONV=1 curl.exe -s -o /dev/null -w "%{http_code}" --max-time 8 \
  "$GW/coupons/page?pageNo=1&pageSize=1" 2>/dev/null)
chk "预检：网关(8080)可达" "$([ "$GW_CODE" != "000" ] && echo up || echo down)" "up"
PROMO_CODE=$(MSYS_NO_PATHCONV=1 curl.exe -s -o /dev/null -w "%{http_code}" --max-time 8 \
  "$PROMOTION_DIRECT/user-coupons/rules?ids=1" 2>/dev/null)
chk "预检：营销服务(8088)已启动（需 EXTRA_SPECS=\"zx-promotion:8088\"）" \
  "$([ "$PROMO_CODE" != "000" ] && echo up || echo down)" "up"
if [ "$GW_CODE" = "000" ] || [ "$PROMO_CODE" = "000" ]; then
  LINES+=("  [INFO] 服务未就绪，后续断言结果不可信 —— 请先执行：")
  LINES+=("  [INFO]   EXTRA_SPECS=\"zx-promotion:8088\" /usr/bin/bash scripts/dev-up-core.sh")
fi

cleanup_fixtures

sql "USE zx_user; INSERT INTO user (id, cell_phone, username, password, name, type, status, deleted)
     SELECT $TUID, '$TUPHONE', 'e2e券状态学员', password, 'e2e券状态学员', 2, 1, 0
     FROM user WHERE id=2001 LIMIT 1;" >/dev/null
sql "USE zx_course; INSERT INTO course (id, name, cover_url, price, category_id_lv1, status, free, publish_times, description, create_time, update_time, deleted)
     VALUES ($CID_A, '验证用课程-券状态A', '', 1000, 1, 1, 0, 0, '自动化验证用合成课程', NOW(), NOW(), 0),
            ($CID_B, '验证用课程-券状态B', '', 1000, 1, 1, 0, 0, '自动化验证用合成课程', NOW(), NOW(), 0);" >/dev/null
# 合成券：面值 500（分），无门槛，进行中，有效期跨今天
sql "USE zx_promotion; INSERT INTO coupon (id, name, type, discount_amount, threshold_amount, total_num, issued_num, status, valid_begin_time, valid_end_time, create_time, update_time, deleted)
     VALUES ($CPN, '验证用-满减券', 1, 500, 0, 100, 0, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY), NOW(), NOW(), 0);" >/dev/null

body login-tu.json "{\"cellPhone\":\"$TUPHONE\",\"password\":\"123456\"}"
http POST /accounts/login - "$OUT_DIR/login-tu.json" "$OUT_DIR/r-login-tu.json" >/dev/null
TU_TOKEN=$(J "$OUT_DIR/r-login-tu.json" "d['data']['accessToken']")
chk "合成学员登录成功" "$(J "$OUT_DIR/r-login-tu.json" "d['code']")" "200"

body claim.json "{\"couponId\":$CPN}"
http POST /user-coupons/claim "$TU_TOKEN" "$OUT_DIR/claim.json" "$OUT_DIR/r-claim.json" >/dev/null
chk "领取优惠券成功" "$(J "$OUT_DIR/r-claim.json" "d['code']")" "200"
UCID="$(sql "USE zx_promotion; SELECT id FROM user_coupon WHERE user_id=$TUID AND coupon_id=$CPN AND deleted=0 LIMIT 1;")"
chk "前置：券已领取且状态为「未使用」" "$(uc_status "$UCID")" "0"
info "合成用户券行 id = $UCID"

# =====================================================================
echo "=== 阶段 1：下单用券 → 券状态立即变为「已使用」（核心） ==="
LINES+=("")
LINES+=("=== 1. 下单用券 → 券状态实时同步为「已使用」（核心需求）===")

# 课程价 1000 分，券面值 500 → 实付 500
body place-a.json "{\"courseId\":$CID_A,\"totalFee\":500,\"couponId\":$CPN,\"userCouponId\":$UCID}"
http POST /orders/placeOrder "$TU_TOKEN" "$OUT_DIR/place-a.json" "$OUT_DIR/r-place-a.json" >/dev/null
chk "用券下单成功" "$(J "$OUT_DIR/r-place-a.json" "d['code']")" "200"
OID_A=$(J "$OUT_DIR/r-place-a.json" "d['data']")
info "合成订单 id = $OID_A"

chk "【核心】券状态立即更新为「已使用」(status=1)" "$(uc_status "$UCID")" "1"
chk "【核心】回填核销订单号" "$(uc_order "$UCID")" "$OID_A"
chk "【核心】回填使用时间" "$(uc_use_time "$UCID")" "1"
# 该状态同步走 afterCommit 同步回写，不依赖 MQ —— 核销流水只是 INFO（MQ 可能未就绪）
info "核销流水条数（MQ 通道，异步）：$(sql "USE zx_trade; SELECT COUNT(*) FROM coupon_use_record WHERE order_id=$OID_A;")"

# =====================================================================
echo "=== 阶段 2：优惠券列表接口状态一致（前端读的就是它） ==="
LINES+=("")
LINES+=("=== 2. 优惠券列表接口同步返回「已使用」===")

http GET /user-coupons "$TU_TOKEN" - "$OUT_DIR/r-mine-all.json" >/dev/null
chk "券列表接口返回该券为「已使用」(前端 status=2)" \
  "$(J "$OUT_DIR/r-mine-all.json" "[str(c.get('status')) for c in (d['data'] or []) if str(c.get('id'))==str($UCID)][0]")" "2"

http GET "/user-coupons?status=2" "$TU_TOKEN" - "$OUT_DIR/r-mine-used.json" >/dev/null
chk "按「已使用」筛选能查到该券" \
  "$(J "$OUT_DIR/r-mine-used.json" "str($UCID) in [str(c.get('id')) for c in (d['data'] or [])]")" "True"
http GET "/user-coupons?status=1" "$TU_TOKEN" - "$OUT_DIR/r-mine-unused.json" >/dev/null
chk "按「未使用」筛选已查不到该券（券界面不再显示可用）" \
  "$(J "$OUT_DIR/r-mine-unused.json" "str($UCID) in [str(c.get('id')) for c in (d['data'] or [])]")" "False"
chk "该学员「未使用」券数量归零（券界面可用数同步下降）" \
  "$(J "$OUT_DIR/r-mine-unused.json" "len(d['data'] or [])")" "0"

# =====================================================================
echo "=== 阶段 3：重复使用同一张券 → 被拦截且提示语准确 ==="
LINES+=("")
LINES+=("=== 3. 重复使用同一张券：拦截 + 提示语准确（原症状）===")

body place-b.json "{\"courseId\":$CID_B,\"totalFee\":500,\"couponId\":$CPN,\"userCouponId\":$UCID}"
http POST /orders/placeOrder "$TU_TOKEN" "$OUT_DIR/place-b.json" "$OUT_DIR/r-place-b.json" >/dev/null
chk_ne "重复使用同一张券被拒（不产生第二笔订单）" "$(J "$OUT_DIR/r-place-b.json" "d['code']")" "200"
PLACE_B_MSG=$(J "$OUT_DIR/r-place-b.json" "d.get('msg')")
info "拦截提示语：$PLACE_B_MSG"
chk "提示语说明真实原因（已使用过），不再误报「已达领取上限」" \
  "$(J "$OUT_DIR/r-place-b.json" "'已使用过' in (d.get('msg') or '')")" "True"
chk "被拒后未产生订单" "$(sql "USE zx_trade; SELECT COUNT(*) FROM trade_order WHERE user_id=$TUID AND course_id=$CID_B;")" "0"
chk "被拒后券状态未被打乱（仍为已使用）" "$(uc_status "$UCID")" "1"

# =====================================================================
echo "=== 阶段 4：订单关单 → 券退回「未使用」并可再次使用 ==="
LINES+=("")
LINES+=("=== 4. 关单退券：状态退回「未使用」，券恢复可用 ===")

http POST "/orders/$OID_A/timeout" "$TU_TOKEN" - "$OUT_DIR/r-timeout.json" >/dev/null
chk "待支付订单关单成功" "$(J "$OUT_DIR/r-timeout.json" "d['code']")" "200"
chk "订单状态已关闭" "$(sql "USE zx_trade; SELECT status FROM trade_order WHERE id=$OID_A;")" "2"
chk "【核心】关单后券状态退回「未使用」(status=0)" "$(uc_status "$UCID")" "0"
chk "关单后清空核销订单号（不再被已关闭订单占住）" "$(uc_order "$UCID")" "0"
chk "关单后清空使用时间" "$(uc_use_time "$UCID")" "0"

http GET "/user-coupons?status=1" "$TU_TOKEN" - "$OUT_DIR/r-mine-unused2.json" >/dev/null
chk "券列表重新显示为「未使用」（券界面恢复可用）" \
  "$(J "$OUT_DIR/r-mine-unused2.json" "str($UCID) in [str(c.get('id')) for c in (d['data'] or [])]")" "True"

body place-b2.json "{\"courseId\":$CID_B,\"totalFee\":500,\"couponId\":$CPN,\"userCouponId\":$UCID}"
http POST /orders/placeOrder "$TU_TOKEN" "$OUT_DIR/place-b2.json" "$OUT_DIR/r-place-b2.json" >/dev/null
chk "退回后券可再次使用（券状态与限用计数同时恢复）" "$(J "$OUT_DIR/r-place-b2.json" "d['code']")" "200"
OID_B=$(J "$OUT_DIR/r-place-b2.json" "d['data']")
chk "再次使用后券状态又变为「已使用」" "$(uc_status "$UCID")" "1"
chk "券绑定到新订单" "$(uc_order "$UCID")" "$OID_B"

# =====================================================================
echo "=== 阶段 5：状态回写幂等（重复调用 / 过期订单号无副作用） ==="
LINES+=("")
LINES+=("=== 5. 回写幂等：重复调用不产生副作用 ===")

# 用「旧订单号」重复回写：券已是已使用 → 必须保持原绑定不被覆盖
MSYS_NO_PATHCONV=1 curl.exe -s -o "$OUT_DIR/r-idem-1.json" -w "%{http_code}" -X POST \
  "$PROMOTION_DIRECT/user-coupons/internal/mark-used?userCouponId=$UCID&userId=$TUID&couponId=$CPN&orderId=$OID_A" \
  > "$OUT_DIR/r-idem-1-code.txt" 2>/dev/null
chk "重复回写「已使用」返回 HTTP 200（幂等，不报错）" "$(cat "$OUT_DIR/r-idem-1-code.txt")" "200"
chk "重复回写不覆盖已有绑定（仍是当前订单）" "$(uc_order "$UCID")" "$OID_B"
chk "重复回写后状态仍为「已使用」" "$(uc_status "$UCID")" "1"

# 不存在的订单号退回：不应有任何影响
MSYS_NO_PATHCONV=1 curl.exe -s -o "$OUT_DIR/r-idem-2.json" -w "%{http_code}" -X POST \
  "$PROMOTION_DIRECT/user-coupons/internal/mark-refunded?orderId=9000000002999" \
  > "$OUT_DIR/r-idem-2-code.txt" 2>/dev/null
chk "退回一个不存在的订单号返回 HTTP 200（幂等空操作）" "$(cat "$OUT_DIR/r-idem-2-code.txt")" "200"
chk "无关订单的退回未影响该券状态" "$(uc_status "$UCID")" "1"
chk "无关订单的退回未影响绑定订单" "$(uc_order "$UCID")" "$OID_B"

# 不存在的券：不应抛异常
MSYS_NO_PATHCONV=1 curl.exe -s -o "$OUT_DIR/r-idem-3.json" -w "%{http_code}" -X POST \
  "$PROMOTION_DIRECT/user-coupons/internal/mark-used?userCouponId=9000000002888&orderId=$OID_B" \
  > "$OUT_DIR/r-idem-3-code.txt" 2>/dev/null
chk "回写一张不存在的券返回 HTTP 200（容错，不抛异常）" "$(cat "$OUT_DIR/r-idem-3-code.txt")" "200"

# =====================================================================
echo "=== 阶段 6：内部回写接口鉴权 ==="
LINES+=("")
LINES+=("=== 6. 内部回写接口鉴权：外部不可调用 ===")

# 6.1 服务间直连（无 user-info 头）→ 放行
MSYS_NO_PATHCONV=1 curl.exe -s -o "$OUT_DIR/r-internal-ok.json" -w "%{http_code}" -X POST \
  "$PROMOTION_DIRECT/user-coupons/internal/mark-used?userCouponId=$UCID&orderId=$OID_B" \
  > "$OUT_DIR/r-internal-ok-code.txt" 2>/dev/null
chk "服务间直连调用内部回写接口放行（HTTP 200）" "$(cat "$OUT_DIR/r-internal-ok-code.txt")" "200"

# 6.2 学员经网关带登录态调用 → 403（禁止外部用户回写券状态）
http POST "/user-coupons/internal/mark-used?userCouponId=$UCID&orderId=$OID_B" "$TU_TOKEN" - "$OUT_DIR/r-internal-ext.json" >/dev/null
chk "学员经网关调用内部回写接口被拒 403" "$(J "$OUT_DIR/r-internal-ext.json" "d['code']")" "403"

# 6.3 匿名经网关调用 → 网关 401（该路径不在白名单）
NOAUTH_CODE=$(MSYS_NO_PATHCONV=1 curl.exe -s -o "$OUT_DIR/r-internal-anon.json" -w "%{http_code}" -X POST \
  "$GW/user-coupons/internal/mark-used?userCouponId=$UCID&orderId=$OID_B" 2>/dev/null)
chk "匿名经网关调用内部回写接口被网关拒绝（HTTP 401）" "$NOAUTH_CODE" "401"

# =====================================================================
echo "=== 阶段 7：全库存量一致性（漂移必须为 0） ==="
LINES+=("")
LINES+=("=== 7. 全库存量一致性 ===")

chk "全库「券状态与最后一条核销流水」漂移数 = 0" \
  "$(sql "SELECT COUNT(*) FROM (
            SELECT uc.status AS actual,
                   CASE WHEN last.status = 1 THEN 1
                        WHEN uc.valid_end_time IS NOT NULL AND uc.valid_end_time <= NOW() THEN 2
                        ELSE 0 END AS expected
            FROM zx_promotion.user_coupon uc
            JOIN (SELECT r.user_coupon_id, r.status
                  FROM zx_trade.coupon_use_record r
                  JOIN (SELECT user_coupon_id, MAX(id) AS last_id FROM zx_trade.coupon_use_record
                        WHERE user_coupon_id IS NOT NULL GROUP BY user_coupon_id) t
                    ON t.user_coupon_id = r.user_coupon_id AND t.last_id = r.id) last
              ON last.user_coupon_id = uc.id) x
          WHERE x.actual <> x.expected;")" "0"
chk "全库「有核销流水但未回填订单号」的券 = 0" \
  "$(sql "USE zx_promotion; SELECT COUNT(*) FROM user_coupon uc
          WHERE uc.status=1 AND uc.order_id IS NULL
            AND EXISTS (SELECT 1 FROM zx_trade.coupon_use_record r WHERE r.user_coupon_id=uc.id);")" "0"
info "种子数据中本就标记为已使用、无订单号的券数量（非本模块产生，仅记录）：\
$(sql "USE zx_promotion; SELECT COUNT(*) FROM user_coupon WHERE status=1 AND order_id IS NULL;")"
chk "状态回写接口未产生越权副作用（合成券仍是已使用）" "$(uc_status "$UCID")" "1"

# =====================================================================
echo "=== 阶段 8：前端产物（券状态三端实时联动） ==="
LINES+=("")
LINES+=("=== 8. 前端：券中心与下单页共用同一份状态 ===")

COMPOSABLE="$ROOT/zx-web/src/composables/useMyCoupons.ts"
CENTER="$ROOT/zx-web/src/views/trade/CouponCenterView.vue"
TRADE="$ROOT/zx-web/src/views/trade/TradeView.vue"

chk "券状态全局单例 composable 已创建" "$(test -f "$COMPOSABLE" && echo 1 || echo 0)" "1"
chk "单例提供乐观更新 markUsed（用券后立即置为已使用）" \
  "$(grep -c "function markUsed" "$COMPOSABLE" 2>/dev/null)" "1"
chk_ge "单例提供「可用券」统一判定 usableCoupons" \
  "$(grep -c "usableCoupons" "$COMPOSABLE" 2>/dev/null)" "1"
chk_ge "优惠券中心改为读取全局单例" "$(grep -c "useMyCoupons" "$CENTER" 2>/dev/null)" "1"
chk "优惠券中心不再各自拉取一份（避免两份状态不一致）" \
  "$(grep -c "myCoupons" "$CENTER" 2>/dev/null)" "0"
chk_ge "确认下单页改为读取同一份可用券" "$(grep -c "useMyCoupons" "$TRADE" 2>/dev/null)" "1"
chk_ge "下单成功后立即标记该券为已使用（前端即时呈现）" \
  "$(grep -c "markUsed" "$TRADE" 2>/dev/null)" "1"
chk "前端构建产物存在" "$(test -f "$ROOT/zx-web/dist/index.html" && echo 1 || echo 0)" "1"
chk_ge "构建产物包含优惠券中心分包" \
  "$(ls "$ROOT/zx-web/dist/assets" 2>/dev/null | grep -c "^CouponCenterView-")" "1"

# =====================================================================
echo "=== 阶段 9：清理夹具 ==="
LINES+=("")
LINES+=("=== 9. 清理夹具 ===")
cleanup_fixtures
chk "合成用户券已清理" "$(sql "USE zx_promotion; SELECT COUNT(*) FROM user_coupon WHERE user_id=$TUID;")" "0"
chk "合成券模板已清理" "$(sql "USE zx_promotion; SELECT COUNT(*) FROM coupon WHERE id=$CPN;")" "0"
chk "合成订单已清理" "$(sql "USE zx_trade; SELECT COUNT(*) FROM trade_order WHERE user_id=$TUID;")" "0"
chk "合成核销流水已清理" "$(sql "USE zx_trade; SELECT COUNT(*) FROM coupon_use_record WHERE user_id=$TUID;")" "0"
chk "合成课程已清理" "$(sql "USE zx_course; SELECT COUNT(*) FROM course WHERE id IN ($CID_A,$CID_B);")" "0"
chk "合成学员已清理" "$(sql "USE zx_user; SELECT COUNT(*) FROM user WHERE id=$TUID;")" "0"
chk "清理后全库漂移仍为 0（未破坏真实数据）" \
  "$(sql "SELECT COUNT(*) FROM (
            SELECT uc.status AS actual,
                   CASE WHEN last.status = 1 THEN 1
                        WHEN uc.valid_end_time IS NOT NULL AND uc.valid_end_time <= NOW() THEN 2
                        ELSE 0 END AS expected
            FROM zx_promotion.user_coupon uc
            JOIN (SELECT r.user_coupon_id, r.status
                  FROM zx_trade.coupon_use_record r
                  JOIN (SELECT user_coupon_id, MAX(id) AS last_id FROM zx_trade.coupon_use_record
                        WHERE user_coupon_id IS NOT NULL GROUP BY user_coupon_id) t
                    ON t.user_coupon_id = r.user_coupon_id AND t.last_id = r.id) last
              ON last.user_coupon_id = uc.id) x
          WHERE x.actual <> x.expected;")" "0"

# =====================================================================
printf '%s\n' "${LINES[@]}"
echo ""
echo "====================================================================="
echo "断言总数: $((PASS + FAIL))   通过: $PASS   失败: $FAIL"
if [ "$FAIL" -eq 0 ]; then echo "结论: 全部通过 ✅"; else echo "结论: 存在失败项 ❌"; fi
echo "====================================================================="
echo "报告已写入: $REPORT"

{
  echo "====================================================================="
  echo "知行智学 · 优惠券使用状态同步 端到端验证报告"
  echo "生成时间: $(date '+%Y-%m-%d %H:%M:%S')"
  echo "网关地址: $GW"
  echo "====================================================================="
  printf '%s\n' "${LINES[@]}"
  echo ""
  echo "---------------------------------------------------------------------"
  echo "断言总数: $((PASS + FAIL))   通过: $PASS   失败: $FAIL"
  echo "原始报文目录: logs/verify-coupon-status/"
  echo "---------------------------------------------------------------------"
} > "$REPORT"
