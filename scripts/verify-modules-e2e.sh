#!/usr/bin/env bash
# =====================================================================
# 知行智学 · 五大模块端到端验证（自包含：启服务 → 断言 → 关停 → 出报告）
# ---------------------------------------------------------------------
# 覆盖需求：
#   1. 个人中心 / 积分中心：概览、排行榜（前 10 + 本人排名）、积分明细、自动加分
#   2. 我的课表 / 课程内容：课表可访问、课程详情含两级目录（章节 + 小节）
#   3. 重置密码：管理员重置接口可用且不再报"未配置 ZX_USER_DEFAULT_PASSWORD"
#   4. 账号状态管理：管理员启用/禁用；禁用后登录被拦截（业务码 423）
#   5. 前端样式：构建产物存在 + 全局溢出兜底样式生效
#
# 运行（沙箱/Windows Git Bash）：
#   /usr/bin/bash scripts/verify-modules-e2e.sh          # 完整：冷启动 6 服务 → 断言 → 关停
#   REUSE=1 /usr/bin/bash scripts/verify-modules-e2e.sh  # 快速：复用已运行服务，只跑断言（约 30 秒）
#
# 设计说明：
#   * 端口已占用时视为"复用"；为验证最新代码，仍会先停掉再以最新 fat jar 启动。
#   * 后台任务里环境变量可能丢失，故 -D 参数一律直接写在 java 命令行。
#   * java.io.tmpdir 必须用 Windows 风格路径（D:/...），Unix 路径 Windows JVM 不认。
# =====================================================================
set -u

ROOT="D:/1/zx-learn"
PY="C:/Users/20670/.workbuddy/binaries/python/versions/3.13.12/python.exe"
GW="http://localhost:8080"
OUT_DIR="$ROOT/logs/e2e-modules"
REPORT="$ROOT/docs/e2e-modules-report.txt"

cd "$ROOT" || exit 1
mkdir -p "$OUT_DIR" "$ROOT/logs/tmp" "$ROOT/docs"

PASS=0
FAIL=0
declare -a LINES=()
declare -a STARTED_PORTS=()

# REUSE=1 时复用已在运行的服务，跳过启动与关停，只跑断言（用于增量回归）
REUSE_MODE="${REUSE:-0}"

ok()  { PASS=$((PASS + 1)); LINES+=("  [PASS] $1"); }
bad() { FAIL=$((FAIL + 1)); LINES+=("  [FAIL] $1"); }
info(){ LINES+=("  [INFO] $1"); }
chk() { if [ "$2" = "$3" ]; then ok "$1"; else bad "$1  <期望=$3 实际=$2>"; fi; }

# ---------------------------------------------------------------------
# 端口 / 进程工具
# ---------------------------------------------------------------------
# 坑：本沙箱的 PATH 里 /tmp/system32 用 Windows 版程序遮蔽了部分 GNU 工具
#     （sort.exe / find.exe）。Windows sort 不支持 -u，会报"无效命令行开关"，
#     导致管道输出为空、端口探测恒为"未占用"。故此处用 awk 去重，禁用 sort/find。
port_pids() {
  netstat -ano 2>/dev/null | grep LISTENING | grep ":$1 " | awk '{print $5}' | awk '!seen[$0]++'
}

port_busy() { [ -n "$(port_pids "$1")" ]; }

kill_port() {
  local p=$1
  for pid in $(port_pids "$p"); do
    MSYS_NO_PATHCONV=1 taskkill /F /PID "$pid" >/dev/null 2>&1
  done
  sleep 1
}

# 就绪判定：优先 HTTP 可达（真正验证服务能响应），端口监听作为兜底
svc_up() {
  local p=$1
  MSYS_NO_PATHCONV=1 curl.exe -s -o /dev/null --max-time 2 "http://localhost:$p/" >/dev/null 2>&1 && return 0
  port_busy "$p" && return 0
  return 1
}

# 沙箱内 JVM 冷启动从 spawn 到真正监听可能被排到 5~7 分钟后，
# 因此等待上限给足 15 分钟（300 × 3s），避免把"慢启动"误判为"启动失败"。
wait_port() {
  local p=$1 n=0
  while [ "$n" -lt 300 ]; do
    svc_up "$p" && return 0
    sleep 3
    n=$((n + 1))
  done
  return 1
}

