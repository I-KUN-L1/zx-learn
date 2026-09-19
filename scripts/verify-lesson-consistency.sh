#!/usr/bin/env bash
# =====================================================================
# 知行智学 · 课表一致性（已拥有 ↔ 我的课表）端到端验证
# ---------------------------------------------------------------------
# 覆盖的两类真实症状：
#   1) 在课程界面购买新课程时提示"课程已拥有"，但该课程在「我的课表」里没有；
#   2) 加入免费课程时同样提示"课程已拥有"，且「我的课表」里同样找不到。
#
# 根因（两条叠加）：
#   A. 订单已支付，但 zx-learning 没收到开课事件（支付时学习服务不可用 → Feign 同步开课失败，
#      且本地消息表里的 orderPaid 事件在 MQ 不可用期间重试耗尽转死信 order_msg.status=3），
#      旧代码在"已支付订单"命中时直接报错拦截 → 用户永远进不去（提示已拥有、课表却没有）。
#   B. lesson 的唯一索引 uk_user_course 是物理约束，删除课表项是逻辑删除（deleted=1），
#      旧代码撞唯一索引只做"幂等跳过" → 被删过的课表项永远不复活。
#
# 验证点：
#   A 付费课「已支付但无课表」→ 再次下单：拦截同时**自愈补开课**，课表立即出现；
#   B 免费课「已支付但无课表」→ 再次加入学习：**幂等成功**（不再报已拥有）且补开课；
#   C 已开通后重复加入学习：幂等成功、不产生重复课表项；
#   D 课表项被逻辑删除后重新开课：**复活**，不会永久隐形；
#   E 死信对账补偿：orderPaid 死信 → 补开课 + 死信标记已消费（可重复调用且幂等）；
#   F 全库存量："已支付但课表缺失" 必须为 0；
#   G 前端产物：课表每页 9 门（3×3）+ 小规模课表整屏展示，不再出现"第 9 门被挤到下一页"。
#
# 用法：
#   cd "D:/1/zx-learn" && /usr/bin/bash scripts/dev-up-core.sh >/dev/null 2>&1; \
#     REUSE=1 /usr/bin/bash scripts/verify-lesson-consistency.sh 2>&1 | tail -70
#   （服务不跨工具调用存活，必须"启动 + 断言"在同一条命令内完成）
#
# 沙箱注意：脚本内禁用 sort/find；sql() 必须去掉 mysql 输出的 \r；IN(...) 逗号分隔。
# =====================================================================
set -u

ROOT="D:/1/zx-learn"
PY="C:/Users/20670/.workbuddy/binaries/python/versions/3.13.12/python.exe"
MYSQL="/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"
GW="http://localhost:8080"
LEARNING_DIRECT="http://localhost:8086"
TRADE_DIRECT="http://localhost:8087"
OUT_DIR="$ROOT/logs/verify-lesson-consistency"
REPORT="$ROOT/docs/verify-lesson-consistency-report.txt"
REUSE_MODE="${REUSE:-0}"

cd "$ROOT" || exit 1
mkdir -p "$OUT_DIR" "$ROOT/logs/tmp" "$ROOT/docs"

PASS=0
FAIL=0
declare -a LINES=()

ok()   { PASS=$((PASS + 1)); LINES+=("  [PASS] $1"); }
bad()  { FAIL=$((FAIL + 1)); LINES+=("  [FAIL] $1"); }
info() { LINES+=("  [INFO] $1"); }
chk()  { if [ "$2" = "$3" ]; then ok "$1"; else bad "$1  <期望=$3 实际=$2>"; fi; }
chk_ge() { if [ "$2" -ge "$3" ] 2>/dev/null; then ok "$1"; else bad "$1  <期望>=$3 实际=$2>"; fi; }

