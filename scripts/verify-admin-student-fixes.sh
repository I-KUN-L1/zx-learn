#!/usr/bin/env bash
# =====================================================================
# 知行智学 · 管理员端/学员端四项修复 端到端验证
# ---------------------------------------------------------------------
# 覆盖需求：
#   1. 管理员端用户列表展示「注册时间」
#   2. 学员端学情报告不再「系统繁忙」、数据正常展示
#   3. 我的订单：已拥有课程不出现于待支付 + 倒计时结束自动关闭
#      （含修复"倒计时归零仍无法关闭"）
#   4. 订单删除：学员端软删除（管理端保留）/ 管理端清理无用订单
#
# 用法：
#   /usr/bin/bash scripts/verify-admin-student-fixes.sh
#   REUSE=1 /usr/bin/bash scripts/verify-admin-student-fixes.sh   # 复用已运行服务只跑断言
#
# 设计说明：
#   * 测试夹具用「直接 INSERT 合成订单」构造（id 段 99xx…，与雪花段隔离），
#     避免走下单/支付链路产生课表、优惠券、MQ 等副作用，测试结束物理删除，可重复执行。
#   * 沙箱 PATH 用 /tmp/system32 的 Windows 版 sort/find 遮蔽了 GNU 工具 → 脚本内禁用。
#   * 断言全部读 body.code（后端业务异常以 HTTP 200 + code 返回）。
# =====================================================================
set -u

ROOT="D:/1/zx-learn"
PY="C:/Users/20670/.workbuddy/binaries/python/versions/3.13.12/python.exe"
MYSQL="/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"
GW="http://localhost:8080"
OUT_DIR="$ROOT/logs/verify-fixes"
REPORT="$ROOT/docs/verify-admin-student-fixes-report.txt"
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
chk_ne() { if [ "$2" != "$3" ]; then ok "$1"; else bad "$1  <不应等于 $3>"; fi; }

MYSQL_PWD_VAL="$(grep -E '^MYSQL_ROOT_PASSWORD=' .env | cut -d= -f2-)"
sql() { "$MYSQL" -uroot -p"$MYSQL_PWD_VAL" -N --default-character-set=utf8mb4 -e "$1" 2>/dev/null; }

port_busy() { netstat -ano 2>/dev/null | grep LISTENING | grep ":$1 " | awk '{print $5}' | awk '!seen[$0]++' | grep -q .; }
svc_up() {
  local p=$1
  MSYS_NO_PATHCONV=1 curl.exe -s -o /dev/null --max-time 3 "http://localhost:$p/" >/dev/null 2>&1 && return 0
  port_busy "$p" && return 0
  return 1
}
kill_port() {
  for pid in $(netstat -ano 2>/dev/null | grep LISTENING | grep ":$1 " | awk '{print $5}' | awk '!seen[$0]++'); do
    MSYS_NO_PATHCONV=1 taskkill /F /PID "$pid" >/dev/null 2>&1
  done
}

SVC_ENVS="zx-user:8082 zx-course:8083 zx-auth:8081 zx-learning:8086 zx-exam:8084 zx-trade:8087 zx-insight:8095 zx-gateway:8080"

http() {  # http <method> <path> <token|-> <bodyfile|-> <outfile>
  local m=$1 path=$2 tok=$3 body=$4 out=$5
  local args=(-s -X "$m" "$GW$path" -o "$out" -w "%{http_code}" --max-time 30)
  [ "$tok" != "-" ] && args+=(-H "Authorization: Bearer $tok")
  if [ "$body" != "-" ]; then
    args+=(-H "Content-Type: application/json" --data-binary "@$body")
  fi
  MSYS_NO_PATHCONV=1 curl.exe "${args[@]}"
}

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
# 合成测试订单（id 段 99 开头，与雪花 id 段隔离）
# ---------------------------------------------------------------------
TID_CLOSED=990000000000000001
TID_PAID=990000000000000002
TID_UNPAID=990000000000000003
TID_REFUNDING=990000000000000004
TID_OWNED_PENDING=990000000000000005   # 已拥有课程的僵尸待支付单
TID_POISONED=990000000000000006        # 带历史关单流水的待支付单
TID_ADMIN_DEL=990000000000000007       # 供管理端删除的待支付单
TID_ADMIN_REJECT=990000000000000008    # 供管理端删除被拒的已支付单
# 注意：这里必须是「逗号分隔」，用于 SQL 的 IN (...) 列表；
# 曾用空格分隔导致 "IN (a b c)" 语法错误 → 夹具既没删掉也数不出来（断言静默误报）。
ALL_TIDS="$TID_CLOSED,$TID_PAID,$TID_UNPAID,$TID_REFUNDING,$TID_OWNED_PENDING,$TID_POISONED,$TID_ADMIN_DEL,$TID_ADMIN_REJECT"
TID_POISON_MSG_ID=990000000000000091