stop_all() {
  for p in "${STARTED_PORTS[@]:-}"; do
    [ -n "$p" ] && kill_port "$p"
  done
}
if [ "$REUSE_MODE" = "1" ]; then
  # 复用模式：不要设置 EXIT trap，否则会把复用的服务一并杀掉
  info "REUSE=1：复用已运行的服务（跳过启动与关停）"
else
  trap stop_all EXIT
fi

# 并发启动：6 个 JVM 同时排队，总等待 ≈ 单个冷启动延迟（串行会叠加 6 倍）
launch_svc() {
  local mod=$1 port=$2
  if port_busy "$port"; then
    info "端口 $port 已被占用，先停掉旧进程以确保验证的是最新构建"
    kill_port "$port"
  fi
  # 不要用 `env -u`：PATH 里的 ~/.local/bin/env 是存根脚本（无 exec "$@"），
  # 会让 java 静默不启动（日志 0 字节、端口不监听）。用脚本内 unset，同样对子进程生效。
  unset SERVER__PORT SERVER__HOST
  java -Djava.io.tmpdir="$ROOT/logs/tmp" -Dfile.encoding=UTF-8 -Xmx340m \
    -jar "$ROOT/$mod/target/$mod.jar" --server.port="$port" > "$OUT_DIR/svc-$mod.log" 2>&1 &
  STARTED_PORTS+=("$port")
}

await_svc() {
  local mod=$1 port=$2
  if wait_port "$port"; then
    info "$mod 已就绪 (:$port)"
  else
    bad "$mod 启动超时 (:$port)，详见 logs/e2e-modules/svc-$mod.log"
  fi
}

# ---------------------------------------------------------------------
# HTTP / JSON 工具
# ---------------------------------------------------------------------
http() {  # http <method> <path> <token|-> <bodyfile|-> <outfile>  → 打印 HTTP 状态码
  local m=$1 path=$2 tok=$3 body=$4 out=$5
  local args=(-s -X "$m" "$GW$path" -o "$out" -w "%{http_code}" --max-time 30)
  [ "$tok" != "-" ] && args+=(-H "Authorization: Bearer $tok")
  if [ "$body" != "-" ]; then
    args+=(-H "Content-Type: application/json" --data-binary "@$body")
  fi
  MSYS_NO_PATHCONV=1 curl.exe "${args[@]}"
}