MYSQL_PWD_VAL="$(grep -E '^MYSQL_ROOT_PASSWORD=' .env | cut -d= -f2-)"
# 注意：Windows 版 mysql.exe 多行输出是 CRLF，命令替换只去掉 \n，残留 \r 会污染
# for 循环里的每个元素 → 统一 tr -d '\r'（踩坑记录）
sql() { "$MYSQL" -uroot -p"$MYSQL_PWD_VAL" -N --default-character-set=utf8mb4 -e "$1" 2>/dev/null | tr -d '\r'; }

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
# 合成夹具（与真实数据和雪花 id 段完全隔离）
# ---------------------------------------------------------------------
TUID=9000000001
TUPHONE=13999900001
CID_PAID=9900000000000001
CID_FREE=9900000000000002
OID_PAID=9000000002001
OID_FREE=9000000002002
MSG_PAID=9000000002101

cleanup_fixtures() {
  sql "USE zx_trade; DELETE FROM order_msg WHERE order_id IN ($OID_PAID,$OID_FREE) OR id=$MSG_PAID;" >/dev/null
  sql "USE zx_trade; DELETE FROM trade_order_detail WHERE order_id IN ($OID_PAID,$OID_FREE);" >/dev/null
  sql "USE zx_trade; DELETE FROM trade_pay_record WHERE order_id IN ($OID_PAID,$OID_FREE);" >/dev/null
  sql "USE zx_trade; DELETE FROM trade_order WHERE user_id=$TUID OR id IN ($OID_PAID,$OID_FREE);" >/dev/null
  sql "USE zx_learning; DELETE FROM lesson WHERE user_id=$TUID;" >/dev/null
  sql "USE zx_learning; DELETE FROM consume_record WHERE consume_key LIKE 'lesson:paid:%';" >/dev/null
  sql "USE zx_course; DELETE FROM course_quota WHERE course_id IN ($CID_PAID,$CID_FREE);" >/dev/null
  sql "USE zx_course; DELETE FROM course_quota_record WHERE course_id IN ($CID_PAID,$CID_FREE);" >/dev/null
  sql "USE zx_course; DELETE FROM course WHERE id IN ($CID_PAID,$CID_FREE);" >/dev/null
  sql "USE zx_user; DELETE FROM user WHERE id=$TUID;" >/dev/null
}

# ---------------------------------------------------------------------
echo "=== 阶段 0：准备（合成学员 / 合成课程） ==="
cleanup_fixtures

# 合成学员：复制真实学员 2001 的密码 hash，手机号固定 13999900001
sql "USE zx_user; INSERT INTO user (id, cell_phone, username, password, name, type, status, deleted)
     SELECT $TUID, '$TUPHONE', 'e2e课表一致性学员', password, 'e2e课表一致性学员', 2, 1, 0
     FROM user WHERE id=2001 LIMIT 1;" >/dev/null
sql "USE zx_course; INSERT INTO course (id, name, cover_url, price, category_id_lv1, status, free, publish_times, description, create_time, update_time, deleted)
     VALUES ($CID_PAID, '验证用课程-付费课表自愈', '', 100, 1, 1, 0, 0, '自动化验证用合成课程', NOW(), NOW(), 0);" >/dev/null
sql "USE zx_course; INSERT INTO course (id, name, cover_url, price, category_id_lv1, status, free, publish_times, description, create_time, update_time, deleted)
     VALUES ($CID_FREE, '验证用课程-免费课表自愈', '', 0, 1, 1, 1, 0, '自动化验证用合成课程', NOW(), NOW(), 0);" >/dev/null

body login-tu.json "{\"cellPhone\":\"$TUPHONE\",\"password\":\"123456\"}"
http POST /accounts/login - "$OUT_DIR/login-tu.json" "$OUT_DIR/r-login-tu.json" >/dev/null
TU_TOKEN=$(J "$OUT_DIR/r-login-tu.json" "d['data']['accessToken']")
chk "合成学员登录成功" "$(J "$OUT_DIR/r-login-tu.json" "d['code']")" "200"

# =====================================================================
echo "=== 阶段 1：付费课「已支付但课表缺失」→ 再次下单自愈（症状 1） ==="
LINES+=("")
LINES+=("=== 一、付费课：已支付但课表缺失 → 下单自愈补开（症状 1）===")

