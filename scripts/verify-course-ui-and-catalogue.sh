#!/usr/bin/env bash
# =====================================================================
# 知行智学 · 课程界面 / 我的课表 / 章节内容 三项优化 端到端验证
# ---------------------------------------------------------------------
# 覆盖需求：
#   1. 课程界面的「已拥有」必须以「我的课表」为唯一口径，且与我的课表实时联动：
#      - 课表里有 → 已拥有；课表里没有 → 可购买（即"支付了但课表还没开"不算已拥有）；
#      - 支付成功 / 免费开课后**立即**（同一请求内）写入课表 → 两端一致，无需等 MQ。
#   2. 「章节内容」模块补全：每门课都有章节目录，每个小节都有要点 + 讲义正文。
#   3. 「我的课表」界面：统计/检索/筛选与兜底封面（前端产物静态校验）。
#
# 用法：
#   /usr/bin/bash scripts/verify-course-ui-and-catalogue.sh
#   REUSE=1 /usr/bin/bash scripts/verify-course-ui-and-catalogue.sh   # 复用已运行服务只跑断言
#
# 沙箱/环境注意（踩坑记录）：
#   * 服务不跨工具调用存活 → 「启动 + 断言」必须在同一条命令内完成：
#       cd "D:/1/zx-learn" && /usr/bin/bash scripts/dev-up-core.sh >/dev/null 2>&1; \
#         REUSE=1 /usr/bin/bash scripts/verify-course-ui-and-catalogue.sh 2>&1 | tail -60
#   * 脚本内禁用 sort/find（被 /tmp/system32 的 Windows 版遮蔽）。
#   * SQL 的 IN (...) 必须逗号分隔；consume_record.id 非自增且 ≤18 位。
#   * 清理 synthetic 数据必须在删订单**之前**先删 order_msg，否则本地消息表补偿会把
#     orderPaid 重投给 MQ（RocketMQ 在跑），课表被"复活"，清理断言失败。
# =====================================================================
set -u

ROOT="D:/1/zx-learn"
PY="C:/Users/20670/.workbuddy/binaries/python/versions/3.13.12/python.exe"
MYSQL="/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"
GW="http://localhost:8080"
LEARNING_DIRECT="http://localhost:8086"
OUT_DIR="$ROOT/logs/verify-course-ui"
REPORT="$ROOT/docs/verify-course-ui-report.txt"
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
# 注意：Windows 版 mysql.exe 多行输出为 CRLF，命令替换只会去掉 \n，
# 残留的 \r 会让 `for x in $(sql ...)` 的每个元素尾带 \r（最后一行除外，
# 这正是"只有最后一门课能拉到详情"的根因）→ 统一 tr -d '\r' 清掉。
sql() { "$MYSQL" -uroot -p"$MYSQL_PWD_VAL" -N --default-character-set=utf8mb4 -e "$1" 2>/dev/null | tr -d '\r'; }

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

SVC_ENVS="zx-course:8083 zx-learning:8086 zx-trade:8087 zx-gateway:8080"

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
# 合成夹具（与真实数据和雪花 id 段隔离）
# ---------------------------------------------------------------------
# 合成课程：用于真实走「下单 → 支付 → 同步开课」链路，不污染真实课程数据
CID_PAID=9900000000000001
CID_FREE=9900000000000002
# 合成小节笔记的归属课程/小节
NOTE_CID=9900000000000003
NOTE_SID=9900000000000011
# 「已支付但课表未开通」场景：直接用真实历史数据 —— 学员 2001 对课程 3006 有已支付订单，
# 但 3006 不在其课表中（旧口径 bought-course-ids 会显示"已拥有"，新口径以课表为准 → 可购买）。
# 用真实数据而不是合成订单，更能说明"口径不一致"是真实存在的问题。
CID_PAID_NOLESSON=3006