J() {  # J <jsonfile> <python-expr with `d`>  —— 读文件避免 stdin 问题
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

# =====================================================================
echo "=== 阶段 0：环境准备 ==="
LINES+=("")
LINES+=("=== 一、环境与构建产物 ===")
for m in zx-user zx-course zx-auth zx-exam zx-learning zx-gateway; do
  if [ -f "$ROOT/$m/target/$m.jar" ]; then ok "$m fat jar 存在"; else bad "$m fat jar 缺失"; fi
done
if [ -f "$ROOT/zx-web/dist/index.html" ]; then ok "前端构建产物 dist/index.html 存在"; else bad "前端构建产物缺失"; fi

# =====================================================================
SVC_ENVS="zx-user:8082 zx-course:8083 zx-auth:8081 zx-learning:8086 zx-exam:8084 zx-gateway:8080"
if [ "$REUSE_MODE" = "1" ]; then
  echo "=== 阶段 1：复用已运行的服务（REUSE=1）==="
  LINES+=("")
  LINES+=("=== 一之二、服务就绪检查（复用模式）===")
  for e in $SVC_ENVS; do
    if svc_up "${e##*:}"; then ok "${e%%:*} 已在运行 (${e##*:})"; else bad "${e%%:*} 未运行 (${e##*:})"; fi
  done
else
  echo "=== 阶段 1：启动服务（并发）==="
  launch_svc zx-user     8082
  launch_svc zx-course   8083
  launch_svc zx-auth     8081
  launch_svc zx-learning 8086
  launch_svc zx-exam     8084
  launch_svc zx-gateway  8080
  for e in $SVC_ENVS; do
    await_svc "${e%%:*}" "${e##*:}"
  done
fi

LINES+=("")
LINES+=("=== 二、登录与鉴权 ===")

# 学员登录
body login-stu.json '{"cellPhone":"13900000001","password":"123456"}' >/dev/null
http POST /accounts/login - "$OUT_DIR/login-stu.json" "$OUT_DIR/r-login-stu.json" >/dev/null
chk "学员登录 13900000001/123456" "$(J "$OUT_DIR/r-login-stu.json" "d['code']")" "200"
STU_TOKEN=$(J "$OUT_DIR/r-login-stu.json" "d['data']['accessToken']")

# 管理员登录
body login-adm.json '{"cellPhone":"13800000001","password":"123456"}' >/dev/null
http POST /accounts/login - "$OUT_DIR/login-adm.json" "$OUT_DIR/r-login-adm.json" >/dev/null
chk "管理员登录 13800000001/123456" "$(J "$OUT_DIR/r-login-adm.json" "d['code']")" "200"
ADM_TOKEN=$(J "$OUT_DIR/r-login-adm.json" "d['data']['accessToken']")

# 网关鉴权：无 token 访问受保护接口
http GET /points/summary - - "$OUT_DIR/r-noauth.json" >/dev/null
NA=$(J "$OUT_DIR/r-noauth.json" "d.get('code','-') if isinstance(d,dict) else '-'")
chk "未携带 token 访问 /points/summary 被拒" "$NA" "401"

# =====================================================================
echo "=== 阶段 2：个人中心 / 积分模块 ==="
LINES+=("")
LINES+=("=== 三、个人中心与积分中心（需求 1）===")

http GET /points/summary "$STU_TOKEN" - "$OUT_DIR/r-summary.json" >/dev/null
chk "GET /points/summary 返回 200" "$(J "$OUT_DIR/r-summary.json" "d['code']")" "200"

http GET "/points/records/page?pageNo=1&pageSize=500" "$STU_TOKEN" - "$OUT_DIR/r-records.json" >/dev/null
chk "GET /points/records/page 返回 200" "$(J "$OUT_DIR/r-records.json" "d['code']")" "200"

http GET "/points/rank?top=10" "$STU_TOKEN" - "$OUT_DIR/r-rank.json" >/dev/null
chk "GET /points/rank 返回 200" "$(J "$OUT_DIR/r-rank.json" "d['code']")" "200"

# ---- 积分聚合正确性：跨接口自洽断言，替代原先写死的绝对基数 ----
# 历史问题：原先断言 points=250 / rank=3 / totalUsers=8 / recordCount=4 / 榜首=2101/325 等，
# 依赖一份**不可重现的初始夹具**；而本脚本自身在下方"自动扣分/加分"环节就会持续累计积分，
# 于是从第二次运行起必然失败（实测 250→662、4→14、榜首 325→845），
# 既无法重复执行，也无法区分"聚合逻辑错了"与"数据只是变多了"。
# 现改为「summary ↔ records ↔ rank」三方交叉验证：不依赖绝对基数，却能真正抓住聚合错误
# （重复计数、漏计数、排名错位、榜单未排序等）。
chk "积分总额 = 本人明细求和（summary ↔ records 自洽）" \
  "$(J "$OUT_DIR/r-summary.json" "d['data']['points']")" \
  "$(J "$OUT_DIR/r-records.json" "sum(x.get('points',0) for x in d['data']['list'])")"
chk "summary.recordCount = 明细总数（summary ↔ records 自洽）" \
  "$(J "$OUT_DIR/r-summary.json" "d['data']['recordCount']")" \
  "$(J "$OUT_DIR/r-records.json" "d['data']['total']")"
chk "summary.rank = 排行榜 me.rank（summary ↔ rank 自洽）" \
  "$(J "$OUT_DIR/r-summary.json" "d['data']['rank']")" \
  "$(J "$OUT_DIR/r-rank.json" "(d['data'].get('me') or {}).get('rank')")"
chk "榜单含本人条目(me) 且 userId=2001" \
  "$(J "$OUT_DIR/r-rank.json" "'yes' if (d['data'].get('me') or {}).get('userId')==2001 else 'no'")" "yes"
chk "排行榜按积分降序排列" \
  "$(J "$OUT_DIR/r-rank.json" "'yes' if all(d['data']['top'][i]['points']>=d['data']['top'][i+1]['points'] for i in range(len(d['data']['top'])-1)) else 'no'")" "yes"
chk "排行榜条数 = min(10, 参与积分人数)" \
  "$(J "$OUT_DIR/r-rank.json" "len(d['data']['top'])")" \
  "$(J "$OUT_DIR/r-summary.json" "min(10, d['data']['totalUsers'])")"
chk "参与积分人数 ≥ 8（演示学员基数）" \
  "$(J "$OUT_DIR/r-summary.json" "'yes' if d['data']['totalUsers']>=8 else 'no'")" "yes"
chk "本人排名落在 1..参与人数 区间" \
  "$(J "$OUT_DIR/r-summary.json" "'yes' if 1<=d['data']['rank']<=d['data']['totalUsers'] else 'no'")" "yes"
chk "明细首条含来源文案" "$(J "$OUT_DIR/r-records.json" "'有' if d['data']['list'][0].get('sourceText') else '无'")" "有"

# --- 自动加分：完成测验 ---
BEFORE=$(J "$OUT_DIR/r-summary.json" "d['data']['points']")
body quiz.json '[{"questionId":1,"courseId":3001,"userAnswer":"B"}]'
http POST /question-results "$STU_TOKEN" "$OUT_DIR/quiz.json" "$OUT_DIR/r-quiz.json" >/dev/null
chk "提交测验（答对 1 题）返回 200" "$(J "$OUT_DIR/r-quiz.json" "d['code']")" "200"
chk "测验判分：答对" "$(J "$OUT_DIR/r-quiz.json" "d['data'][0]['correct']")" "True"
http GET /points/summary "$STU_TOKEN" - "$OUT_DIR/r-summary2.json" >/dev/null
AFTER=$(J "$OUT_DIR/r-summary2.json" "d['data']['points']")
DELTA=$((AFTER - BEFORE))
chk "完成测验后积分自动 +5（实际 $BEFORE → $AFTER）" "$DELTA" "5"

# --- 自动加分：发布讨论 ---
BEFORE=$AFTER
body board.json '{"courseId":3001,"title":"端到端验证·自动加分话题","content":"本话题由端到端验证脚本创建，用于校验发帖自动加分。"}'
http POST /boards "$STU_TOKEN" "$OUT_DIR/board.json" "$OUT_DIR/r-board.json" >/dev/null
chk "发布讨论话题返回 200" "$(J "$OUT_DIR/r-board.json" "d['code']")" "200"
NEW_BOARD=$(J "$OUT_DIR/r-board.json" "d['data']")
http GET /points/summary "$STU_TOKEN" - "$OUT_DIR/r-summary3.json" >/dev/null
AFTER=$(J "$OUT_DIR/r-summary3.json" "d['data']['points']")
DELTA=$((AFTER - BEFORE))
chk "发布讨论后积分自动 +5（实际 $BEFORE → $AFTER）" "$DELTA" "5"

# --- 自动加分：回复讨论 ---
BEFORE=$AFTER
body reply.json "{\"boardId\":$NEW_BOARD,\"content\":\"回复用于校验积分自动累加。\"}"
http POST /replies "$STU_TOKEN" "$OUT_DIR/reply.json" "$OUT_DIR/r-reply.json" >/dev/null
chk "回复讨论返回 200" "$(J "$OUT_DIR/r-reply.json" "d['code']")" "200"
http GET /points/summary "$STU_TOKEN" - "$OUT_DIR/r-summary4.json" >/dev/null
AFTER=$(J "$OUT_DIR/r-summary4.json" "d['data']['points']")
DELTA=$((AFTER - BEFORE))
chk "回复讨论后积分自动 +2（实际 $BEFORE → $AFTER）" "$DELTA" "2"

# --- 内部积分接口不得被外部调用 ---
body award.json '{"userId":2001,"source":"QUIZ","points":9999,"refId":"hack:1"}'
http POST /points/award "$STU_TOKEN" "$OUT_DIR/award.json" "$OUT_DIR/r-award.json" >/dev/null
chk "外部经网关调用内部加分接口被拒(403)" "$(J "$OUT_DIR/r-award.json" "d.get('code','-')")" "403"

# --- 讨论区数据 ---
http GET "/boards/page?courseId=3001&pageNo=1&pageSize=10" "$STU_TOKEN" - "$OUT_DIR/r-boards.json" >/dev/null
chk "GET /boards/page 返回 200" "$(J "$OUT_DIR/r-boards.json" "d['code']")" "200"
http GET /boards/7001 "$STU_TOKEN" - "$OUT_DIR/r-board7001.json" >/dev/null
chk "GET /boards/7001 返回 200" "$(J "$OUT_DIR/r-board7001.json" "d['code']")" "200"
chk "话题 7001 含 2 条回复" "$(J "$OUT_DIR/r-board7001.json" "len(d['data']['replies'])")" "2"

# =====================================================================
echo "=== 阶段 3：课表与课程内容 ==="
LINES+=("")
LINES+=("=== 四、我的课表与课程内容页（需求 2）===")

http GET "/lessons/page?pageNo=1&pageSize=10" "$STU_TOKEN" - "$OUT_DIR/r-lessons.json" >/dev/null
chk "GET /lessons/page 返回 200" "$(J "$OUT_DIR/r-lessons.json" "d['code']")" "200"
chk "课表条目数 > 0" "$(J "$OUT_DIR/r-lessons.json" "'有' if d['data']['total']>0 else '无'")" "有"

http GET /courses/3001 "$STU_TOKEN" - "$OUT_DIR/r-course.json" >/dev/null
chk "GET /courses/3001 返回 200" "$(J "$OUT_DIR/r-course.json" "d['code']")" "200"
chk "课程返回两级目录：根节点 2 章" "$(J "$OUT_DIR/r-course.json" "len(d['data']['catalogues'])")" "2"
chk "首章含 2 个小节(sections)" "$(J "$OUT_DIR/r-course.json" "len(d['data']['catalogues'][0]['sections'])")" "2"
chk "小节带可播放媒资标识(trailer/duration)" "$(J "$OUT_DIR/r-course.json" "'有' if 'trailer' in d['data']['catalogues'][0]['sections'][0] else '无'")" "有"

http GET /lessons/3002 "$STU_TOKEN" - "$OUT_DIR/r-lesson3002.json" >/dev/null
chk "学员已拥有的课程 3002 课表可查" "$(J "$OUT_DIR/r-lesson3002.json" "str(d['data']['courseId']) if d.get('data') else 'null'")" "3002"

# 内部目录接口不对外暴露
http GET /course/3001/catalogues "$STU_TOKEN" - "$OUT_DIR/r-inner-cat.json" >/dev/null
chk "内部接口 /course/3001/catalogues 对外被拒(403)" "$(J "$OUT_DIR/r-inner-cat.json" "d.get('code','-')")" "403"

# =====================================================================
echo "=== 阶段 4：重置密码 ==="
LINES+=("")
LINES+=("=== 五、重置密码（需求 3）===")

http PUT /users/2102/password/default "$ADM_TOKEN" - "$OUT_DIR/r-reset.json" >/dev/null
chk "管理员重置密码接口不再报环境变量缺失(200)" "$(J "$OUT_DIR/r-reset.json" "d['code']")" "200"
chk "重置响应不含 ZX_USER_DEFAULT_PASSWORD 报错" "$(J "$OUT_DIR/r-reset.json" "'含' if 'ZX_USER_DEFAULT_PASSWORD' in str(d.get('msg','')) else '不含'")" "不含"

body login-2102.json '{"cellPhone":"13900000202","password":"123456"}'
http POST /accounts/login - "$OUT_DIR/login-2102.json" "$OUT_DIR/r-login-2102.json" >/dev/null
chk "被重置账号可用统一密码 123456 登录" "$(J "$OUT_DIR/r-login-2102.json" "d['code']")" "200"

# =====================================================================
echo "=== 阶段 5：账号状态管理 ==="
LINES+=("")
LINES+=("=== 六、账号状态管理（需求 4）===")

http PUT /users/2102/status/0 "$ADM_TOKEN" - "$OUT_DIR/r-disable.json" >/dev/null
chk "管理员禁用账号 2102 返回 200" "$(J "$OUT_DIR/r-disable.json" "d['code']")" "200"

http POST /accounts/login - "$OUT_DIR/login-2102.json" "$OUT_DIR/r-login-disabled.json" >/dev/null
chk "被禁用账号登录被拦截(业务码 423)" "$(J "$OUT_DIR/r-login-disabled.json" "d.get('code','-')")" "423"
DIS_MSG=$(J "$OUT_DIR/r-login-disabled.json" "str(d.get('msg',''))")
case "$DIS_MSG" in
  *管理员*) ok "禁用登录提示含「管理员」字样: $DIS_MSG" ;;
  *) bad "禁用登录提示语异常: $DIS_MSG" ;;