# 构造脏状态：已支付订单存在，但课表里没有该课程（模拟"支付成功但开课事件丢失"）
sql "USE zx_trade; INSERT INTO trade_order (id, order_no, user_id, course_id, course_name, course_price, total_fee, deduction, status, pay_time, create_time, update_time, deleted, user_deleted)
     VALUES ($OID_PAID, 'TE$OID_PAID', $TUID, $CID_PAID, '验证用课程-付费课表自愈', 100, 100, 0, 1, NOW(), NOW(), NOW(), 0, 0);
     INSERT INTO trade_order_detail (id, order_id, course_id, name, price, create_time, update_time, deleted)
     VALUES (9000000002201, $OID_PAID, $CID_PAID, '验证用课程-付费课表自愈', 100, NOW(), NOW(), 0);" >/dev/null

chk "前置：已支付订单存在、课表为空（脏状态已构造）" \
  "$(sql "USE zx_trade; SELECT COUNT(*) FROM trade_order WHERE user_id=$TUID AND course_id=$CID_PAID AND status=1 AND deleted=0;")|$(sql "USE zx_learning; SELECT COUNT(*) FROM lesson WHERE user_id=$TUID AND course_id=$CID_PAID AND deleted=0;")" "1|0"

# 关键：旧代码在这里抛"您已购买该课程"，课表永远补不上 → 用户被卡死
body place-order.json "{\"courseId\":$CID_PAID,\"totalFee\":100}"
http POST /orders/placeOrder "$TU_TOKEN" "$OUT_DIR/place-order.json" "$OUT_DIR/r-place.json" >/dev/null
PLACE_MSG=$(J "$OUT_DIR/r-place.json" "d['msg']")
chk_ge "下单被拦截（已支付订单，不再重复建单）" \
  "$("$PY" -c "print(len('''$PLACE_MSG'''))" 2>/dev/null)" "1"
info "拦截提示语：$PLACE_MSG"
chk "拦截提示明确告知已开通学习（不再只说'已拥有'）" \
  "$(J "$OUT_DIR/r-place.json" "'已为你开通学习' in (d.get('msg') or '')")" "True"
chk "【核心】自愈：课表已补开该课程" \
  "$(sql "USE zx_learning; SELECT COUNT(*) FROM lesson WHERE user_id=$TUID AND course_id=$CID_PAID AND deleted=0;")" "1"
http GET /lessons/mine/course-ids "$TU_TOKEN" - "$OUT_DIR/r-ids-a.json" >/dev/null
chk "自愈后「已拥有」集合含该课程（两端一致）" \
  "$(J "$OUT_DIR/r-ids-a.json" "str($CID_PAID) in [str(x) for x in (d['data'] or [])]")" "True"
http GET "/lessons/page?pageNo=1&pageSize=50" "$TU_TOKEN" - "$OUT_DIR/r-page-a.json" >/dev/null
chk "自愈后「我的课表」列表可见该课程" \
  "$(J "$OUT_DIR/r-page-a.json" "str($CID_PAID) in [str(o.get('courseId')) for o in d['data']['list']]")" "True"

# =====================================================================
echo "=== 阶段 2：免费课「已支付但课表缺失」→ 加入学习幂等成功（症状 2） ==="
LINES+=("")
LINES+=("=== 二、免费课：已支付但课表缺失 → 加入学习幂等成功（症状 2）===")

sql "USE zx_trade; INSERT INTO trade_order (id, order_no, user_id, course_id, course_name, course_price, total_fee, deduction, status, pay_time, create_time, update_time, deleted, user_deleted)
     VALUES ($OID_FREE, 'TE$OID_FREE', $TUID, $CID_FREE, '验证用课程-免费课表自愈', 0, 0, 0, 1, NOW(), NOW(), NOW(), 0, 0);
     INSERT INTO trade_order_detail (id, order_id, course_id, name, price, create_time, update_time, deleted)
     VALUES (9000000002202, $OID_FREE, $CID_FREE, '验证用课程-免费课表自愈', 0, NOW(), NOW(), 0);" >/dev/null