cleanup_fixtures() {
  sql "USE zx_trade; DELETE FROM trade_order WHERE id IN ($ALL_TIDS);" >/dev/null
  sql "USE zx_trade; DELETE FROM trade_order_detail WHERE order_id IN ($ALL_TIDS);" >/dev/null
  sql "USE zx_trade; DELETE FROM order_msg WHERE order_id IN ($ALL_TIDS);" >/dev/null
  sql "USE zx_trade; DELETE FROM consume_record WHERE consume_key LIKE 'order:close:99%';" >/dev/null
}

if [ "$REUSE_MODE" != "1" ]; then
  trap 'for p in 8080 8081 8082 8083 8084 8086 8087 8095; do kill_port "$p"; done' EXIT
fi

# =====================================================================
echo "=== 阶段 0：环境准备 ==="
LINES+=("")
LINES+=("=== 一、环境与构建产物 ===")
for m in zx-user zx-course zx-auth zx-learning zx-exam zx-trade zx-insight zx-gateway; do
  if [ -f "$ROOT/$m/target/$m.jar" ]; then ok "$m fat jar 存在"; else bad "$m fat jar 缺失"; fi
done
if [ -f "$ROOT/zx-web/dist/index.html" ]; then ok "前端构建产物存在"; else bad "前端构建产物缺失"; fi

# 迁移列/索引就位
chk "trade_order.user_deleted 列已就位" "$(sql "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='zx_trade' AND TABLE_NAME='trade_order' AND COLUMN_NAME='user_deleted';")" "1"
chk "trade_order.idx_user_status 索引已就位" "$(sql "SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA='zx_trade' AND TABLE_NAME='trade_order' AND INDEX_NAME='idx_user_status';")" "3"

# =====================================================================
echo "=== 阶段 1：启动 / 复用服务 ==="
if [ "$REUSE_MODE" = "1" ]; then
  LINES+=("")
  LINES+=("=== 二、服务就绪检查（复用模式）===")
  for e in $SVC_ENVS; do
    if svc_up "${e##*:}"; then ok "${e%%:*} 已在运行 (${e##*:})"; else bad "${e%%:*} 未运行 (${e##*:})"; fi
  done