esac

http PUT /users/2102/status/1 "$ADM_TOKEN" - "$OUT_DIR/r-enable.json" >/dev/null
chk "管理员启用账号 2102 返回 200" "$(J "$OUT_DIR/r-enable.json" "d['code']")" "200"
http POST /accounts/login - "$OUT_DIR/login-2102.json" "$OUT_DIR/r-login-again.json" >/dev/null
chk "启用后账号恢复可登录" "$(J "$OUT_DIR/r-login-again.json" "d['code']")" "200"

# 越权：学员不能改状态
http PUT /users/2103/status/0 "$STU_TOKEN" - "$OUT_DIR/r-forbid-status.json" >/dev/null
chk "学员调用状态修改接口被拒(403)" "$(J "$OUT_DIR/r-forbid-status.json" "d.get('code','-')")" "403"

# 信息性：管理员禁用自己（后端有"不能禁用本人/最后一名管理员"保护则会被拒绝）
ADM_ID=$(J "$OUT_DIR/r-login-adm.json" "d['data']['userId']")
http PUT "/users/$ADM_ID/status/0" "$ADM_TOKEN" - "$OUT_DIR/r-self-disable.json" >/dev/null
info "管理员禁用自己 → code=$(J "$OUT_DIR/r-self-disable.json" "d.get('code','-')") msg=$(J "$OUT_DIR/r-self-disable.json" "str(d.get('msg',''))")"
http PUT "/users/$ADM_ID/status/1" "$ADM_TOKEN" - "$OUT_DIR/r-self-restore.json" >/dev/null