chk "前置：免费课已支付订单存在、课表为空" \
  "$(sql "USE zx_trade; SELECT COUNT(*) FROM trade_order WHERE user_id=$TUID AND course_id=$CID_FREE AND status=1 AND deleted=0;")|$(sql "USE zx_learning; SELECT COUNT(*) FROM lesson WHERE user_id=$TUID AND course_id=$CID_FREE AND deleted=0;")" "1|0"

# 关键：旧代码抛"您已拥有该课程，请勿重复加入学习"，课表补不上 → 症状 2
http POST "/orders/freeCourse/$CID_FREE" "$TU_TOKEN" - "$OUT_DIR/r-free-b.json" >/dev/null
chk "【核心】加入学习返回成功（不再报"课程已拥有"）" \
  "$(J "$OUT_DIR/r-free-b.json" "d['code']")" "200"
chk "【核心】自愈：课表已补开该免费课" \
  "$(sql "USE zx_learning; SELECT COUNT(*) FROM lesson WHERE user_id=$TUID AND course_id=$CID_FREE AND deleted=0;")" "1"
http GET /lessons/mine/course-ids "$TU_TOKEN" - "$OUT_DIR/r-ids-b.json" >/dev/null
chk "自愈后「已拥有」集合含该免费课" \
  "$(J "$OUT_DIR/r-ids-b.json" "str($CID_FREE) in [str(x) for x in (d['data'] or [])]")" "True"

# =====================================================================
echo "=== 阶段 3：已开通后重复加入学习 → 幂等成功 ==="
LINES+=("")
LINES+=("=== 三、已开通后重复加入学习：幂等成功 ===")

http POST "/orders/freeCourse/$CID_FREE" "$TU_TOKEN" - "$OUT_DIR/r-free-c.json" >/dev/null
chk "重复加入学习仍返回成功（幂等，不报错）" \
  "$(J "$OUT_DIR/r-free-c.json" "d['code']")" "200"
chk "幂等：课表未产生重复项" \
  "$(sql "USE zx_learning; SELECT COUNT(*) FROM lesson WHERE user_id=$TUID AND course_id=$CID_FREE;")" "1"

# =====================================================================
echo "=== 阶段 4：课表项被逻辑删除后重新开课 → 复活 ==="
LINES+=("")
LINES+=("=== 四、课表项被删除后重新开课：必须复活（不永久隐形）===")

sql "USE zx_learning; UPDATE lesson SET deleted=1 WHERE user_id=$TUID AND course_id=$CID_FREE;" >/dev/null
http GET /lessons/mine/course-ids "$TU_TOKEN" - "$OUT_DIR/r-ids-d0.json" >/dev/null
chk "前置：删除后该课程不再属于已拥有" \
  "$(J "$OUT_DIR/r-ids-d0.json" "str($CID_FREE) in [str(x) for x in (d['data'] or [])]")" "False"

body enroll-revive.json "{\"userId\":$TUID,\"courseId\":$CID_FREE,\"courseName\":\"验证用课程-免费课表自愈\"}"
req "$LEARNING_DIRECT" POST /lessons/internal/enroll - "$OUT_DIR/enroll-revive.json" "$OUT_DIR/r-enroll-revive.json" >/dev/null
chk "重新开课后课表项复活（deleted=0）" \
  "$(sql "USE zx_learning; SELECT COUNT(*) FROM lesson WHERE user_id=$TUID AND course_id=$CID_FREE AND deleted=0;")" "1"
http GET /lessons/mine/course-ids "$TU_TOKEN" - "$OUT_DIR/r-ids-d1.json" >/dev/null
chk "复活后可再次「已拥有」" \
  "$(J "$OUT_DIR/r-ids-d1.json" "str($CID_FREE) in [str(x) for x in (d['data'] or [])]")" "True"

# =====================================================================
echo "=== 阶段 5：orderPaid 死信对账补偿 ==="
LINES+=("")
LINES+=("=== 五、orderPaid 死信 → 对账补偿补开课 ===")