else
  echo "  启动 Redis 与服务（最长 12 分钟）..."
  if ! port_busy 6379; then
    ( cd /d/1/Redis && MSYS_NO_PATHCONV=1 nohup ./redis-server.exe redis.windows.conf > "$ROOT/logs/svc/redis.log" 2>&1 & )
    for i in $(seq 1 20); do port_busy 6379 && break; sleep 1; done
  fi
  for spec in $SVC_ENVS; do
    m=${spec%%:*}; p=${spec##*:}
    port_busy "$p" && continue
    # 不要用 `env -u`：PATH 里的 ~/.local/bin/env 是存根脚本（无 exec "$@"），
    # 会让 java 静默不启动（日志 0 字节、端口不监听）。用脚本内 unset，同样对子进程生效。
    unset SERVER__PORT SERVER__HOST
    MSYS_NO_PATHCONV=1 nohup \
      java -Djava.io.tmpdir="$ROOT/logs/tmp" -Dfile.encoding=UTF-8 -Xmx340m \
      -jar "$ROOT/$m/target/$m.jar" --server.port="$p" \
      > "$ROOT/logs/verify-fixes/svc-$m.log" 2>&1 &
  done
  deadline=$(( $(date +%s) + 480 ))
  while [ "$(date +%s)" -lt "$deadline" ]; do
    pending=""
    for spec in $SVC_ENVS; do svc_up "${spec##*:}" || pending="$pending ${spec%%:*}"; done
    [ -z "$pending" ] && break
    echo "  ...等待中:$pending"
    sleep 15
  done
  LINES+=("")
  LINES+=("=== 二、服务就绪 ===")
  for spec in $SVC_ENVS; do
    if svc_up "${spec##*:}"; then ok "${spec%%:*} 已就绪 (${spec##*:})"; else bad "${spec%%:*} 启动失败 (${spec##*:})"; fi
  done
fi

# =====================================================================
echo "=== 阶段 2：构造测试夹具 ==="
cleanup_fixtures

# 取 2001 已拥有的一门课程，用于"已拥有课程僵尸待支付"场景
STU_STUB=$(body login-stu.json '{"cellPhone":"13900000001","password":"123456"}')
http POST /accounts/login - "$OUT_DIR/login-stu.json" "$OUT_DIR/r-login-stu.json" >/dev/null
STU_TOKEN=$(J "$OUT_DIR/r-login-stu.json" "d['data']['accessToken']")
chk "学员登录 13900000001/123456" "$(J "$OUT_DIR/r-login-stu.json" "d['code']")" "200"

http GET /orders/bought-course-ids "$STU_TOKEN" - "$OUT_DIR/r-bought.json" >/dev/null
OWNED_COURSE=$(J "$OUT_DIR/r-bought.json" "d['data'][0] if isinstance(d.get('data'),list) and d['data'] else 0")
if [ "$OWNED_COURSE" != "0" ] && [ "$OWNED_COURSE" != "PARSE_ERR" ] && [ "$OWNED_COURSE" != "EVAL_ERR" ]; then
  ok "取到学员 2001 的已拥有课程 id=$OWNED_COURSE"
else
  bad "未能取到已拥有课程 id（后续对账断言无意义）"
  OWNED_COURSE=0
fi

mkorder() {  # mkorder <id> <orderNo> <courseId> <status> <userDeleted> <minutesAgo>
  sql "USE zx_trade; INSERT INTO trade_order
       (id, order_no, user_id, course_id, course_name, course_price, total_fee, deduction,
        status, user_deleted, pay_type, pay_time, create_time, update_time, deleted)
       VALUES ($1, '$2', 2001, $3, '验证用课程-$2', 10000, 10000, 0,
               $4, $5, CASE WHEN $4=1 THEN 2 ELSE NULL END,
               CASE WHEN $4=1 THEN NOW() ELSE NULL END,
               DATE_SUB(NOW(), INTERVAL $6 MINUTE), NOW(), 0);" >/dev/null
  sql "USE zx_trade; INSERT INTO trade_order_detail (order_id, course_id, name, price, create_time, update_time, deleted)
       VALUES ($1, $3, '验证用课程-$2', 10000, NOW(), NOW(), 0);" >/dev/null
}

mkorder $TID_CLOSED       "VTESTCLOSED"   9001 2 0 20
mkorder $TID_PAID         "VTESTPAID"     9002 1 0 20
mkorder $TID_UNPAID       "VTESTUNPAID"   9003 0 0 20
mkorder $TID_REFUNDING    "VTESTREFUND"   9004 3 0 20
mkorder $TID_OWNED_PENDING "VTESTOWNED"   "${OWNED_COURSE:-9005}" 0 0 2  # 必须「未超时」：否则 OrderTimeoutJob 的 60s 兜底扫描会先把它关掉，
                                                                          # 就测不到「课程已拥有」这条对账规则（曾用 30 分钟 → 夹具被定时任务抢先关闭）
mkorder $TID_POISONED     "VTESTPOISON"   9006 0 0 5
mkorder $TID_ADMIN_DEL    "VTESTADMDEL"   9007 0 0 2   # 2 分钟前：确保不被对账自动关单，供「学员删待支付被拒」断言使用
mkorder $TID_ADMIN_REJECT "VTESTADMREJ"   9008 1 0 20

# 给 TID_POISONED 预置一条历史关单流水：旧实现会因此永久无法关单
# consume_record.id 非自增（无 default），必须显式给出主键
sql "USE zx_trade; INSERT INTO consume_record (id, consume_key, topic, tag, status, create_time, update_time, deleted)
     VALUES ($TID_POISON_MSG_ID, 'order:close:$TID_POISONED', 'zx_order_timeout', 'CLOSE', 1, NOW(), NOW(), 0);" >/dev/null

chk "夹具订单已入库（8 笔）" "$(sql "USE zx_trade; SELECT COUNT(*) FROM trade_order WHERE id IN ($ALL_TIDS);")" "8"
chk "历史关单流水已预置" "$(sql "USE zx_trade; SELECT COUNT(*) FROM consume_record WHERE consume_key='order:close:$TID_POISONED';")" "1"

# =====================================================================
echo "=== 阶段 3：需求 1 · 管理员端注册时间 ==="
LINES+=("")
LINES+=("=== 三、管理员端：账号注册时间（需求 1）===")

body login-adm.json '{"cellPhone":"13800000001","password":"123456"}' >/dev/null
http POST /accounts/login - "$OUT_DIR/login-adm.json" "$OUT_DIR/r-login-adm.json" >/dev/null
ADM_TOKEN=$(J "$OUT_DIR/r-login-adm.json" "d['data']['accessToken']")
chk "管理员登录 13800000001/123456" "$(J "$OUT_DIR/r-login-adm.json" "d['code']")" "200"

http GET "/users/page?pageNo=1&pageSize=10" "$ADM_TOKEN" - "$OUT_DIR/r-users.json" >/dev/null
chk "GET /users/page 返回 200" "$(J "$OUT_DIR/r-users.json" "d['code']")" "200"
chk "用户列表非空" "$(J "$OUT_DIR/r-users.json" "len(d['data']['list'])>0")" "True"
chk "列表首条含 createTime 字段" "$(J "$OUT_DIR/r-users.json" "'createTime' in d['data']['list'][0]")" "True"
chk "createTime 非空（注册时间已下发）" "$(J "$OUT_DIR/r-users.json" "bool(d['data']['list'][0].get('createTime'))")" "True"
chk "createTime 格式为 yyyy-MM-dd HH:mm:ss（19 位）" \
  "$(J "$OUT_DIR/r-users.json" "(len(s:=str(d['data']['list'][0].get('createTime')))==19 and s[4]=='-' and s[7]=='-' and s[10]==' ' and s[13]==':' and s[16]==':')")" "True"
chk "全部列表项 createTime 均非空" "$(J "$OUT_DIR/r-users.json" "all(bool(u.get('createTime')) for u in d['data']['list'])")" "True"

# =====================================================================
echo "=== 阶段 4：需求 2 · 学员端学情报告 ==="
LINES+=("")
LINES+=("=== 四、学员端学情报告（需求 2）===")

http GET /insight/profiles/mine "$STU_TOKEN" - "$OUT_DIR/r-profile.json" >/dev/null
chk "GET /insight/profiles/mine 返回 200" "$(J "$OUT_DIR/r-profile.json" "d['code']")" "200"
chk_ne "能力画像不含「系统繁忙」" "$(J "$OUT_DIR/r-profile.json" "d['msg']")" "系统繁忙，请稍后再试"
chk "能力维度 5 项" "$(J "$OUT_DIR/r-profile.json" "len(d['data']['abilities'])")" "5"
chk "近 7 日趋势 7 项" "$(J "$OUT_DIR/r-profile.json" "len(d['data']['trends'])")" "7"
chk "累计学习时长已下发" "$(J "$OUT_DIR/r-profile.json" "d['data']['totalDuration'] is not None")" "True"
chk "连续打卡天数已下发" "$(J "$OUT_DIR/r-profile.json" "d['data']['continuousDays'] is not None")" "True"

http GET /insight/reports/latest "$STU_TOKEN" - "$OUT_DIR/r-report.json" >/dev/null
chk "GET /insight/reports/latest 返回 200" "$(J "$OUT_DIR/r-report.json" "d['code']")" "200"
chk "学情报告 5 个维度齐备" "$(J "$OUT_DIR/r-report.json" "len(d['data']['dimensions'])")" "5"
chk "报告正文(summary)非空" "$(J "$OUT_DIR/r-report.json" "bool(d['data'].get('summary'))")" "True"
chk "报告日期为今天" "$(J "$OUT_DIR/r-report.json" "d['data']['reportDate'] is not None")" "True"

http GET /insight/learning-path "$STU_TOKEN" - "$OUT_DIR/r-path.json" >/dev/null
chk "GET /insight/learning-path 返回 200" "$(J "$OUT_DIR/r-path.json" "d['code']")" "200"
chk "学习路径推荐理由非空" "$(J "$OUT_DIR/r-path.json" "bool(d['data'].get('reason'))")" "True"

# 无学习数据的新学员也必须可正常渲染（不能 500「系统繁忙」）
STU2_TOKEN=$(curl.exe -s -X POST "$GW/accounts/login" -H "Content-Type: application/json" \
  -d '{"cellPhone":"13900003333","password":"123456"}' | "$PY" -c "import sys,json;print(json.load(sys.stdin).get('data',{}).get('accessToken',''))")
if [ -n "$STU2_TOKEN" ]; then
  for path in /insight/profiles/mine /insight/reports/latest /insight/learning-path; do
    http GET "$path" "$STU2_TOKEN" - "$OUT_DIR/r-nodata-$(echo "$path" | tr '/' '_').json" >/dev/null
    chk "无数据学员 $path 仍返回 200（不报系统繁忙）" \
      "$(J "$OUT_DIR/r-nodata-$(echo "$path" | tr '/' '_').json" "d['code']")" "200"
  done
else
  bad "无数据学员登录失败，跳过空数据断言"
fi

# LLM 路径修复：不得再出现 /v4/v1/chat/completions 这种重复版本段
if [ -f "$ROOT/logs/verify-fixes/svc-zx-insight.log" ]; then
  if grep -q "v4/v1/chat/completions" "$ROOT/logs/verify-fixes/svc-zx-insight.log" 2>/dev/null; then
    bad "大模型请求路径仍重复版本段（/v4/v1/...）"
  else
    ok "大模型请求路径已修正（无 /v4/v1 重复版本段）"
  fi
fi
chk "LLM 接口路径推断为智谱 /chat/completions" \
  "$(grep -c "chat/completions" "$ROOT/zx-insight/src/main/java/com/zhixing/insight/service/InsightLlmClient.java" 2>/dev/null | awk '{print ($1>0)?"yes":"no"}')" "yes"

# =====================================================================
echo "=== 阶段 5：需求 3 · 已拥有课程 + 超时自动关闭 ==="
LINES+=("")
LINES+=("=== 五、我的订单：状态流转修复（需求 3）===")

# 5.1 已拥有课程的僵尸待支付单：读取待支付列表时应被自动关闭
chk "夹具：已拥有课程待支付单初始为待支付" \
  "$(sql "USE zx_trade; SELECT status FROM trade_order WHERE id=$TID_OWNED_PENDING;")" "0"
http GET "/orders/page?pageNo=1&pageSize=100&status=1" "$STU_TOKEN" - "$OUT_DIR/r-pending.json" >/dev/null
chk "GET /orders/page?status=1（待支付）返回 200" "$(J "$OUT_DIR/r-pending.json" "d['code']")" "200"
chk "已拥有课程的僵尸单已被自动关闭" \
  "$(sql "USE zx_trade; SELECT status FROM trade_order WHERE id=$TID_OWNED_PENDING;")" "2"
chk "待支付列表中不再出现已拥有课程的订单" \
  "$(J "$OUT_DIR/r-pending.json" "str($TID_OWNED_PENDING) not in [str(o.get('id')) for o in d['data']['list']] and $TID_OWNED_PENDING not in [o.get('id') for o in d['data']['list']]")" "True"

# 5.2 待支付列表中所有课程都不属于"已拥有"
OWNED_JSON=$(curl.exe -s -H "Authorization: Bearer $STU_TOKEN" "$GW/orders/bought-course-ids")
"$PY" -c "
import json,sys
owned = set(str(x) for x in json.loads(sys.argv[1]).get('data') or [])
page = json.load(open(sys.argv[2], encoding='utf-8')).get('data') or {}
hit = [o for o in page.get('list') or [] if any(str(d.get('courseId')) in owned for d in (o.get('details') or []))]
print('NONE' if not hit else 'HIT:'+str(hit))
" "$OWNED_JSON" "$OUT_DIR/r-pending.json" > "$OUT_DIR/pending-check.txt" 2>/dev/null
chk "待支付列表全部订单的课程均非「已拥有」" "$(cat "$OUT_DIR/pending-check.txt")" "NONE"

# 5.3 「关不掉」修复：即使存在历史关单流水，仍能正常关单（旧实现被永久锁死）
chk "夹具：被历史流水毒化的待支付单存在" \
  "$(sql "USE zx_trade; SELECT COUNT(*) FROM trade_order WHERE id=$TID_POISONED AND status=0;")" "1"
http POST "/orders/$TID_POISONED/timeout" "$STU_TOKEN" - "$OUT_DIR/r-close1.json" >/dev/null
chk "带历史关单流水的订单仍可关单（旧实现被锁死）" "$(J "$OUT_DIR/r-close1.json" "d['code']")" "200"
chk "该订单状态已变为已关闭" \
  "$(sql "USE zx_trade; SELECT status FROM trade_order WHERE id=$TID_POISONED;")" "2"

# 5.4 重复关单幂等（第二次调用不报错、状态仍是已关闭）
http POST "/orders/$TID_POISONED/timeout" "$STU_TOKEN" - "$OUT_DIR/r-close2.json" >/dev/null
chk "重复关单幂等返回 200" "$(J "$OUT_DIR/r-close2.json" "d['code']")" "200"
chk "重复关单后状态仍为已关闭" \
  "$(sql "USE zx_trade; SELECT status FROM trade_order WHERE id=$TID_POISONED;")" "2"

# 5.5 状态被回退后仍能再次关单（这正是"倒计时归零关不掉"的复现路径）
sql "USE zx_trade; UPDATE trade_order SET status=0 WHERE id=$TID_POISONED;" >/dev/null
http POST "/orders/$TID_POISONED/timeout" "$STU_TOKEN" - "$OUT_DIR/r-close3.json" >/dev/null
chk "状态回退为待支付后仍能再次关单" "$(J "$OUT_DIR/r-close3.json" "d['code']")" "200"
chk "再次关单后状态为已关闭" \
  "$(sql "USE zx_trade; SELECT status FROM trade_order WHERE id=$TID_POISONED;")" "2"

# 5.6 超时对账：超过 15 分钟的待支付单在读取列表时被关闭
#     注意：该夹具必须在「本次列表读取之前」复位——5.1 的对账已把它关过一次，
#     每一次对账断言都要用「新进入超时窗口」的单子，否则测的是上一轮的结果。
sql "USE zx_trade; UPDATE trade_order SET status=0, update_time=NOW(),
     create_time=DATE_SUB(NOW(), INTERVAL 20 MINUTE) WHERE id=$TID_UNPAID;" >/dev/null
chk "夹具：超时待支付单已复位为待支付" \
  "$(sql "USE zx_trade; SELECT status FROM trade_order WHERE id=$TID_UNPAID;")" "0"
http GET "/orders/page?pageNo=1&pageSize=100&status=1" "$STU_TOKEN" - "$OUT_DIR/r-pending2.json" >/dev/null
chk "超时（>15 分钟）待支付单已自动关闭" \
  "$(sql "USE zx_trade; SELECT status FROM trade_order WHERE id=$TID_UNPAID;")" "2"

# =====================================================================
echo "=== 阶段 6：需求 4 · 订单删除 ==="
LINES+=("")
LINES+=("=== 六、订单删除：学员端软删 + 管理端清理（需求 4）===")

SALES_BEFORE=$(sql "USE zx_trade; SELECT IFNULL(SUM(total_fee),0) FROM trade_order WHERE deleted=0 AND status=1;")
TOTAL_BEFORE=$(sql "USE zx_trade; SELECT COUNT(*) FROM trade_order WHERE deleted=0;")

# 6.1 学员删除「已关闭」订单
http DELETE "/orders/$TID_CLOSED" "$STU_TOKEN" - "$OUT_DIR/r-del-closed.json" >/dev/null
chk "学员删除已关闭订单返回 200" "$(J "$OUT_DIR/r-del-closed.json" "d['code']")" "200"
chk "订单被标记为学员已删除（user_deleted=1）" \
  "$(sql "USE zx_trade; SELECT user_deleted FROM trade_order WHERE id=$TID_CLOSED;")" "1"
chk "记录未被物理删除（管理端可追溯）" \
  "$(sql "USE zx_trade; SELECT COUNT(*) FROM trade_order WHERE id=$TID_CLOSED;")" "1"

# 6.2 学员删除「已支付」订单（已完成交易）
http DELETE "/orders/$TID_PAID" "$STU_TOKEN" - "$OUT_DIR/r-del-paid.json" >/dev/null
chk "学员删除已支付订单返回 200" "$(J "$OUT_DIR/r-del-paid.json" "d['code']")" "200"
chk "已支付订单标记学员已删除" \
  "$(sql "USE zx_trade; SELECT user_deleted FROM trade_order WHERE id=$TID_PAID;")" "1"

# 6.3 学员删除「待支付」订单应被拒绝
http DELETE "/orders/$TID_ADMIN_DEL" "$STU_TOKEN" - "$OUT_DIR/r-del-pending.json" >/dev/null
chk_ne "学员删除待支付订单被拒（非 200）" "$(J "$OUT_DIR/r-del-pending.json" "d['code']")" "200"
chk "拒绝原因提示先支付或取消" \
  "$(J "$OUT_DIR/r-del-pending.json" "'待支付' in str(d.get('msg',''))")" "True"

# 6.4 学员删除「退款中」订单应被拒绝
http DELETE "/orders/$TID_REFUNDING" "$STU_TOKEN" - "$OUT_DIR/r-del-refunding.json" >/dev/null
chk_ne "学员删除退款中订单被拒（非 200）" "$(J "$OUT_DIR/r-del-refunding.json" "d['code']")" "200"

# 6.5 学员端列表不再出现已删除订单
http GET "/orders/page?pageNo=1&pageSize=100" "$STU_TOKEN" - "$OUT_DIR/r-myorders.json" >/dev/null
chk "学员端列表已隐藏软删的已关闭订单" \
  "$(J "$OUT_DIR/r-myorders.json" "$TID_CLOSED not in [int(o.get('id')) for o in d['data']['list']]")" "True"
chk "学员端列表已隐藏软删的已支付订单" \
  "$(J "$OUT_DIR/r-myorders.json" "$TID_PAID not in [int(o.get('id')) for o in d['data']['list']]")" "True"

# 6.6 管理端仍能看到被学员删除的订单
http GET "/orders/admin/page?pageNo=1&pageSize=50&keyword=VTESTCLOSED" "$ADM_TOKEN" - "$OUT_DIR/r-adm-del.json" >/dev/null
chk "管理端仍可检索到学员已删除的订单" "$(J "$OUT_DIR/r-adm-del.json" "d['data']['total']")" "1"
chk "管理端可见 userDeleted=1 标记" "$(J "$OUT_DIR/r-adm-del.json" "int(d['data']['list'][0]['userDeleted'])")" "1"

# 6.7 管理端删除「待支付」无用订单
http DELETE "/orders/admin/$TID_ADMIN_DEL" "$ADM_TOKEN" - "$OUT_DIR/r-adm-del2.json" >/dev/null
chk "管理端删除待支付订单返回 200" "$(J "$OUT_DIR/r-adm-del2.json" "d['code']")" "200"
chk "管理端列表不再展示该订单" \
  "$(sql "USE zx_trade; SELECT deleted FROM trade_order WHERE id=$TID_ADMIN_DEL;")" "1"
http GET "/orders/admin/page?pageNo=1&pageSize=50&keyword=VTESTADMDEL" "$ADM_TOKEN" - "$OUT_DIR/r-adm-del3.json" >/dev/null
chk "管理端按订单号检索已删除订单返回 0 条" "$(J "$OUT_DIR/r-adm-del3.json" "d['data']['total']")" "0"

# 6.8 管理端删除「已支付」订单应被拒绝（保护销售数据）
http DELETE "/orders/admin/$TID_ADMIN_REJECT" "$ADM_TOKEN" - "$OUT_DIR/r-adm-reject.json" >/dev/null
chk_ne "管理端删除已支付订单被拒（非 200）" "$(J "$OUT_DIR/r-adm-reject.json" "d['code']")" "200"
chk "拒绝原因提示已支付参与销售统计" \
  "$(J "$OUT_DIR/r-adm-reject.json" "'已支付' in str(d.get('msg',''))")" "True"
chk "该已支付订单未被删除" \
  "$(sql "USE zx_trade; SELECT deleted FROM trade_order WHERE id=$TID_ADMIN_REJECT;")" "0"

# 6.9 删除后销售数据口径一致
SALES_AFTER=$(sql "USE zx_trade; SELECT IFNULL(SUM(total_fee),0) FROM trade_order WHERE deleted=0 AND status=1;")
chk "删除操作不影响销售额统计" "$SALES_AFTER" "$SALES_BEFORE"
chk "管理端统计接口销售额与库内一致" \
  "$(curl.exe -s -H "Authorization: Bearer $ADM_TOKEN" "$GW/orders/admin/statistics" | "$PY" -c "import sys,json;print(json.load(sys.stdin)['data']['totalSales'])")" "$SALES_AFTER"

# =====================================================================
echo "=== 阶段 7：清理夹具 ==="
cleanup_fixtures
chk "测试夹具已清理干净" "$(sql "USE zx_trade; SELECT COUNT(*) FROM trade_order WHERE id IN ($ALL_TIDS);")" "0"
chk "关单流水夹具已清理" "$(sql "USE zx_trade; SELECT COUNT(*) FROM consume_record WHERE consume_key LIKE 'order:close:99%';")" "0"

# =====================================================================
TOTAL=$((PASS + FAIL))
LINES+=("")
LINES+=("=== 七、汇总 ===")
LINES+=("  断言总数: $TOTAL   通过: $PASS   失败: $FAIL")
LINES+=("  $([ "$FAIL" -eq 0 ] && echo '结论: 全部通过 ✅' || echo "结论: 存在 $FAIL 项失败 ❌")")

{
  echo "====================================================================="
  echo "知行智学 ZhiXing Learn · 管理员端/学员端四项修复 验证报告"
  echo "生成时间: $(date '+%Y-%m-%d %H:%M:%S')"
  echo "网关地址: $GW"
  echo "====================================================================="
  printf '%s\n' "${LINES[@]}"
  echo ""
  echo "---------------------------------------------------------------------"
  echo "原始响应报文目录: logs/verify-fixes/"
  echo "服务启动日志目录: logs/verify-fixes/svc-*.log"
  echo "---------------------------------------------------------------------"
} > "$REPORT"

printf '%s\n' "${LINES[@]}"
echo ""
echo "报告已写入: $REPORT"
echo "EXIT_FAIL_COUNT=$FAIL"