# =====================================================================
echo "=== 阶段 6：前端样式修复 ==="
LINES+=("")
LINES+=("=== 七、前端样式与自适应（需求 5）===")

CSS_COUNT=$(ls "$ROOT/zx-web/dist/assets/"*.css 2>/dev/null | wc -l)
if [ "$CSS_COUNT" -gt 0 ]; then
  ok "前端样式产物存在（$CSS_COUNT 个 CSS 文件）"
  # Vite 会把 CSS 按 chunk 拆开，兜底类落在主 chunk 里，因此要全量扫描
  CSS_HIT=$(grep -l "zx-chart-box" "$ROOT/zx-web/dist/assets/"*.css 2>/dev/null | head -1)
  if [ -n "$CSS_HIT" ]; then
    ok "产物 CSS 含图表/溢出兜底类 zx-chart-box（$(basename "$CSS_HIT")）"
  else
    bad "产物 CSS 未找到溢出兜底类 zx-chart-box"
  fi
  CSS_OVER=$(grep -l "min-width:0\|overflow:hidden\|max-width:100%" "$ROOT/zx-web/dist/assets/"*.css 2>/dev/null | wc -l)
  chk "产物 CSS 含自适应/溢出约束规则（命中 $CSS_OVER 个文件）" "$([ "$CSS_OVER" -gt 0 ] && echo 有 || echo 无)" "有"