cleanup_fixtures() {
  # 顺序关键：先删订单消息（切断本地消息表重投 → MQ 复活课表），再删订单与课表
  sql "USE zx_trade; DELETE FROM order_msg WHERE order_id IN (SELECT id FROM trade_order WHERE user_id=2001 AND course_id IN ($CID_PAID,$CID_FREE));" >/dev/null
  sql "USE zx_trade; DELETE FROM trade_order_detail WHERE order_id IN (SELECT id FROM trade_order WHERE user_id=2001 AND course_id IN ($CID_PAID,$CID_FREE));" >/dev/null
  sql "USE zx_trade; DELETE FROM trade_order WHERE user_id=2001 AND course_id IN ($CID_PAID,$CID_FREE);" >/dev/null
  sql "USE zx_trade; DELETE FROM consume_record WHERE consume_key LIKE 'order:close:%' OR consume_key LIKE 'lesson:paid:%';" >/dev/null
  sql "USE zx_learning; DELETE FROM consume_record WHERE consume_key LIKE 'lesson:paid:%';" >/dev/null
  # 3006 仅恢复"课表未开通"的原始状态（其历史订单属于基线数据，不能删）
  # 注意：**不要**删 $CID_PAID_NOLESSON(3006)：3006 的历史已支付订单是真实基线数据，
  # 其课表项按「已支付 → 课表必有」的不变量应当存在，不属于合成夹具。
  # 本场景需要的"脏状态"由阶段 3 显式构造，并在该阶段末尾补开还原。
  sql "USE zx_learning; DELETE FROM lesson WHERE user_id=2001 AND course_id IN ($CID_PAID,$CID_FREE);" >/dev/null
  sql "USE zx_learning; DELETE FROM note WHERE course_id IN ($NOTE_CID,$CID_PAID,$CID_FREE) OR lesson_id=$NOTE_SID;" >/dev/null
  sql "USE zx_course; DELETE FROM course_quota WHERE course_id IN ($CID_PAID,$CID_FREE);" >/dev/null
  sql "USE zx_course; DELETE FROM course_quota_record WHERE course_id IN ($CID_PAID,$CID_FREE);" >/dev/null
  sql "USE zx_course; DELETE FROM course WHERE id IN ($CID_PAID,$CID_FREE);" >/dev/null
}

if [ "$REUSE_MODE" != "1" ]; then
  trap 'for p in 8080 8083 8086 8087; do kill_port "$p"; done' EXIT
fi

# =====================================================================
echo "=== 阶段 0：环境与构建产物 ==="
LINES+=("")
LINES+=("=== 一、环境与构建产物 ===")
for m in zx-course zx-learning zx-trade zx-gateway; do
  if [ -f "$ROOT/$m/target/$m.jar" ]; then ok "$m fat jar 存在"; else bad "$m fat jar 缺失"; fi
done
if [ -f "$ROOT/zx-web/dist/index.html" ]; then ok "前端构建产物存在"; else bad "前端构建产物缺失"; fi

# 迁移列就位
CI=$(sql "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='zx_course' AND TABLE_NAME='course_catalogue' AND COLUMN_NAME IN ('content','key_points','attachment_url','attachment_name');")
chk "course_catalogue 四个内容列已就位" "$CI" "4"

# =====================================================================
echo "=== 阶段 1：启动 / 复用服务 ==="
if [ "$REUSE_MODE" = "1" ]; then
  LINES+=("")
  LINES+=("=== 二、服务就绪检查（复用模式）===")
  for e in $SVC_ENVS; do
    if svc_up "${e##*:}"; then ok "${e%%:*} 已在运行 (${e##*:})"; else bad "${e%%:*} 未运行 (${e##*:})"; fi
  done
else
  echo "  启动 Redis 与服务（最长 8 分钟）..."
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
      > "$OUT_DIR/svc-$m.log" 2>&1 &
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
echo "=== 阶段 2：章节内容补全（需求 2）==="
LINES+=("")
LINES+=("=== 三、章节内容模块：目录与讲义补全（需求 2）===")

# 先清掉可能残留的合成数据，避免它们混进"逐门课程"校验
cleanup_fixtures

# 校验口径：仅**已上架**课程（course.status=1，见 Constant.COURSE_STATUS_ON_SHELF）。
# 为什么必须限定 status=1：学员端课程详情接口 GET /courses/{id} 对草稿/下架课程
# 返回 400「课程不存在」（实测），草稿课程天然允许"暂无章节目录"（先建课、后编目录）。
# 若把 status=0 也算进来，后台每建一门草稿课都会让本套件误报——曾因此产生 2 项假失败。
COURSE_IDS=$(sql "USE zx_course; SELECT id FROM course WHERE deleted=0 AND status=1 ORDER BY id;")
COURSE_COUNT=$(sql "USE zx_course; SELECT COUNT(*) FROM course WHERE deleted=0 AND status=1;")
info "在架课程数：$COURSE_COUNT（口径：deleted=0 且 status=1）"

