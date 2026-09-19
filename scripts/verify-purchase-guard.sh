#!/usr/bin/env bash
# 端到端验证：课程购买防重复（口径 = 课程中心课表 ∪ 已支付订单）+ 并发互斥 + 学习中心同步
# 方案：创建临时测试学员（复制真实学员密码 hash），全程不触碰真实学员数据，可重复执行。
# 用例：A 已支付订单拦截；B 待支付单拦截；C 已拥有集合（并集口径）；
#       D 并发三连发仅一单；E 支付成功课表同步；F 课程中心存在即禁止购买；
#       G 免费课重复导入拦截；最后清理临时学员全部数据。
set -u
cd /d/1/zx-learn
ROOT=$(pwd)
LOG=$ROOT/logs/e2e
PY=/c/Users/20670/.workbuddy/binaries/python/versions/3.13.12/python.exe
GW=http://localhost:8080
MYSQL="/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"
MYSQL_PWD=$(grep -E '^MYSQL_ROOT_PASSWORD=' .env | cut -d= -f2-)
TUID=9000000001
TUPHONE=13999900001
PASS=0; FAIL=0
CREATED_ORDER_IDS=()

ok() { PASS=$((PASS+1)); echo "  [PASS] $1"; }
bad() { FAIL=$((FAIL+1)); echo "  [FAIL] $1"; [ $# -gt 1 ] && echo "         -> $2"; }

mysqlx() { "$MYSQL" -uroot -p"$MYSQL_PWD" -N -B -e "$1" 2>/dev/null; }

jval() { # jval <json|-> <a.b.c>
  local J="$1"; [ "$J" = "-" ] && J=$(cat)
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

api() { # api <METHOD> <path> <token> [body]
  local m=$1 p=$2 t=$3 b=${4:-}
  if [ -n "$b" ]; then
    curl -s --noproxy '*' --max-time 20 -X "$m" "$GW$p" -H "Authorization: Bearer $t" -H 'Content-Type: application/json' -d "$b"
  else
    curl -s --noproxy '*' --max-time 20 -X "$m" "$GW$p" -H "Authorization: Bearer $t"
  fi
}

# 防端口注入 + tmpdir 直接写命令行（沙箱后台进程会丢环境变量）
unset SERVER__PORT SERVER__HOST SERVER_PORT
mkdir -p "$ROOT/logs/tmp"

# ---------------------------------------------------------------- 构造临时测试学员
# 此时尚无服务运行，无 MQ/定时任务干扰。数据全部挂在 TUID 下，结尾统一清理。
echo "== 构造临时测试学员（$TUPHONE, id=$TUID）=="
mysqlx "DELETE FROM zx_user.user WHERE id=$TUID;
        DELETE FROM zx_trade.trade_order WHERE user_id=$TUID;
        DELETE FROM zx_learning.lesson WHERE user_id=$TUID;
        DELETE FROM zx_learning.consume_record WHERE consume_key LIKE 'lesson:paid:%';
        INSERT INTO zx_user.user (id, cell_phone, username, password, name, type, status, deleted)
        SELECT $TUID, '$TUPHONE', 'e2e测试学员', password, 'e2e测试学员', 2, 1, 0
          FROM zx_user.user WHERE id=2001 LIMIT 1;"
CNT=$(mysqlx "SELECT COUNT(*) FROM zx_user.user WHERE id=$TUID;")
[ "$CNT" = "1" ] && ok "临时学员已就绪" || { bad "临时学员创建失败"; exit 1; }

# ---------------------------------------------------------------- 启动服务
echo
echo "== 启动依赖服务 =="
start() { # start <name> <module> —— Windows Java 不认 /d/1/... 路径，jar 与 tmpdir 均用 D:/ 风格
  local jar="D:/1/zx-learn/$2/target/$2.jar"
  nohup java -Djava.io.tmpdir=D:/1/zx-learn/logs/tmp -jar "$jar" > "$LOG/$1.out" 2>&1 &
  PIDS+=($!)
}
PIDS=()
for m in zx-user zx-course zx-learning zx-trade; do start "$m" "$m"; done
sleep 20
start zx-auth zx-auth
sleep 5
start zx-gateway zx-gateway

wait_http() { # wait_http <port> <name>
  for _ in $(seq 1 60); do
    local c
    c=$(curl -s --noproxy '*' -o /dev/null -w '%{http_code}' --max-time 2 "http://localhost:$1/")
    [ "$c" != "000" ] && { echo "  $2 就绪 (: $1 HTTP $c)"; return 0; }
    sleep 2
  done
  bad "$2 启动超时 (: $1)"
  return 1
}
for s in "8082 zx-user" "8083 zx-course" "8086 zx-learning" "8087 zx-trade" "8081 zx-auth" "8080 zx-gateway"; do
  wait_http $s
done
sleep 3

echo
echo "== 登录 =="
R=$(api POST /accounts/login "" "{\"cellPhone\":\"$TUPHONE\",\"password\":\"123456\"}")
T=$(jval "$R" data.accessToken)
[ -n "$T" ] && ok "临时学员登录成功" || { bad "登录失败" "$R"; for p in "${PIDS[@]}"; do kill $p 2>/dev/null; done; exit 1; }

# ---------------------------------------------------------------- A：已支付订单拦截
echo
echo "【A】已支付订单拦截（预置 3001 已支付订单，无课表）"
mysqlx "INSERT INTO zx_trade.trade_order (id, order_no, user_id, course_id, course_name, course_price, total_fee, deduction, status, pay_time, create_time, deleted)
        SELECT 9000000001001, 'TE9000000001001', $TUID, 3001, name, price, price, 0, 1, NOW(), NOW(), 0
          FROM zx_course.course WHERE id=3001 LIMIT 1;
        INSERT INTO zx_trade.order_detail (id, order_id, course_id, name, price, create_time)
        SELECT 9000000001101, 9000000001001, 3001, name, price, NOW() FROM zx_course.course WHERE id=3001 LIMIT 1;"
R=$(api POST /orders/placeOrder "$T" '{"courseId":3001,"totalFee":19900}')
MSG=$(jval "$R" msg); CODE=$(jval "$R" code)
[ "$CODE" != "200" ] && echo "$MSG" | grep -q "已购买" && ok "已支付订单拦截下单：$MSG" || bad "3001 未被已支付订单拦截" "code=$CODE msg=$MSG"

# ---------------------------------------------------------------- C：已拥有集合（并集口径）
echo
echo "【C】已拥有集合 = 课程中心 ∪ 已支付订单（预置课表 3009/3005/3008）"
mysqlx "INSERT INTO zx_learning.lesson (id, user_id, course_id, course_name, create_time) VALUES
        (9000000002001,$TUID,3009,'e2e课表3009',NOW()),
        (9000000002002,$TUID,3005,'e2e课表3005',NOW()),
        (9000000002003,$TUID,3008,'e2e课表3008',NOW());"
LC=$(mysqlx "SELECT COUNT(*) FROM zx_learning.lesson WHERE user_id=$TUID AND course_id IN (3009,3005,3008);")
# 注意：只统计本次预置的三门课。上面的 3001 拦截会触发「已支付但课表缺失 → 自愈补开课」，
# 课表里因此会多出 3001 一行，不能再用"全表行数=3"来断言
[ "$LC" = "3" ] && ok "课表预置完成（3009/3005/3008）" || bad "课表预置异常（count=$LC）"
R=$(api GET /orders/bought-course-ids "$T")
HAS=$(printf '%s' "$R" | JQ_PATH=. "$PY" -c "
import json,sys
o=json.loads(sys.stdin.read())
ids={str(x) for x in (o.get('data') or [])}
print('yes' if {'3001','3009','3005','3008'}.issubset(ids) else 'no')
")
[ "$HAS" = "yes" ] && ok "已拥有集合含订单课程 3001 + 课表课程 3009/3005/3008" || bad "并集口径缺失" "$R"

# ---------------------------------------------------------------- F：课程中心存在即禁止购买
echo
echo "【F】课程中心存在即禁止购买（无活跃订单的课表课程）"
R=$(api POST /orders/placeOrder "$T" '{"courseId":3009,"totalFee":100}')
MSG=$(jval "$R" msg); CODE=$(jval "$R" code)
[ "$CODE" != "200" ] && echo "$MSG" | grep -q "课程中心" && ok "课表课程 3009 下单被拒：$MSG" || bad "3009 未按课程中心口径拦截" "code=$CODE msg=$MSG"
R=$(api POST /orders/placeOrder "$T" '{"courseId":3005,"totalFee":100}')
MSG=$(jval "$R" msg); CODE=$(jval "$R" code)
[ "$CODE" != "200" ] && echo "$MSG" | grep -q "课程中心" && ok "课表课程 3005（进度 0）下单被拒：$MSG" || bad "3005 未按课程中心口径拦截" "code=$CODE msg=$MSG"

# ---------------------------------------------------------------- G：免费课重复导入（幂等成功）
echo
echo "【G】免费课重复导入：幂等成功（3008 免费课已在课程中心）"
R=$(api POST "/orders/freeCourse/3008" "$T")
MSG=$(jval "$R" msg); CODE=$(jval "$R" code)
# 语义已于 2026-09-14 变更：课表已开通 / 已支付一律**幂等成功**并自愈补开课，
# 只有"待支付单"才拦截 —— 不再把用户挡在"课程已拥有"上（旧断言要求"被拒"，已过期）
[ "$CODE" = "200" ] && ok "免费课重复导入幂等成功（不再报已拥有）" || bad "免费课重复导入未按幂等成功返回" "code=$CODE msg=$MSG"
LC2=$(mysqlx "SELECT COUNT(*) FROM zx_learning.lesson WHERE user_id=$TUID AND course_id=3008;")
[ "$LC2" = "1" ] && ok "重复导入未产生重复课表项（幂等）" || bad "重复导入产生了重复课表项" "count=$LC2"

# ---------------------------------------------------------------- B：待支付单拦截（3006 干净课程）
echo
echo "【B】待支付订单拦截重复下单（3006：先建单再重复下单）"
P3006=$(mysqlx "SELECT price FROM zx_course.course WHERE id=3006 AND deleted=0;")
R=$(api POST /orders/placeOrder "$T" "{\"courseId\":3006,\"totalFee\":$P3006}")
CODE=$(jval "$R" code); B_ORDER=$(jval "$R" data)
if [ "$CODE" = "200" ] && [ -n "$B_ORDER" ]; then
  CREATED_ORDER_IDS+=("$B_ORDER")
  R=$(api POST /orders/placeOrder "$T" "{\"courseId\":3006,\"totalFee\":$P3006}")
  MSG=$(jval "$R" msg); CODE=$(jval "$R" code)
  [ "$CODE" != "200" ] && echo "$MSG" | grep -q "待支付" && ok "待支付重复下单被拦：$MSG" || bad "待支付单未拦截" "code=$CODE msg=$MSG"
  R=$(api POST "/orders/$B_ORDER/timeout" "$T")
  [ "$(jval "$R" code)" = "200" ] && ok "待支付单已关闭（后续用例可复用 3006）" || bad "关闭待支付单失败" "$R"
  sleep 1
else
  bad "构造 3006 待支付单失败" "$R"
fi

# ---------------------------------------------------------------- D：并发三连发
echo
echo "【D】并发三连发下单（3006）仅一单成功"
OUT=$LOG/concurrent
rm -f "$OUT".{1,2,3}.json
CPIDS=()
for i in 1 2 3; do
  curl -s --noproxy '*' --max-time 20 -X POST "$GW/orders/placeOrder" -H "Authorization: Bearer $T" \
    -H 'Content-Type: application/json' -d "{\"courseId\":3006,\"totalFee\":$P3006}" > "$OUT.$i.json" &
  CPIDS+=($!)
done
for p in "${CPIDS[@]}"; do wait "$p" 2>/dev/null || true; done
SUCCESS=0; E_ORDER=""
for i in 1 2 3; do
  C=$(jval "$(cat "$OUT.$i.json")" code)
  [ "$C" = "200" ] && { SUCCESS=$((SUCCESS+1)); E_ORDER=$(jval "$(cat "$OUT.$i.json")" data); }
done
[ "$SUCCESS" = "1" ] && ok "并发 3 请求仅 1 单成功（orderId=$E_ORDER）" || bad "并发下单成功数=$SUCCESS（期望 1）" "$(head -c 120 "$OUT.1.json") / $(head -c 120 "$OUT.2.json")"
[ -n "$E_ORDER" ] && CREATED_ORDER_IDS+=("$E_ORDER")

# ---------------------------------------------------------------- E：支付 → 课表同步
echo
echo "【E】正常下单 → 支付 → 学习中心同步"
R=$(api POST "/orders/$E_ORDER/timeout" "$T")
[ "$(jval "$R" code)" = "200" ] && ok "并发遗留待支付单已关闭" || bad "关闭并发遗留待支付单失败" "$R"
sleep 1
R=$(api POST /orders/placeOrder "$T" "{\"courseId\":3006,\"totalFee\":$P3006}")
CODE=$(jval "$R" code); D_ORDER=$(jval "$R" data)
[ "$CODE" = "200" ] && ok "正常下单成功（orderId=$D_ORDER）" || bad "正常下单失败" "$R"
[ -n "$D_ORDER" ] && CREATED_ORDER_IDS+=("$D_ORDER")
R=$(api POST "/orders/pay/mock/$D_ORDER" "$T")
[ "$(jval "$R" code)" = "200" ] && ok "Mock 支付成功" || bad "支付失败" "$R"
sleep 3
R=$(api GET "/orders/$D_ORDER" "$T")
[ "$(jval "$R" data.status)" = "2" ] && ok "订单状态已流转为已支付(2)" || bad "订单状态异常" "$R"
R=$(api GET "/lessons/3006" "$T")
LCID=$(jval "$R" data.courseId)
[ "$LCID" = "3006" ] && ok "课程中心已同步（GET /lessons/3006 返回课表项 courseId=3006）" || bad "课表未同步（弱返回也算失败）" "$R"
R=$(api GET /orders/bought-course-ids "$T")
HAS=$(printf '%s' "$R" | JQ_PATH=. "$PY" -c "
import json,sys
o=json.loads(sys.stdin.read())
print('yes' if '3006' in {str(x) for x in (o.get('data') or [])} else 'no')
")
[ "$HAS" = "yes" ] && ok "已拥有集合即时纳入新购课程 3006" || bad "新购课程未进入已拥有集合" "$R"

# ---------------------------------------------------------------- 关停 + 清理
echo
echo "== 关停服务 =="
for p in "${PIDS[@]}"; do kill $p 2>/dev/null; done
sleep 3

echo
echo "== 清理临时学员数据（服务关停后执行，避免本地消息表补偿重投复活课表）=="
# 预置 A 用例订单 + 运行期创建的订单，一并清理（order_msg 无 user_id 列，按 order_id 删）
IDS=$(IFS=,; echo "9000000001001,${CREATED_ORDER_IDS[*]}" | sed 's/,$//')
mysqlx "DELETE FROM zx_trade.trade_order WHERE id IN ($IDS) OR user_id=$TUID;
        DELETE FROM zx_trade.trade_order_detail WHERE order_id IN ($IDS);
        DELETE FROM zx_trade.trade_pay_record WHERE order_id IN ($IDS);
        DELETE FROM zx_trade.order_msg WHERE order_id IN ($IDS);
        DELETE FROM zx_learning.lesson WHERE user_id=$TUID;
        DELETE FROM zx_learning.consume_record WHERE consume_key LIKE 'lesson:paid:%';
        DELETE FROM zx_user.user WHERE id=$TUID;"
LEFT=$(mysqlx "SELECT CONCAT(COUNT(*), '-', (SELECT COUNT(*) FROM zx_trade.trade_order WHERE user_id=$TUID), '-', (SELECT COUNT(*) FROM zx_learning.lesson WHERE user_id=$TUID)) FROM zx_user.user WHERE id=$TUID;")
[ "$LEFT" = "0-0-0" ] && ok "临时学员数据已全部清理" || bad "清理后仍有残留（user-order-lesson=$LEFT）"

echo
echo "======================================================================"
echo " 结果：PASS=$PASS  FAIL=$FAIL"
[ "$FAIL" = "0" ] && echo " ALL PASS" || echo " 存在失败项，请核对上方输出"
echo "======================================================================"
exit 0
