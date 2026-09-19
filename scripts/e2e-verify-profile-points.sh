#!/usr/bin/env bash
# =====================================================================
# 一次性端到端验证：个人中心 / 积分体系 / 课程讨论 / 账号状态管理
#
#   覆盖：
#     1) 三角色登录
#     2) 个人中心数据：积分概况 / 学习积分排行榜（前 10 + 本人排名）/ 积分明细
#     3) 完成课程学习自动加分（小节完成 +10）
#     4) 完成测验自动加分（答对 +5 / 题）
#     5) 参与讨论自动加分（发帖 +5 / 回复 +2）
#     6) 课程内容页数据：课程目录两级结构（章 → 小节）下发
#     7) 账号状态管理：管理员禁用 → 被禁用账号登录被拦截（业务码 423）→ 启用后恢复
#     8) 重置密码固定为 123456
#     9) 越权防护：学员访问积分授予 / 状态修改接口被拒
#
# 用法（Git Bash）：/usr/bin/bash scripts/e2e-verify-profile-points.sh
# =====================================================================
set -u
ROOT=/d/1/zx-learn
cd "$ROOT" || exit 1
PY="/c/Users/20670/.workbuddy/binaries/python/versions/3.13.12/python.exe"
MYSQL="/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"
LOG="$ROOT/logs/e2e-profile"
mkdir -p "$LOG" "$ROOT/logs/tmp"
GW=http://localhost:8080

# 防端口注入：宿主终端注入的 SERVER__PORT 会被 Spring 松散绑定覆盖 server.port
unset SERVER__PORT SERVER__HOST SERVER_PORT
# 沙箱内后台进程会丢失 JAVA_TOOL_OPTIONS，故 -Djava.io.tmpdir 必须写在 java 命令行上
JVM_TMPDIR="-Djava.io.tmpdir=D:/1/zx-learn/logs/tmp"

MYSQL_ROOT_PASSWORD=$(grep -E "^MYSQL_ROOT_PASSWORD=" "$ROOT/.env" | cut -d= -f2-)
mysqlx() { "$MYSQL" -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B -e "$1" 2>/dev/null; }

PASS=0; FAIL=0
ok() { PASS=$((PASS + 1)); echo "  [PASS] $1"; }
bad() { FAIL=$((FAIL + 1)); echo "  [FAIL] $1   ${2:-}"; }