else
  bad "未找到前端 CSS 产物"
fi
if ls "$ROOT/zx-web/dist/assets/" 2>/dev/null | grep -q "CourseContentView"; then
  ok "课程内容页已打包进产物"
else
  bad "课程内容页未打包"
fi
if ls "$ROOT/zx-web/dist/assets/" 2>/dev/null | grep -q "ProfileView"; then
  ok "个人中心页已打包进产物"
else
  bad "个人中心页未打包"
fi

grep -q "zx-chart-box" "$ROOT/zx-web/src/styles/index.css" 2>/dev/null \
  && ok "全局样式表已定义图表容器兜底类 zx-chart-box" \
  || bad "全局样式表缺少 zx-chart-box 兜底类"

# =====================================================================
echo "=== 阶段 7：收尾 ==="
LINES+=("")
LINES+=("=== 八、汇总 ===")
TOTAL=$((PASS + FAIL))
LINES+=("  断言总数: $TOTAL   通过: $PASS   失败: $FAIL")
SUMMARY_LINE=$([ "$FAIL" -eq 0 ] && echo "结论: 全部通过 ✅" || echo "结论: 存在 $FAIL 项失败 ❌")
LINES+=("  $SUMMARY_LINE")

{
  echo "====================================================================="
  echo "知行智学 ZhiXing Learn · 五大模块端到端验证报告"
  echo "生成时间: $(date '+%Y-%m-%d %H:%M:%S')"
  echo "网关地址: $GW"
  echo "====================================================================="
  printf '%s\n' "${LINES[@]}"
  echo ""
  echo "---------------------------------------------------------------------"
  echo "原始响应报文目录: logs/e2e-modules/"
  echo "服务启动日志目录: logs/e2e-modules/svc-*.log"
  echo "---------------------------------------------------------------------"
} > "$REPORT"

printf '%s\n' "${LINES[@]}"
echo ""
echo "报告已写入: $REPORT"
echo "EXIT_FAIL_COUNT=$FAIL"