# 逐门课拉详情并校验目录完整性
: > "$OUT_DIR/catalogue-summary.txt"
for cid in $COURSE_IDS; do
  # 用脚本内已验证可靠的 http() 辅助（自带 $GW 前缀与 --max-time），
  # 不再用裸 curl -o（历史 bug：cid 尾带 \r 时静默不落盘）
  http GET "/courses/$cid" - - "$OUT_DIR/course-$cid.json" >/dev/null 2>&1
  [ -s "$OUT_DIR/course-$cid.json" ] || info "课程 $cid 详情响应为空（文件未落盘）"
  "$PY" -c "
import json, sys
# 强制 LF 输出：Windows 下 Python 文本模式会把换行翻译成 CRLF，而下游断言里存在
# 形如 EMPTY_POINTS 累加 empty_p 的算术，尾随回车会让该算术报错并中断整个 while
# 循环，导致下面 5 条断言全部以 0 通过（假阳性，实测处理行数只有 1）。此处从源头断掉最可靠。
# 注意：不要改用外部过滤器配合追加重定向 —— 本机 tr 是另一个 Git 安装的 Windows 原生
# 程序，不遵守追加语义，会把循环里的追加静默变成覆盖（实测 3 次追加只剩 1 行）。
# 另注：本块位于 shell 的双引号字符串内，注释里不得出现反引号或美元加括号的算术写法。
sys.stdout.reconfigure(newline='\n')
cid = sys.argv[1]
try:
    d = json.load(open(sys.argv[2], encoding='utf-8'))
except Exception:
    print(f'{cid}\tPARSE_ERR\t0\t0\t0\t0'); sys.exit(0)