# 构造：付费课课表被清空 + orderPaid 消息处于死信（status=3，重试耗尽）
#
# ⚠ 竞态说明（本用例曾出现 1 项失败，经定位为**测试竞态，非产品缺陷**）：
#   zx-trade 启用了 @EnableScheduling，LessonReconcileJob 每 5 分钟自动执行**同一段**补偿逻辑
#   （deadPaidOrderIds 无时间过滤，与手工触发看到的是同一批行）。若它恰好在"装填夹具"与
#   "手工触发"之间跑了一轮，会抢先补开课并把死信标记为已消费 → 手工触发"无事可做"返回 0，
#   而其后的「课表已补开 / 死信已消费」断言依然成立（补偿确实发生了，只是执行者不是手工触发）。
#   因此改为「装填 → 触发 → 若被自动通道抢先则重新装填」的有界重试：
#   既保留对"手工触发计数"的严格断言，又不依赖定时任务的相位。
arm_fixture() {
  sql "USE zx_learning; DELETE FROM lesson WHERE user_id=$TUID AND course_id=$CID_PAID;" >/dev/null
  sql "USE zx_trade; INSERT INTO order_msg (id, order_id, biz_key, topic, tag, payload, status, retry_count, max_retry, next_retry_time, create_time, update_time, deleted)
       VALUES ($MSG_PAID, $OID_PAID, '$OID_PAID:orderPaid', 'zx_order_paid', 'PAID', '{}', 3, 6, 5, NULL, NOW(), NOW(), 0)
       ON DUPLICATE KEY UPDATE status=3, retry_count=max_retry, next_retry_time=NULL, deleted=0;" >/dev/null
}
arm_fixture
chk "前置：课表为空 + orderPaid 处于死信" \
  "$(sql "USE zx_learning; SELECT COUNT(*) FROM lesson WHERE user_id=$TUID AND course_id=$CID_PAID AND deleted=0;")|$(sql "USE zx_trade; SELECT COUNT(*) FROM order_msg WHERE id=$MSG_PAID AND status=3;")" "0|1"

HEALED=""
for attempt in 1 2 3; do
  req "$TRADE_DIRECT" POST "/order-details/reconcile-lessons?limit=200" - - "$OUT_DIR/r-reconcile-1.json" >/dev/null
  HEALED=$(cat "$OUT_DIR/r-reconcile-1.json" 2>/dev/null | tr -d '\r')
  [ "${HEALED:-0}" -ge 1 ] 2>/dev/null && break
  # 返回 0 有两种可能，必须区分：
  #   ① 被 LessonReconcileJob 抢先（该死信已转为 status=2）→ 重新装填，再试一轮；
  #   ② 死信仍是 status=3 却补不开课 → 真正的产品缺陷，立即退出并如实失败。
  if [ "$(sql "USE zx_trade; SELECT COUNT(*) FROM order_msg WHERE id=$MSG_PAID AND status=2;")" = "1" ]; then
    info "第 $attempt 轮手工触发为 0：定时任务已抢先补偿，重新装填夹具后重试"
    arm_fixture
  else
    break
  fi
done
chk_ge "对账补偿本轮补开课 ≥ 1 门" "$HEALED" "1"
chk "补偿后课表已补开该课程" \
  "$(sql "USE zx_learning; SELECT COUNT(*) FROM lesson WHERE user_id=$TUID AND course_id=$CID_PAID AND deleted=0;")" "1"
chk "补偿后死信被标记为已消费（不再重复补偿）" \
  "$(sql "USE zx_trade; SELECT COUNT(*) FROM order_msg WHERE id=$MSG_PAID AND status=2;")" "1"

req "$TRADE_DIRECT" POST "/order-details/reconcile-lessons?limit=200" - - "$OUT_DIR/r-reconcile-2.json" >/dev/null
chk "再次对账补开课为 0（幂等）" "$(cat "$OUT_DIR/r-reconcile-2.json" 2>/dev/null | tr -d '\r')" "0"