jval() { # jval <json|-> <a.b.c>
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

# 积分总额（个人中心「我的数据」的核心指标）
pts() { api GET /points/summary "$T_STU" | jval - data.points; }
# JSON 数组长度
jlen() { # jlen <json> <a.b.c 指向数组>
  local J="$1"
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
print(len(v) if isinstance(v,list) else -1)
"
}

# ---------------------------------------------------------------- 启动服务
echo "== 启动依赖服务 =="
PIDS=()
start() { nohup java "$JVM_TMPDIR" -jar "$ROOT/$1/target/$1.jar" > "$LOG/$1.out" 2>&1 & PIDS+=($!); }
for m in zx-user zx-course zx-learning zx-exam zx-auth; do start "$m"; done
sleep 28
start zx-gateway

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
wait_health 8084 zx-exam
wait_health 8081 zx-auth
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
login() { api POST /accounts/login "" "{\"cellPhone\":\"$1\",\"password\":\"${2:-123456}\"}"; }
R=$(login 13800000001); T_ADMIN=$(jval "$R" data.accessToken)
R=$(login 13900000001); T_STU=$(jval "$R" data.accessToken)
R=$(login 13900000002); T_TEA=$(jval "$R" data.accessToken)
[ -n "$T_ADMIN" ] && ok "管理员登录成功" || bad "管理员登录失败" "$R"
[ -n "$T_STU" ] && ok "学员登录成功" || bad "学员登录失败" "$R"
[ -n "$T_TEA" ] && ok "教师登录成功" || bad "教师登录失败" "$R"

# ---------------------------------------------------------------- 个人中心
echo
echo "【2】个人中心：积分概况 / 排行榜 / 积分明细"
R=$(api GET /points/summary "$T_STU")
P_BASE=$(jval "$R" data.points); RANK=$(jval "$R" data.rank); TU=$(jval "$R" data.totalUsers)
if [ "$(jval "$R" code)" = "200" ] && [ -n "$P_BASE" ] && [ "$P_BASE" -gt 0 ] && [ "$RANK" -gt 0 ] && [ "$TU" -gt 0 ]; then
  ok "积分概况：当前积分=$P_BASE，我的排名=第 $RANK 名 / 共 $TU 人"
else
  bad "积分概况异常" "$(echo "$R" | head -c 240)"
fi

R=$(api GET "/points/rank?top=10" "$T_STU")
RN=$(jlen "$R" data.top)
ME_RANK=$(jval "$R" data.me.rank)
TOPMOST=$(jval "$R" data.top.0.points)
if [ "$RN" -gt 0 ] && [ "$RN" -le 10 ] && [ -n "$ME_RANK" ]; then
  ok "排行榜返回 $RN 条（<=10），本人排名条目 rank=$ME_RANK，榜首积分=$TOPMOST"
else
  bad "排行榜结构异常" "$(echo "$R" | head -c 240)"
fi
# 降序校验
DESC=$(R_JSON="$R" "$PY" -c "
import json,os
o=json.loads(os.environ['R_JSON'])
rows=(o.get('data') or {}).get('top') or []
vals=[r.get('points') for r in rows]
print('Y' if vals==sorted(vals,reverse=True) else 'N')
")
[ "$DESC" = "Y" ] && ok "排行榜按积分降序排列" || bad "排行榜排序错误" "points=$DESC"

R=$(api GET "/points/records/page?pageNo=1&pageSize=10" "$T_STU")
RC=$(jlen "$R" data.list)
SRC1=$(jval "$R" data.list.0.sourceText)
if [ "$RC" -gt 0 ] && [ -n "$SRC1" ]; then
  ok "积分明细分页返回 $RC 条，来源文案示例：$SRC1"
else
  bad "积分明细异常" "$(echo "$R" | head -c 240)"
fi

# 非学员不可查看我的积分（学员专属）
C=$(api GET /points/summary "$T_TEA" | jval - code)
[ "$C" = "403" ] && ok "教师访问学员积分接口被拒(403)" || bad "积分接口越权未拦截" "code=$C"

# ---------------------------------------------------------------- 完成课程学习加分
echo
echo "【3】完成课程学习 → 积分自动更新（+10）"
COURSE_T=3011; LESSON_T=99991
mysqlx "DELETE FROM zx_learning.learning_record WHERE user_id=2001 AND course_id=$COURSE_T AND lesson_id=$LESSON_T;"
mysqlx "DELETE FROM zx_learning.points_record WHERE user_id=2001 AND ref_id='$COURSE_T:$LESSON_T';"
P0=$(pts)
R=$(api POST /learning-records/progress "$T_STU" "{\"courseId\":$COURSE_T,\"lessonId\":$LESSON_T,\"progress\":100,\"learnDuration\":600}")
P1=$(pts)
if [ "$(jval "$R" code)" = "200" ] && [ "$((P1 - P0))" = "10" ]; then
  ok "完成小节学习后积分 $P0 → $P1（+10）"
else
  bad "完成学习未加分" "code=$(jval "$R" code) $P0 → $P1 $(echo "$R" | head -c 160)"
fi
# 幂等：重复提交同一小节不应重复加分
R=$(api POST /learning-records/progress "$T_STU" "{\"courseId\":$COURSE_T,\"lessonId\":$LESSON_T,\"progress\":100,\"learnDuration\":10}")
P2=$(pts)
[ "$P2" = "$P1" ] && ok "重复完成同一小节不再重复加分（幂等生效）" || bad "积分幂等失效" "$P1 → $P2"

# ---------------------------------------------------------------- 完成测验加分
echo
echo "【4】完成测验 → 积分自动更新（答对 +5/题）"
QROW=$(mysqlx "SELECT id FROM zx_exam.question WHERE status=1 AND deleted=0 AND answer IS NOT NULL AND answer<>'' ORDER BY id LIMIT 1;")
QID=$(printf '%s' "$QROW" | awk '{print $1}')
ANS=$(mysqlx "SELECT answer FROM zx_exam.question WHERE id=$QID;")
if [ -n "$QID" ] && [ -n "$ANS" ]; then
  P0=$(pts)
  R=$(api POST /question-results "$T_STU" "[{\"questionId\":$QID,\"userAnswer\":\"$ANS\"}]")
  P1=$(pts)
  CORRECT=$(jval "$R" data.0.correct)
  if [ "$(jval "$R" code)" = "200" ] && [ "$CORRECT" = "True" ] && [ "$((P1 - P0))" = "5" ]; then
    ok "测验答对（题目 $QID，答案 $ANS）后积分 $P0 → $P1（+5）"
  else
    bad "测验加分异常" "correct=$CORRECT $P0 → $P1 $(echo "$R" | head -c 200)"
  fi
else
  bad "题库无已发布题目，无法验证测验加分" "QID=$QID"
fi

# ---------------------------------------------------------------- 参与讨论加分
echo
echo "【5】参与讨论 → 积分自动更新（发帖 +5 / 回复 +2）"
P0=$(pts)
R=$(api POST /boards "$T_STU" "{\"courseId\":3001,\"title\":\"E2E 自动验证话题\",\"content\":\"验证参与讨论自动加分。\"}")
BID=$(jval "$R" data)
P1=$(pts)
if [ "$(jval "$R" code)" = "200" ] && [ "$((P1 - P0))" = "5" ]; then
  ok "发布话题后积分 $P0 → $P1（+5），话题 id=$BID"
else
  bad "发帖加分异常" "$(echo "$R" | head -c 200) $P0 → $P1"
fi

P0=$(pts)
R=$(api POST /replies "$T_STU" "{\"boardId\":\"$BID\",\"content\":\"E2E 自动验证回复。\"}")
RID=$(jval "$R" data)
P1=$(pts)
if [ "$(jval "$R" code)" = "200" ] && [ "$((P1 - P0))" = "2" ]; then
  ok "回复讨论后积分 $P0 → $P1（+2），回复 id=$RID"
else
  bad "回复加分异常" "$(echo "$R" | head -c 200) $P0 → $P1"
fi

R=$(api GET "/boards/page?courseId=3001&pageNo=1&pageSize=20" "$T_STU")
HIT=$(R_JSON="$R" BID="$BID" "$PY" -c "
import json,os
o=json.loads(os.environ['R_JSON'])
rows=(o.get('data') or {}).get('list') or []
m=[r for r in rows if str(r.get('id'))==os.environ['BID']]
print(m[0].get('replyCount') if m else '')
")
[ "$HIT" = "1" ] && ok "讨论列表可见该话题且回复数为 1" || bad "讨论列表/回复数异常" "replyCount=$HIT"
R=$(api GET "/boards/$BID" "$T_STU")
RL=$(jlen "$R" data.replies)
[ "$RL" = "1" ] && ok "话题详情返回 1 条回复" || bad "话题详情回复异常" "replies=$RL"
[ -n "$RID" ] && { R=$(api DELETE "/replies/$RID" "$T_STU"); [ "$(jval "$R" code)" = "200" ] && ok "作者可删除自己的回复" || bad "删除回复失败" "$(echo "$R" | head -c 200)"; }

# ---------------------------------------------------------------- 课程内容页数据
echo
echo "【6】课程内容页：章节目录两级结构下发"
R=$(api GET "/courses/3001" "$T_STU")
CN=$(jlen "$R" data.catalogues)
S0=$(jlen "$R" data.catalogues.0.sections)
if [ "$(jval "$R" code)" = "200" ] && [ "$CN" -gt 0 ] && [ "$S0" -gt 0 ]; then
  C0=$(jval "$R" data.catalogues.0.name); S0N=$(jval "$R" data.catalogues.0.sections.0.name)
  ok "课程 3001 返回 $CN 个章节，首章「$C0」下含 $S0 个小节，首节「$S0N」"
else
  bad "课程目录未组装成两级结构" "chapters=$CN sections=$S0 $(echo "$R" | head -c 200)"
fi

# ---------------------------------------------------------------- 账号状态管理
echo
echo "【7】账号状态管理：管理员禁用 → 登录被拦截（业务码 423）"
TS=$(date +%s)
NEW_PHONE="137$(printf '%08d' $((TS % 100000000)))"
R=$(api POST /users "$T_ADMIN" "{\"cellPhone\":\"$NEW_PHONE\",\"username\":\"e2e_status_$TS\",\"password\":\"Abcd1234\",\"type\":2}")
TMP_ID=$(mysqlx "SELECT id FROM zx_user.user WHERE cell_phone='$NEW_PHONE' AND deleted=0 LIMIT 1;")
if [ "$(jval "$R" code)" = "200" ] && [ -n "$TMP_ID" ]; then
  ok "创建临时学员（id=$TMP_ID, $NEW_PHONE）"
else
  bad "创建临时学员失败" "$(echo "$R" | head -c 200)"
fi

# 禁用前可正常登录
R=$(login "$NEW_PHONE" Abcd1234)
[ -n "$(jval "$R" data.accessToken)" ] && ok "禁用前该账号可正常登录" || bad "禁用前登录失败" "$(echo "$R" | head -c 200)"

R=$(api PUT "/users/$TMP_ID/status/0" "$T_ADMIN")
[ "$(jval "$R" code)" = "200" ] && ok "管理员禁用账号成功（status=0）" || bad "禁用失败" "$(echo "$R" | head -c 200)"

R=$(login "$NEW_PHONE" Abcd1234)
C=$(jval "$R" code); M=$(jval "$R" msg)
if [ "$C" = "423" ] && [ -n "$M" ]; then
  ok "被禁用账号登录被拦截（业务码 423，提示：$M）"
else
  bad "禁用账号未被拦截" "code=$C msg=$M $(echo "$R" | head -c 200)"
fi

R=$(api PUT "/users/$TMP_ID/status/1" "$T_ADMIN")
R=$(login "$NEW_PHONE" Abcd1234)
[ -n "$(jval "$R" data.accessToken)" ] && ok "管理员启用后账号恢复登录" || bad "启用后仍无法登录" "$(echo "$R" | head -c 200)"

# 预置的禁用账号（test-data: 2107 status=0）同样应被拦截
R=$(login 13900000207 123456)
[ "$(jval "$R" code)" = "423" ] && ok "预置禁用账号 13900000207 登录被拦截(423)" || bad "预置禁用账号未拦截" "code=$(jval "$R" code)"

# 自我保护：管理员禁用自己
SELF_ID=$(mysqlx "SELECT id FROM zx_user.user WHERE cell_phone='13800000001' AND deleted=0 LIMIT 1;")
R=$(api PUT "/users/$SELF_ID/status/0" "$T_ADMIN")
C=$(jval "$R" code)
if [ "$C" != "200" ]; then ok "禁止禁用当前登录账号：$(jval "$R" msg)"; else bad "禁用本人未被拦截" "$R"; fi

# 学员无权修改账号状态
R=$(api PUT "/users/$TMP_ID/status/0" "$T_STU")
C=$(jval "$R" code)
if [ "$C" = "403" ] || [ "$C" = "401" ]; then ok "学员修改账号状态被拒($C)"; else bad "状态修改越权未拦截" "code=$C"; fi

# ---------------------------------------------------------------- 重置密码
echo
echo "【8】重置密码固定为 123456"
R=$(api PUT "/users/$TMP_ID/password/default" "$T_ADMIN")
if [ "$(jval "$R" code)" = "200" ]; then
  ok "管理员重置密码成功（不再报"未配置 ZX_USER_DEFAULT_PASSWORD"）"
else
  bad "重置密码失败" "$(echo "$R" | head -c 240)"
fi
R=$(login "$NEW_PHONE" 123456)
[ -n "$(jval "$R" data.accessToken)" ] && ok "重置后可用统一密码 123456 登录" || bad "重置后 123456 登录失败" "$(echo "$R" | head -c 200)"

# ---------------------------------------------------------------- 越权防护
echo
echo "【9】越权防护（内部积分接口不对网关开放）"
R=$(api POST /points/award "$T_STU" '{"userId":2001,"source":"QUIZ","points":9999,"refId":"hack"}')
C=$(jval "$R" code)
if [ "$C" = "403" ]; then ok "学员调用积分授予接口被拒(403)：$(jval "$R" msg)"; else bad "积分授予接口越权未拦截" "code=$C $(echo "$R" | head -c 200)"; fi
R=$(api POST /points/award "$T_ADMIN" '{"userId":2001,"source":"QUIZ","points":9999,"refId":"hack"}')
C=$(jval "$R" code)
[ "$C" = "403" ] && ok "管理员经网关调用内部积分接口同样被拒(403)" || bad "内部接口对管理员开放" "code=$C"
PHACK=$(printf '%s' "$(api GET /points/summary "$T_STU")" | jval - data.points)
[ "$PHACK" -lt 9000 ] && ok "积分未被非法注入（当前 $PHACK）" || bad "积分异常暴涨" "points=$PHACK"

# ---------------------------------------------------------------- 清理
echo
echo "【10】清理本次验证产生的数据"
mysqlx "DELETE FROM zx_learning.points_record WHERE user_id=2001 AND ref_id IN ('$COURSE_T:$LESSON_T','$BID','$RID');"
mysqlx "DELETE FROM zx_learning.learning_record WHERE user_id=2001 AND course_id=$COURSE_T AND lesson_id=$LESSON_T;"
mysqlx "DELETE FROM zx_learning.board_reply WHERE board_id=$BID;"
mysqlx "DELETE FROM zx_learning.board WHERE id=$BID;"
mysqlx "DELETE FROM zx_exam.question_result WHERE user_id=2001 AND create_time > NOW() - INTERVAL 15 MINUTE;"
[ -n "${TMP_ID:-}" ] && { R=$(api DELETE "/users/$TMP_ID" "$T_ADMIN"); ok "已删除临时学员（id=$TMP_ID）"; }
LEFT=$(mysqlx "SELECT (SELECT COUNT(*) FROM zx_learning.board WHERE id=$BID) + (SELECT COUNT(*) FROM zx_learning.points_record WHERE ref_id IN ('$COURSE_T:$LESSON_T','$BID','$RID'));")
[ "$LEFT" = "0" ] && ok "验证数据已清理（话题 / 积分明细 / 学习记录）" || bad "清理不完整" "leftover=$LEFT"

echo
echo "=============================================================="
echo " 结果：PASS=$PASS  FAIL=$FAIL"
echo "=============================================================="
[ "$FAIL" = "0" ] && echo "ALL PASS" || echo "HAS FAILURE"