data = d.get('data') or {}
cats = data.get('catalogues') or []
sections = [s for c in cats for s in (c.get('sections') or [])]
empty_content = sum(1 for s in sections if not (s.get('content') or '').strip())
empty_points = sum(1 for s in sections if not (s.get('keyPoints') or '').strip())
print(f\"{cid}\t{len(cats)}\t{len(sections)}\t{empty_content}\t{empty_points}\")
" "$cid" "$OUT_DIR/course-$cid.json" >> "$OUT_DIR/catalogue-summary.txt"
done

NO_CATALOGUE=0
NO_SECTION=0
EMPTY_CONTENT=0
EMPTY_POINTS=0
PARSE_BAD=0
SUMMARY_ROWS=0
# ⚠ 必须剔除行尾 \r：python 在 Windows 上 print 出的是 CRLF，若让 \r 留在最后一个字段
# （empty_p="0\r"），`$((EMPTY_POINTS + empty_p))` 会抛 arithmetic syntax error，
# **while 循环在第一行就中断**，于是下面 5 条断言全部以 0 "通过"——即**假阳性**：
# 实测"无空讲义 / 要点非空"这两条从来没有真正执行过（处理行数=1）。
# 此处显式剥离 \r，并用 SUMMARY_ROWS 与 COURSE_COUNT 对账：任何静默跳过都会被下面的覆盖断言抓出来。
while IFS="$(printf '\t')" read -r cid chapters sections empty_c empty_p; do
  cid=${cid%$'\r'}; chapters=${chapters%$'\r'}; sections=${sections%$'\r'}
  empty_c=${empty_c%$'\r'}; empty_p=${empty_p%$'\r'}
  [ -z "$cid" ] && continue
  SUMMARY_ROWS=$((SUMMARY_ROWS + 1))
  [ "$chapters" = "PARSE_ERR" ] && { PARSE_BAD=$((PARSE_BAD + 1)); continue; }
  [ "$chapters" -lt 1 ] && NO_CATALOGUE=$((NO_CATALOGUE + 1))
  [ "$sections" -lt 1 ] && NO_SECTION=$((NO_SECTION + 1))
  EMPTY_CONTENT=$((EMPTY_CONTENT + empty_c))
  EMPTY_POINTS=$((EMPTY_POINTS + empty_p))
done < "$OUT_DIR/catalogue-summary.txt"

# 覆盖自检（防假阳性）：逐门自检必须真正跑满全部在架课程，一条都不能少。
chk "逐门课程自检覆盖全部在架课程（$COURSE_COUNT 门，防静默跳过）" "$SUMMARY_ROWS" "$COURSE_COUNT"
chk "全部课程详情接口可解析" "$PARSE_BAD" "0"
chk "每门课程都至少有一个章节目录节点" "$NO_CATALOGUE" "0"
chk "每门课程都至少有一个小节" "$NO_SECTION" "0"
chk "所有小节都有讲义正文（无空讲义）" "$EMPTY_CONTENT" "0"
chk "所有小节都有本节要点" "$EMPTY_POINTS" "0"

# 抽检一门课的讲义是真实内容而非占位文案
STU_TOKEN_CHECK=""
body login-stu.json '{"cellPhone":"13900000001","password":"123456"}' >/dev/null
http POST /accounts/login - "$OUT_DIR/login-stu.json" "$OUT_DIR/r-login-stu.json" >/dev/null
STU_TOKEN=$(J "$OUT_DIR/r-login-stu.json" "d['data']['accessToken']")
chk "学员登录 13900000001/123456" "$(J "$OUT_DIR/r-login-stu.json" "d['code']")" "200"

http GET /courses/3001 "$STU_TOKEN" - "$OUT_DIR/r-course-3001.json" >/dev/null
chk "课程详情 3001 返回 200" "$(J "$OUT_DIR/r-course-3001.json" "d['code']")" "200"
chk "3001 章节数 ≥ 2" "$(J "$OUT_DIR/r-course-3001.json" "1 if len(d['data']['catalogues'])>=2 else 0")" "1"
chk "3001 小节数 ≥ 4" "$(J "$OUT_DIR/r-course-3001.json" "1 if sum(len(c.get('sections') or []) for c in d['data']['catalogues'])>=4 else 0")" "1"
chk "讲义为真实内容（含“本节目标”而非占位文案）" \
  "$(J "$OUT_DIR/r-course-3001.json" "'本节目标' in ''.join((s.get('content') or '') for c in d['data']['catalogues'] for s in (c.get('sections') or []))")" "True"
chk "要点已按 “|” 下发（可切分）" \
  "$(J "$OUT_DIR/r-course-3001.json" "'|' in [s.get('keyPoints') or '' for c in d['data']['catalogues'] for s in (c.get('sections') or [])][0]")" "True"

# 库内自检：不存在"有目录但讲义为空"的小节
chk "库内无空讲义小节（SQL 交叉校验）" \
  "$(sql "USE zx_course; SELECT COUNT(*) FROM course_catalogue WHERE deleted=0 AND chapter_type=2 AND (content IS NULL OR content='');")" "0"

# --- 目录结构统一性 / 禁止占位（需求 2 的硬指标）---
# 1) 每门课都达到「2 章 × 2 小节」的统一下限（早期种子课曾只有 1 章 1 节）
STRUCT_BAD=0
while IFS="$(printf '\t')" read -r cid chapters sections _empty_c _empty_p; do
  cid=${cid%$'\r'}; chapters=${chapters%$'\r'}; sections=${sections%$'\r'}
  [ -z "$cid" ] && continue
  [ "$chapters" = "PARSE_ERR" ] && continue
  if [ "$chapters" -lt 2 ] || [ "$sections" -lt 4 ]; then STRUCT_BAD=$((STRUCT_BAD + 1)); fi
done < "$OUT_DIR/catalogue-summary.txt"
chk "每门在架课程目录结构 ≥ 2 章 × 4 小节（共 $COURSE_COUNT 门）" "$STRUCT_BAD" "0"

# 2) 目录节点名不得是英文占位（历史遗留 Chapter-1 Intro / 1.1 Setup）
chk "目录节点名无英文占位（均为中文教学内容）" \
  "$(sql "USE zx_course; SELECT COUNT(*) FROM course_catalogue WHERE deleted=0 AND name REGEXP '^[A-Za-z0-9 ._-]+\$';")" "0"

# 3) 不允许存在"目录全空"的课程（界面打开即空白）
# 3) 不允许存在"目录全空"的**在架**课程（界面打开即空白）
#    口径限定 status=1：下架/草稿课程（status=0）本就允许暂无目录，且详请接口对其返回 400，
#    不属于"用户打开即空白"的可见风险。
chk "无在架课程出现空目录（$COURSE_COUNT 门均已配置章节）" \
  "$(sql "USE zx_course; SELECT COUNT(*) FROM course c WHERE c.deleted=0 AND c.status=1 AND NOT EXISTS (SELECT 1 FROM course_catalogue cc WHERE cc.course_id=c.id AND cc.deleted=0);")" "0"

# =====================================================================
echo "=== 阶段 3：「已拥有」口径与两端联动（需求 1）==="
LINES+=("")
LINES+=("=== 四、课程界面 ↔ 我的课表：「已拥有」口径与实时联动（需求 1）===")

cleanup_fixtures

http GET /lessons/mine/course-ids "$STU_TOKEN" - "$OUT_DIR/r-mine-ids.json" >/dev/null
chk "GET /lessons/mine/course-ids 返回 200" "$(J "$OUT_DIR/r-mine-ids.json" "d['code']")" "200"
chk "课表课程 id 为数组" "$(J "$OUT_DIR/r-mine-ids.json" "isinstance(d['data'], list)")" "True"

# 权威口径校验：接口返回集合必须与 lesson 表完全一致（差值集为空）
DB_LESSON_IDS=$(sql "USE zx_learning; SELECT GROUP_CONCAT(course_id ORDER BY course_id) FROM lesson WHERE user_id=2001 AND deleted=0;")
API_LESSON_IDS=$("$PY" -c "
import json,sys
d=json.load(open(sys.argv[1],encoding='utf-8'))
print(','.join(str(int(x)) for x in sorted((d.get('data') or []), key=lambda v: int(v))))
" "$OUT_DIR/r-mine-ids.json")
chk "接口返回的已拥有集合与 lesson 表完全一致" "$API_LESSON_IDS" "$DB_LESSON_IDS"

# 核心场景：存在「已支付订单」但课表未开通时，不应显示为已拥有
# 显式构造脏状态（删掉 3006 的课表项；其历史已支付订单必须保留），阶段末尾会补开还原
sql "USE zx_learning; DELETE FROM lesson WHERE user_id=2001 AND course_id=$CID_PAID_NOLESSON;" >/dev/null
chk "前置：课程 $CID_PAID_NOLESSON 不在学员课表中但已有已支付订单" \
  "$(sql "USE zx_learning; SELECT COUNT(*) FROM lesson WHERE user_id=2001 AND course_id=$CID_PAID_NOLESSON AND deleted=0;")|$(sql "USE zx_trade; SELECT COUNT(*) FROM trade_order WHERE user_id=2001 AND course_id=$CID_PAID_NOLESSON AND status=1 AND deleted=0;")" "0|1"

http GET /orders/bought-course-ids "$STU_TOKEN" - "$OUT_DIR/r-bought.json" >/dev/null
chk "旧口径 /orders/bought-course-ids 含该课程（课表∪已支付，会误显示已拥有）" \
  "$(J "$OUT_DIR/r-bought.json" "str($CID_PAID_NOLESSON) in [str(x) for x in (d['data'] or [])]")" "True"

http GET /lessons/mine/course-ids "$STU_TOKEN" - "$OUT_DIR/r-mine-ids2.json" >/dev/null
chk "新口径 /lessons/mine/course-ids 不含该课程（以课表为准 → 显示可购买）" \
  "$(J "$OUT_DIR/r-mine-ids2.json" "str($CID_PAID_NOLESSON) in [str(x) for x in (d['data'] or [])]")" "False"

# 内部接口同步开课：写入后立即生效（无需等 MQ）
body enroll-3006.json "{\"userId\":2001,\"courseId\":$CID_PAID_NOLESSON,\"courseName\":\"验证用课程-同步开课\"}"
MSYS_NO_PATHCONV=1 curl.exe -s -o "$OUT_DIR/r-enroll.json" -w "%{http_code}" -X POST \
  -H "Content-Type: application/json" --data-binary "@$OUT_DIR/enroll-3006.json" \
  "$LEARNING_DIRECT/lessons/internal/enroll" > "$OUT_DIR/r-enroll-code.txt" 2>/dev/null
chk "内部开课接口返回 HTTP 200" "$(cat "$OUT_DIR/r-enroll-code.txt")" "200"
http GET /lessons/mine/course-ids "$STU_TOKEN" - "$OUT_DIR/r-mine-ids3.json" >/dev/null
chk "同步开课后立即出现在课表（已拥有）" \
  "$(J "$OUT_DIR/r-mine-ids3.json" "str($CID_PAID_NOLESSON) in [str(x) for x in (d['data'] or [])]")" "True"
http GET "/lessons/page?pageNo=1&pageSize=100" "$STU_TOKEN" - "$OUT_DIR/r-lessons-sync.json" >/dev/null
chk "两端一致：该课程同时出现在「我的课表」列表中" \
  "$(J "$OUT_DIR/r-lessons-sync.json" "str($CID_PAID_NOLESSON) in [str(o.get('courseId')) for o in d['data']['list']]")" "True"

# 越权保护：经网关带登录态调用内部接口必须被拒（403）
http POST /lessons/internal/enroll "$STU_TOKEN" "$OUT_DIR/enroll-3006.json" "$OUT_DIR/r-enroll-ext.json" >/dev/null
chk_ne "外部用户经网关调用内部开课接口被拒" "$(J "$OUT_DIR/r-enroll-ext.json" "d['code']")" "200"

# 场景收尾：调用 cleanup_fixtures 清掉本轮合成课程/订单；
# 注意不要留下「已支付但课表缺失」的脏状态 —— 那正是本轮修复目标，不能当基线保留。
# 因此再用内部开课接口把 3006 补开，恢复「已支付 → 课表必有该课」的不变量
# （3006 的历史已支付订单属于真实基线数据，必须保留）。
cleanup_fixtures
MSYS_NO_PATHCONV=1 curl.exe -s -o /dev/null -w "%{http_code}" -X POST \
  -H "Content-Type: application/json" --data-binary "@$OUT_DIR/enroll-3006.json" \
  "$LEARNING_DIRECT/lessons/internal/enroll" > /dev/null 2>&1
chk "场景收尾：已支付课程已补开课（不留「已支付但课表缺失」脏状态）" \
  "$(sql "USE zx_learning; SELECT COUNT(*) FROM lesson WHERE user_id=2001 AND course_id=$CID_PAID_NOLESSON AND deleted=0;")" "1"
chk "历史已支付订单未被破坏（保留基线数据）" \
  "$(sql "USE zx_trade; SELECT COUNT(*) FROM trade_order WHERE user_id=2001 AND course_id=$CID_PAID_NOLESSON AND status=1 AND deleted=0;")" "1"

# =====================================================================
echo "=== 阶段 4：支付/开课后两端实时联动（真实链路）==="
LINES+=("")
LINES+=("=== 五、支付成功 → 课表立即开通 → 两端一致（需求 1 的实时性）===")

# 合成课程（不污染真实课程数据；价格 1 元 / 免费课）
sql "USE zx_course; INSERT INTO course (id, name, cover_url, price, category_id_lv1, status, free, publish_times, description, create_time, update_time, deleted)
     VALUES ($CID_PAID, '验证用课程-付费同步开课', '', 100, 1, 1, 0, 0, '自动化验证用合成课程', NOW(), NOW(), 0);" >/dev/null
sql "USE zx_course; INSERT INTO course (id, name, cover_url, price, category_id_lv1, status, free, publish_times, description, create_time, update_time, deleted)
     VALUES ($CID_FREE, '验证用课程-免费同步开课', '', 0, 1, 1, 1, 0, '自动化验证用合成课程', NOW(), NOW(), 0);" >/dev/null
chk "合成课程已入库（2 门）" \
  "$(sql "USE zx_course; SELECT COUNT(*) FROM course WHERE id IN ($CID_PAID,$CID_FREE);")" "2"

chk "开课前：付费课程不在课表" \
  "$(J "$OUT_DIR/r-mine-ids.json" "str($CID_PAID) in [str(x) for x in (d['data'] or [])]")" "False"

# --- 付费链路：下单 → Mock 支付 → 立刻查课表 ---
body place-order.json "{\"courseId\":$CID_PAID,\"totalFee\":100}"
http POST /orders/placeOrder "$STU_TOKEN" "$OUT_DIR/place-order.json" "$OUT_DIR/r-place.json" >/dev/null
chk "下单成功" "$(J "$OUT_DIR/r-place.json" "d['code']")" "200"
ORDER_ID=$(J "$OUT_DIR/r-place.json" "str(d['data'])")
if [ -n "$ORDER_ID" ] && [ "$ORDER_ID" != "PARSE_ERR" ]; then
  ok "取到订单 id=$ORDER_ID"
else
  bad "未取到订单 id（后续支付链路断言无意义）"
  ORDER_ID=""
fi

if [ -n "$ORDER_ID" ]; then
  http POST "/orders/pay/mock/$ORDER_ID" "$STU_TOKEN" - "$OUT_DIR/r-pay.json" >/dev/null
  chk "Mock 支付成功" "$(J "$OUT_DIR/r-pay.json" "d['code']")" "200"
  chk "订单状态已支付（库）" \
    "$(sql "USE zx_trade; SELECT status FROM trade_order WHERE id=$ORDER_ID;")" "1"

  # 关键：支付返回后立刻查课表，不做任何轮询/等待
  http GET /lessons/mine/course-ids "$STU_TOKEN" - "$OUT_DIR/r-after-pay.json" >/dev/null
  chk "支付返回后【立即】出现在课表（同步开课，不依赖 MQ）" \
    "$(J "$OUT_DIR/r-after-pay.json" "str($CID_PAID) in [str(x) for x in (d['data'] or [])]")" "True"
  http GET "/lessons/page?pageNo=1&pageSize=100" "$STU_TOKEN" - "$OUT_DIR/r-my-lessons.json" >/dev/null
  chk "「我的课表」列表【立即】出现该课程（两端一致）" \
    "$(J "$OUT_DIR/r-my-lessons.json" "str($CID_PAID) in [str(o.get('courseId')) for o in d['data']['list']]")" "True"
fi

# --- 免费课链路：0 元开课 → 立刻查课表 ---
http POST "/orders/freeCourse/$CID_FREE" "$STU_TOKEN" - "$OUT_DIR/r-free.json" >/dev/null
chk "免费课 0 元开课成功" "$(J "$OUT_DIR/r-free.json" "d['code']")" "200"
http GET /lessons/mine/course-ids "$STU_TOKEN" - "$OUT_DIR/r-after-free.json" >/dev/null
chk "免费开课后【立即】出现在课表" \
  "$(J "$OUT_DIR/r-after-free.json" "str($CID_FREE) in [str(x) for x in (d['data'] or [])]")" "True"
http GET "/lessons/page?pageNo=1&pageSize=100" "$STU_TOKEN" - "$OUT_DIR/r-my-lessons2.json" >/dev/null
chk "「我的课表」列表【立即】出现免费课（两端一致）" \
  "$(J "$OUT_DIR/r-my-lessons2.json" "str($CID_FREE) in [str(o.get('courseId')) for o in d['data']['list']]")" "True"

# 已拥有后重复点「加入学习」：必须**幂等成功**，不能报"课程已拥有"把用户挡在门外
# （报名入口本就期望"进入学习"；旧版抛错正是用户反馈的「提示已拥有、课表却打不开」）
body free-again.json "{}"
http POST "/orders/freeCourse/$CID_FREE" "$STU_TOKEN" "$OUT_DIR/free-again.json" "$OUT_DIR/r-free-again.json" >/dev/null
chk "已拥有后再次加入学习：幂等成功（不再报「课程已拥有」）" "$(J "$OUT_DIR/r-free-again.json" "d['code']")" "200"
chk "幂等：重复加入未产生重复课表项" \
  "$(sql "USE zx_learning; SELECT COUNT(*) FROM lesson WHERE user_id=2001 AND course_id=$CID_FREE;")" "1"

# =====================================================================
echo "=== 阶段 5：章节内容 · 本节笔记（需求 2 子模块）==="
LINES+=("")
LINES+=("=== 六、章节内容子模块：按小节归档的笔记 ===")

body note-add.json "{\"courseId\":$NOTE_CID,\"lessonId\":$NOTE_SID,\"courseName\":\"验证用课程-笔记\",\"content\":\"<b>VTESTNOTE</b> 本节要点记录\"}"
http POST /notes "$STU_TOKEN" "$OUT_DIR/note-add.json" "$OUT_DIR/r-note-add.json" >/dev/null
chk "新增「本节笔记」成功" "$(J "$OUT_DIR/r-note-add.json" "d['code']")" "200"
NOTE_ID=$(J "$OUT_DIR/r-note-add.json" "str(d['data'])")

http GET "/notes/page?pageNo=1&pageSize=50&courseId=$NOTE_CID&lessonId=$NOTE_SID" "$STU_TOKEN" - "$OUT_DIR/r-note-1.json" >/dev/null
chk "按 课程+小节 精确过滤命中 1 条" "$(J "$OUT_DIR/r-note-1.json" "d['data']['total']")" "1"
chk "笔记返回体含 lessonId" "$(J "$OUT_DIR/r-note-1.json" "int(d['data']['list'][0]['lessonId'])")" "$NOTE_SID"

http GET "/notes/page?pageNo=1&pageSize=50&courseId=$NOTE_CID&lessonId=9900000000000099" "$STU_TOKEN" - "$OUT_DIR/r-note-2.json" >/dev/null
chk "不同小节互不串数据（过滤生效）" "$(J "$OUT_DIR/r-note-2.json" "d['data']['total']")" "0"

http GET "/notes/page?pageNo=1&pageSize=50" "$STU_TOKEN" - "$OUT_DIR/r-note-3.json" >/dev/null
chk "「我的笔记」总列表仍能看到该笔记" \
  "$(J "$OUT_DIR/r-note-3.json" "any(str(n['id'])=='$NOTE_ID' for n in d['data']['list'])")" "True"

if [ -n "$NOTE_ID" ] && [ "$NOTE_ID" != "PARSE_ERR" ]; then
  http DELETE "/notes/$NOTE_ID" "$STU_TOKEN" - "$OUT_DIR/r-note-del.json" >/dev/null
  chk "删除本节笔记成功" "$(J "$OUT_DIR/r-note-del.json" "d['code']")" "200"
fi
chk "删除后本节笔记为空" \
  "$(sql "USE zx_learning; SELECT COUNT(*) FROM note WHERE lesson_id=$NOTE_SID AND deleted=0;")" "0"

# =====================================================================
echo "=== 阶段 6：前端产物静态校验（需求 1/3 的 UI 落地）==="
LINES+=("")
LINES+=("=== 七、前端产物校验 ===")

dist_has() {  # dist_has <关键字> <说明>
  if grep -rq "$1" "$ROOT/zx-web/dist/assets/" 2>/dev/null; then ok "$2"; else bad "$2"; fi
}
dist_has "课程检索" "我的课表：课程检索模块已构建"
dist_has "已在课表中" "检索结果按课表判定持有状态（已拥有/可购买）"
dist_has "本节笔记" "章节内容：本节笔记模块已构建"
dist_has "本节要点" "章节内容：本节要点模块已构建"
dist_has "zx-cover__fallback" "封面兜底组件已构建（消除裂图空白占位）"
dist_has "我的学习记录" "章节内容：我的学习记录模块已构建"
dist_has "立即购买" "未拥有课程展示可购买状态"
if [ -f "$ROOT/zx-web/src/composables/useOwnedCourses.ts" ]; then
  ok "已拥有状态由 useOwnedCourses 统一提供（三端共用）"
else
  bad "缺少 useOwnedCourses 组合式函数"
fi
chk "课程列表不再直接依赖订单口径 boughtCourseIds" \
  "$(grep -c "boughtCourseIds" "$ROOT/zx-web/src/views/course/CourseListView.vue" 2>/dev/null)" "0"
chk "课程详情不再直接依赖订单口径 boughtCourseIds" \
  "$(grep -c "boughtCourseIds" "$ROOT/zx-web/src/views/course/CourseDetailView.vue" 2>/dev/null)" "0"

# =====================================================================
echo "=== 阶段 7：清理夹具 ==="
cleanup_fixtures
chk "合成课程已清理" \
  "$(sql "USE zx_course; SELECT COUNT(*) FROM course WHERE id IN ($CID_PAID,$CID_FREE);")" "0"
chk "合成课表项已清理" \
  "$(sql "USE zx_learning; SELECT COUNT(*) FROM lesson WHERE user_id=2001 AND course_id IN ($CID_PAID,$CID_FREE);")" "0"
chk "合成订单已清理" \
  "$(sql "USE zx_trade; SELECT COUNT(*) FROM trade_order WHERE user_id=2001 AND course_id IN ($CID_PAID,$CID_FREE);")" "0"
chk "合成笔记已清理" \
  "$(sql "USE zx_learning; SELECT COUNT(*) FROM note WHERE course_id=$NOTE_CID OR lesson_id=$NOTE_SID;")" "0"

# =====================================================================
TOTAL=$((PASS + FAIL))
LINES+=("")
LINES+=("=== 八、汇总 ===")
LINES+=("  断言总数: $TOTAL   通过: $PASS   失败: $FAIL")
LINES+=("  $([ "$FAIL" -eq 0 ] && echo '结论: 全部通过 ✅' || echo "结论: 存在 $FAIL 项失败 ❌")")

{
  echo "====================================================================="
  echo "知行智学 ZhiXing Learn · 课程界面 / 我的课表 / 章节内容 优化 验证报告"
  echo "生成时间: $(date '+%Y-%m-%d %H:%M:%S')"
  echo "网关地址: $GW"
  echo "====================================================================="
  printf '%s\n' "${LINES[@]}"
  echo ""
  echo "---------------------------------------------------------------------"
  echo "逐门课程目录自检: logs/verify-course-ui/catalogue-summary.txt"
  echo "原始响应报文目录: logs/verify-course-ui/"
  echo "服务启动日志目录: logs/verify-course-ui/svc-*.log"
  echo "---------------------------------------------------------------------"
} > "$REPORT"

printf '%s\n' "${LINES[@]}"
echo ""
echo "报告已写入: $REPORT"
echo "EXIT_FAIL_COUNT=$FAIL"