# =====================================================================
echo "=== 阶段 6：存量数据一致性 ==="
LINES+=("")
LINES+=("=== 六、存量数据：已支付订单必须都能在课表中找到 ===")

chk "全库「已支付但课表缺失」= 0" \
  "$(sql "SELECT COUNT(*) FROM (SELECT o.user_id, o.course_id FROM zx_trade.trade_order o WHERE o.deleted=0 AND o.status=1 AND NOT EXISTS (SELECT 1 FROM zx_learning.lesson l WHERE l.user_id=o.user_id AND l.course_id=o.course_id AND l.deleted=0) GROUP BY o.user_id, o.course_id) t;")" "0"
chk "全库无 orderPaid 死信残留" \
  "$(sql "USE zx_trade; SELECT COUNT(*) FROM order_msg WHERE tag='PAID' AND status=3;")" "0"
chk "全库无被逻辑删除的课表项（与已支付订单冲突）" \
  "$(sql "USE zx_learning; SELECT COUNT(*) FROM lesson l WHERE l.deleted<>0 AND EXISTS (SELECT 1 FROM zx_trade.trade_order o WHERE o.user_id=l.user_id AND o.course_id=l.course_id AND o.status=1 AND o.deleted=0);")" "0"

# =====================================================================
echo "=== 阶段 7：前端产物（我的课表布局：九门同一界面） ==="
LINES+=("")
LINES+=("=== 七、前端产物：课表布局（需求 3）===")

SRC="$ROOT/zx-web/src/views/learning/LearningView.vue"
chk "课表每页 9 门（3 列 × 3 行，不再出现第 9 门被挤到下一页）" \
  "$(grep -c "LESSON_PAGE_SIZE = 9" "$SRC" 2>/dev/null)" "1"
chk "源码已移除旧的每页 8 门设置" \
  "$(grep -c "pageSize: 8" "$SRC" 2>/dev/null)" "0"
chk "小规模课表整屏展示（不超过 15 门不分页）" \
  "$(grep -c "LESSON_SHOW_ALL_MAX = 15" "$SRC" 2>/dev/null)" "1"
chk "前端构建产物存在" \
  "$(test -f "$ROOT/zx-web/dist/index.html" && echo 1 || echo 0)" "1"
chk "构建产物包含新的分页提示文案（共 N 门/本页显示）" \
  "$(grep -rl "本页显示" "$ROOT/zx-web/dist/assets" 2>/dev/null | head -1 | wc -l | tr -d ' ')" "1"

# =====================================================================
echo "=== 阶段 8：清理夹具 ==="
cleanup_fixtures
chk "合成学员已清理" "$(sql "USE zx_user; SELECT COUNT(*) FROM user WHERE id=$TUID;")" "0"
chk "合成课程已清理" "$(sql "USE zx_course; SELECT COUNT(*) FROM course WHERE id IN ($CID_PAID,$CID_FREE);")" "0"
chk "合成订单已清理" "$(sql "USE zx_trade; SELECT COUNT(*) FROM trade_order WHERE user_id=$TUID;")" "0"
chk "合成课表已清理" "$(sql "USE zx_learning; SELECT COUNT(*) FROM lesson WHERE user_id=$TUID;")" "0"
chk "合成消息已清理" "$(sql "USE zx_trade; SELECT COUNT(*) FROM order_msg WHERE id=$MSG_PAID;")" "0"

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
  echo "知行智学 · 课表一致性（已拥有 ↔ 我的课表）端到端验证报告"
  echo "生成时间: $(date '+%Y-%m-%d %H:%M:%S')"
  echo "网关地址: $GW"
  echo "====================================================================="
  printf '%s\n' "${LINES[@]}"
  echo ""
  echo "---------------------------------------------------------------------"
  echo "断言总数: $((PASS + FAIL))   通过: $PASS   失败: $FAIL"
  echo "原始报文目录: logs/verify-lesson-consistency/"
  echo "---------------------------------------------------------------------"
} > "$REPORT"
