#!/usr/bin/env bash
# =====================================================================
# 知行智学 · 「课程封面无法显示」+「教师端草稿箱存不进去」两项缺陷 端到端验证
# ---------------------------------------------------------------------
# 覆盖需求：
#   A. 课程封面正常显示
#      A1. 库里所有封面地址都能公开访问（HTTP 200 + image/*，且响应体非空）；
#      A2. 不存在指向已下线文生图接口（trae-api-cn.mchost.guru）的遗留封面；
#      A3. 前端本地兜底封面资源 course-01~13.svg 齐全。
#   B. 教师端能把新建课程存进草稿箱
#      B1. 草稿箱接口查的是 course_draft，不是 course（正式课程不会混进草稿箱）；
#      B2. 保存草稿 → 立刻能在草稿箱列表里看到（曾经"保存成功但列表恒空"）；
#      B3. 第 2 步填的章节目录真的落库，重新读取能原样回填（曾经被静默丢弃）；
#      B4. 重复保存是「更新」而不是「新增一条」；
#      B5. 删除草稿后从草稿箱消失，且不影响正式课程；
#      B6. 发布后草稿离开草稿箱、正式课程出现，目录同步进 course_catalogue；
#      B7. 越权/未登录不得读写草稿箱（学员、匿名）。
#
# 用法：
#   /usr/bin/bash scripts/verify-draft-and-covers.sh
#   REUSE=1 /usr/bin/bash scripts/verify-draft-and-covers.sh   # 复用已运行服务只跑断言
#
# 沙箱注意（踩坑记录）：
#   * 后台服务不跨工具调用存活 → 「启动 + 断言」必须在同一条命令内完成：
#       cd "D:/1/zx-learn" && /usr/bin/bash scripts/dev-up-core.sh >/dev/null 2>&1; \
#         REUSE=1 /usr/bin/bash scripts/verify-draft-and-covers.sh 2>&1 | tail -70
#   * curl 的 -o /dev/null 在 Git Bash 下会返回 exit 23（写 /dev/null 失败）且
#     size_download 恒为 0 → 校验响应体必须下载到真实文件再 stat。
#   * 断言禁用"假阳性"写法：接口返回 500 时断言必须失败。
# =====================================================================
set -u

ROOT="D:/1/zx-learn"
PY="C:/Users/20670/.workbuddy/binaries/python/versions/3.13.12/python.exe"
MYSQL="/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"
GW="http://localhost:8080"
OUT_DIR="$ROOT/logs/verify-draft-covers"
REPORT="$ROOT/docs/verify-draft-and-covers-report.txt"
REUSE_MODE="${REUSE:-0}"

cd "$ROOT" || exit 1
mkdir -p "$OUT_DIR" "$ROOT/docs"

PASS=0
FAIL=0
declare -a LINES=()

ok()   { PASS=$((PASS + 1)); LINES+=("  [PASS] $1"); }
bad()  { FAIL=$((FAIL + 1)); LINES+=("  [FAIL] $1"); }
info() { LINES+=("  [INFO] $1"); }
chk()  { if [ "$2" = "$3" ]; then ok "$1"; else bad "$1  <期望=$3 实际=$2>"; fi; }

MYSQL_PWD_VAL="$(grep -E '^MYSQL_ROOT_PASSWORD=' .env | cut -d= -f2-)"
sql() { "$MYSQL" -uroot -p"$MYSQL_PWD_VAL" -N --default-character-set=utf8mb4 -e "$1" 2>/dev/null | tr -d '\r'; }

port_busy() { netstat -ano 2>/dev/null | grep LISTENING | grep ":$1 " | awk '{print $5}' | awk '!seen[$0]++' | grep -q .; }
kill_port() {
  for pid in $(netstat -ano 2>/dev/null | grep LISTENING | grep ":$1 " | awk '{print $5}' | awk '!seen[$0]++'); do
    MSYS_NO_PATHCONV=1 taskkill /F /PID "$pid" >/dev/null 2>&1
  done
}

http() {  # http <method> <path> <token|-> <bodyfile|-> <outfile>  → 打印 HTTP 状态码
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

body() { printf '%s' "$2" > "$OUT_DIR/$1"; }   # 静默写请求体（调用方自己知道路径，别污染报告输出）

# 业务码断言：仅当 body.code == 200 才算通过（500 一定失败）
OK200()  { J "$1" "'5xx' if not isinstance(d,dict) else ('ok' if d.get('code')==200 else 'code:%s' % d.get('code'))"; }
# 拒绝类断言：必须是非 200 的 4xx 业务码（不接受 5xx / 200）
DENIED() { J "$1" "'5xx' if not isinstance(d,dict) else ('ok' if (isinstance(d.get('code'),int) and 400<=d['code']<500) else 'code:%s' % d.get('code'))"; }
# 业务码（原样取回，便于断言具体码值）
CODE() { J "$1" "d.get('code') if isinstance(d,dict) else 'NO_JSON'"; }

# ---------------------------------------------------------------------
# 合成夹具（雪花 id 段隔离，避免污染真实课程）
# ---------------------------------------------------------------------
DRAFT_NAME_A="【验证】草稿箱封面修复用例A"
DRAFT_NAME_A2="【验证】草稿箱封面修复用例A-已改名"
DRAFT_NAME_B="【验证】草稿箱封面修复用例B"
PUB_NAME="$DRAFT_NAME_A2"
COVER_URL="https://zx-learn.oss-cn-beijing.aliyuncs.com/covers/course-01.svg"

cleanup() {
  sql "USE zx_course; DELETE FROM course_catalogue WHERE course_id IN (SELECT course_id FROM course_draft WHERE name LIKE '【验证】草稿箱封面修复用例%' AND course_id IS NOT NULL);" >/dev/null
  sql "USE zx_course; DELETE FROM course WHERE name LIKE '【验证】草稿箱封面修复用例%';" >/dev/null
  sql "USE zx_course; DELETE FROM course_draft WHERE name LIKE '【验证】草稿箱封面修复用例%';" >/dev/null
}

if [ "$REUSE_MODE" != "1" ]; then
  trap 'cleanup; for p in 8080 8083; do kill_port "$p"; done' EXIT
fi
cleanup

LINES+=("=====================================================================")
LINES+=("知行智学 · 课程封面显示 + 教师端草稿箱 缺陷修复验证报告")
LINES+=("生成时间: $(date '+%Y-%m-%d %H:%M:%S')")
LINES+=("=====================================================================")

# =====================================================================
LINES+=("")
LINES+=("=== A. 课程封面 ===")

# A1. 逐条验证库里封面地址可公开访问
COVERS="$OUT_DIR/covers.tsv"
sql "USE zx_course; SELECT id, cover_url FROM course WHERE deleted=0 AND cover_url IS NOT NULL AND cover_url<>'' ORDER BY id;" > "$COVERS"

COVER_TOTAL=0
COVER_BAD=0
while IFS=$'\t' read -r cid curl_url; do
  [ -z "${cid:-}" ] && continue
  COVER_TOTAL=$((COVER_TOTAL + 1))
  BIN="$OUT_DIR/cover-$cid.bin"
  CODE=$(MSYS_NO_PATHCONV=1 curl.exe -sL -o "$BIN" -w "%{http_code}" -m 25 "$curl_url" 2>/dev/null)
  SIZE=$(wc -c < "$BIN" 2>/dev/null | tr -d ' ')
  if [ "$CODE" != "200" ] || [ "${SIZE:-0}" -lt 100 ]; then
    COVER_BAD=$((COVER_BAD + 1))
    info "课程 $cid 封面异常：http=$CODE size=${SIZE:-0} url=$curl_url"
  fi
done < "$COVERS"

chk "封面样本数 ≥ 13（1 门基础演示课 + 3001~3012）" "$([ "$COVER_TOTAL" -ge 13 ] && echo 1 || echo 0)" "1"
chk "全部封面可访问（HTTP 200 且响应体非空）" "$COVER_BAD" "0"

# A2. 不得残留已下线的文生图接口地址
DEAD=$(sql "USE zx_course; SELECT COUNT(*) FROM course WHERE cover_url LIKE '%trae-api-cn.mchost.guru%';")
chk "course 表无残留失效封面域名" "$DEAD" "0"
DEAD_DRAFT=$(sql "USE zx_course; SELECT COUNT(*) FROM course_draft WHERE cover_url LIKE '%trae-api-cn.mchost.guru%';")
chk "course_draft 表无残留失效封面域名" "$DEAD_DRAFT" "0"

# A3. 本地兜底封面资源齐全
LOCAL_MISSING=0
for n in 01 02 03 04 05 06 07 08 09 10 11 12 13; do
  [ -s "$ROOT/zx-web/public/covers/course-$n.svg" ] || LOCAL_MISSING=$((LOCAL_MISSING + 1))
done
chk "本地静态封面 course-01~13.svg 齐全" "$LOCAL_MISSING" "0"

# A4. 前端源码不得再出现失效域名（只判"真实 URL 字面量"，注释里的成因说明不算）
SRC_DEAD=$(grep -rl "https://trae-api-cn.mchost.guru" "$ROOT/zx-web/src" 2>/dev/null | wc -l | tr -d ' ')
chk "前端源码无失效封面/横幅 URL 引用" "$SRC_DEAD" "0"

# A5. 管理端列表使用带兜底的封面组件（不再裸 <img>）
COVER_COMP=$(grep -c "CourseCover" "$ROOT/zx-web/src/views/admin/AdminCoursesView.vue" 2>/dev/null | tr -d ' ')
chk "课程管理页引用 CourseCover 兜底组件" "$([ "${COVER_COMP:-0}" -ge 2 ] && echo 1 || echo 0)" "1"

# A6. 首页轮播横幅：本地静态资源，且文件真实存在
BANNER_REMOTE=$(grep -c "image: 'http" "$ROOT/zx-web/src/views/home/HomeView.vue" 2>/dev/null | tr -d ' ')
chk "首页轮播不再使用远程图片地址" "${BANNER_REMOTE:-0}" "0"
BANNER_MISSING=0
for n in 01 02 03; do
  [ -s "$ROOT/zx-web/public/banners/banner-$n.svg" ] || BANNER_MISSING=$((BANNER_MISSING + 1))
done
chk "本地轮播横幅 banner-01~03.svg 齐全" "$BANNER_MISSING" "0"

# =====================================================================
LINES+=("")
LINES+=("=== B. 教师端草稿箱 ===")

body login-tea.json '{"cellPhone":"13900000002","password":"123456"}'
TEA_HTTP=$(http POST /accounts/login - "$OUT_DIR/login-tea.json" "$OUT_DIR/r-login-tea.json")
TEA_TOKEN=$(J "$OUT_DIR/r-login-tea.json" "d['data']['accessToken']")
chk "教师登录 13900000002/123456" "$(J "$OUT_DIR/r-login-tea.json" "d['code']")" "200"

body login-stu.json '{"cellPhone":"13900000001","password":"123456"}'
http POST /accounts/login - "$OUT_DIR/login-stu.json" "$OUT_DIR/r-login-stu.json" >/dev/null
STU_TOKEN=$(J "$OUT_DIR/r-login-stu.json" "d['data']['accessToken']")

# ---- B7. 鉴权 ----
# 注意：/courses/draft/page 不在网关白名单 → 匿名请求由网关直接拒（真 HTTP 401）；
# 已登录但角色不符 → RoleInterceptor 抛 ForbiddenException，经统一异常处理器返回
# **HTTP 200 + body.code = 403**（本项目的业务异常一律走 body.code，不能只看 HTTP 状态码）。
A_HTTP=$(http GET "/courses/draft/page?pageNo=1&pageSize=10" - - "$OUT_DIR/r-draft-anon.json")
chk "匿名访问草稿箱被网关拦截（HTTP 401）" "$A_HTTP" "401"
http GET "/courses/draft/page?pageNo=1&pageSize=10" "$STU_TOKEN" - "$OUT_DIR/r-draft-stu.json" >/dev/null
chk "学员访问草稿箱 → 业务码 403" "$(CODE "$OUT_DIR/r-draft-stu.json")" "403"
http DELETE "/courses/draft/1" "$STU_TOKEN" - "$OUT_DIR/r-draft-del-stu.json" >/dev/null
chk "学员删除草稿 → 业务码 403" "$(CODE "$OUT_DIR/r-draft-del-stu.json")" "403"
# 越权写入用的请求体：角色校验先于业务逻辑执行，用固定值即可，不依赖后面造的草稿
body deny-save.json '{"name":"【验证】越权用例","coverUrl":"","price":0,"categoryIdLv1":1,"free":0}'
http POST /courses/baseInfo/save "$STU_TOKEN" "$OUT_DIR/deny-save.json" "$OUT_DIR/r-save-stu.json" >/dev/null
chk "学员保存草稿 → 业务码 403" "$(CODE "$OUT_DIR/r-save-stu.json")" "403"
body deny-upshelf.json '{"id":1}'
http POST /courses/upShelf "$STU_TOKEN" "$OUT_DIR/deny-upshelf.json" "$OUT_DIR/r-upshelf-stu.json" >/dev/null
chk "学员发布课程 → 业务码 403" "$(CODE "$OUT_DIR/r-upshelf-stu.json")" "403"

# ---- B2. 保存草稿 ----
body draft-a.json "{\"name\":\"$DRAFT_NAME_A\",\"coverUrl\":\"$COVER_URL\",\"price\":9900,\"categoryIdLv1\":1,\"categoryIdLv2\":11,\"free\":0,\"description\":\"验证草稿箱保存链路\",\"step\":2,\"catalogueList\":[{\"name\":\"第一章 验证章节\",\"sections\":[{\"name\":\"1.1 验证小节一\"},{\"name\":\"1.2 验证小节二\"}]},{\"name\":\"第二章 验证章节\",\"sections\":[{\"name\":\"2.1 验证小节三\"}]}]}"
http POST /courses/baseInfo/save "$TEA_TOKEN" "$OUT_DIR/draft-a.json" "$OUT_DIR/r-draft-save.json" >/dev/null
chk "教师保存课程基本信息 → 业务码 200" "$(OK200 "$OUT_DIR/r-draft-save.json")" "ok"
DRAFT_ID=$(J "$OUT_DIR/r-draft-save.json" "d['data']")
# 注意：服务端把 Long/BIGINT 序列化为 **JSON 字符串**（避免前端 JS 精度丢失），
# 所以所有 id 比较都要按字符串比，不能按数字比 —— 按数字比会永远为假，
# "期望 0" 的断言会变成假阳性通过。
chk "返回草稿 id（非空数字串）" "$(J "$OUT_DIR/r-draft-save.json" "'ok' if str(d.get('data') or '').isdigit() and int(d['data'])>0 else 'bad:%s' % d.get('data')")" "ok"

# ---- B1 + B2. 草稿箱立刻能看到（关键回归点）----
http GET "/courses/draft/page?pageNo=1&pageSize=10" "$TEA_TOKEN" - "$OUT_DIR/r-draft-page.json" >/dev/null
chk "草稿箱列表接口 → 业务码 200" "$(OK200 "$OUT_DIR/r-draft-page.json")" "ok"
chk "新建的草稿出现在草稿箱" "$(J "$OUT_DIR/r-draft-page.json" "1 if any(str(x['id'])=='$DRAFT_ID' for x in d['data']['list']) else 0")" "1"
chk "草稿箱带出编辑进度 step=2" "$(J "$OUT_DIR/r-draft-page.json" "1 if [x.get('step') for x in d['data']['list'] if str(x['id'])=='$DRAFT_ID'][0]==2 else 0")" "1"
chk "草稿箱带出封面地址" "$(J "$OUT_DIR/r-draft-page.json" "1 if [x.get('coverUrl') for x in d['data']['list'] if str(x['id'])=='$DRAFT_ID'][0]=='$COVER_URL' else 0")" "1"

# 草稿箱不能混入正式课程（证明它查的是 course_draft 而不是 course）
http GET "/courses/draft/page?pageNo=1&pageSize=200" "$TEA_TOKEN" - "$OUT_DIR/r-draft-page-all.json" >/dev/null
OFFICIAL_IN_DRAFT=$(J "$OUT_DIR/r-draft-page-all.json" "1 if any(str(x['id']) in ('1','3001','3002','3003','3004','3005') for x in d['data']['list']) else 0")
chk "正式课程 id 不会混进草稿箱（说明查的是草稿表）" "$OFFICIAL_IN_DRAFT" "0"
DRAFT_DB_CNT=$(sql "USE zx_course; SELECT COUNT(*) FROM course_draft WHERE deleted=0 AND submitted=0;")
chk "草稿箱条数与 course_draft（submitted=0）一致" "$(J "$OUT_DIR/r-draft-page-all.json" "d['data']['total']")" "$DRAFT_DB_CNT"

# ---- B3. 章节目录真的存下来了 ----
http GET "/courses/baseInfo/$DRAFT_ID" "$TEA_TOKEN" - "$OUT_DIR/r-draft-base.json" >/dev/null
chk "读取草稿编辑态 → 业务码 200" "$(OK200 "$OUT_DIR/r-draft-base.json")" "ok"
chk "草稿读回 2 个章" "$(J "$OUT_DIR/r-draft-base.json" "len(d['data'].get('catalogueList') or [])")" "2"
chk "第一章含 2 个小节" "$(J "$OUT_DIR/r-draft-base.json" "len((d['data'].get('catalogueList') or [{}])[0].get('sections') or [])")" "2"
chk "章节名原样回填" "$(J "$OUT_DIR/r-draft-base.json" "1 if (d['data'].get('catalogueList') or [{}])[0].get('name')=='第一章 验证章节' else 0")" "1"
JSON_DB=$(sql "USE zx_course; SELECT IFNULL(CHAR_LENGTH(catalogue_json),0) FROM course_draft WHERE id=$DRAFT_ID;")
chk "catalogue_json 已落库（长度 > 0）" "$([ "${JSON_DB:-0}" -gt 0 ] && echo 1 || echo 0)" "1"

# ---- B4. 重复保存是更新，不是新增 ----
CNT_BEFORE=$(sql "USE zx_course; SELECT COUNT(*) FROM course_draft WHERE deleted=0;")
body draft-a2.json "{\"id\":$DRAFT_ID,\"name\":\"$DRAFT_NAME_A2\",\"coverUrl\":\"$COVER_URL\",\"price\":9900,\"categoryIdLv1\":1,\"categoryIdLv2\":11,\"free\":0,\"description\":\"验证草稿箱保存链路（已更新）\",\"step\":2,\"catalogueList\":[{\"name\":\"第一章 验证章节\",\"sections\":[{\"name\":\"1.1 验证小节一\"},{\"name\":\"1.2 验证小节二\"}]},{\"name\":\"第二章 验证章节\",\"sections\":[{\"name\":\"2.1 验证小节三\"}]}]}"
http POST /courses/baseInfo/save "$TEA_TOKEN" "$OUT_DIR/draft-a2.json" "$OUT_DIR/r-draft-save2.json" >/dev/null
chk "重复保存（带 id）→ 业务码 200" "$(OK200 "$OUT_DIR/r-draft-save2.json")" "ok"
chk "重复保存返回同一草稿 id" "$(J "$OUT_DIR/r-draft-save2.json" "d['data']")" "$DRAFT_ID"
CNT_AFTER=$(sql "USE zx_course; SELECT COUNT(*) FROM course_draft WHERE deleted=0;")
chk "重复保存不会新增草稿（条数不变）" "$CNT_AFTER" "$CNT_BEFORE"

# ---- B6. 发布：草稿离开草稿箱、正式课程出现、目录同步 ----
chk "发布前校验通过（不再因缺授课老师被拦）" "$(http GET "/courses/checkBeforeUpShelf/$DRAFT_ID" "$TEA_TOKEN" - "$OUT_DIR/r-check.json" | tr -d '\r')" "200"
chk "发布前校验业务码 200" "$(OK200 "$OUT_DIR/r-check.json")" "ok"
body upshelf.json "{\"id\":$DRAFT_ID}"
http POST /courses/upShelf "$TEA_TOKEN" "$OUT_DIR/upshelf.json" "$OUT_DIR/r-upshelf.json" >/dev/null
chk "发布课程 → 业务码 200" "$(OK200 "$OUT_DIR/r-upshelf.json")" "ok"

COURSE_ID=$(sql "USE zx_course; SELECT IFNULL(course_id,0) FROM course_draft WHERE id=$DRAFT_ID;")
chk "草稿已绑定正式课程 id" "$([ "${COURSE_ID:-0}" -gt 0 ] && echo 1 || echo 0)" "1"
http GET "/courses/draft/page?pageNo=1&pageSize=200" "$TEA_TOKEN" - "$OUT_DIR/r-draft-page-after.json" >/dev/null
chk "已发布的草稿已离开草稿箱" "$(J "$OUT_DIR/r-draft-page-after.json" "1 if any(str(x['id'])=='$DRAFT_ID' for x in d['data']['list']) else 0")" "0"

http GET "/courses/$COURSE_ID" "$TEA_TOKEN" - "$OUT_DIR/r-course-new.json" >/dev/null
chk "新课程详情 → 业务码 200" "$(OK200 "$OUT_DIR/r-course-new.json")" "ok"
chk "新课程状态为上架(1)" "$(J "$OUT_DIR/r-course-new.json" "d['data'].get('status')")" "1"
chk "新课程目录同步为 2 章" "$(J "$OUT_DIR/r-course-new.json" "len(d['data'].get('catalogues') or [])")" "2"
chk "新课程目录同步为 3 小节" "$(J "$OUT_DIR/r-course-new.json" "sum(len(c.get('sections') or []) for c in (d['data'].get('catalogues') or []))")" "3"
CAT_DB=$(sql "USE zx_course; SELECT COUNT(*) FROM course_catalogue WHERE course_id=$COURSE_ID AND deleted=0;")
chk "course_catalogue 落库 5 行（2 章 + 3 节）" "$CAT_DB" "5"

# 幂等性：再发布一次不应产生重复目录
body upshelf2.json "{\"id\":$DRAFT_ID}"
http POST /courses/upShelf "$TEA_TOKEN" "$OUT_DIR/upshelf2.json" "$OUT_DIR/r-upshelf2.json" >/dev/null
chk "重复发布仍为 200" "$(OK200 "$OUT_DIR/r-upshelf2.json")" "ok"
CAT_DB2=$(sql "USE zx_course; SELECT COUNT(*) FROM course_catalogue WHERE course_id=$COURSE_ID AND deleted=0;")
chk "重复发布不产生重复目录（仍 5 行）" "$CAT_DB2" "5"

# 已发布的草稿不允许用草稿接口删除
DEL_PUB=$(http DELETE "/courses/draft/$DRAFT_ID" "$TEA_TOKEN" - "$OUT_DIR/r-del-pub.json")
chk "已发布草稿禁止用草稿接口删除" "$(DENIED "$OUT_DIR/r-del-pub.json")" "ok"

# ---- B5. 删除草稿 ----
body draft-b.json "{\"name\":\"$DRAFT_NAME_B\",\"coverUrl\":\"$COVER_URL\",\"price\":0,\"categoryIdLv1\":2,\"free\":1,\"step\":1}"
http POST /courses/baseInfo/save "$TEA_TOKEN" "$OUT_DIR/draft-b.json" "$OUT_DIR/r-draft-save-b.json" >/dev/null
chk "再建一条草稿 → 业务码 200" "$(OK200 "$OUT_DIR/r-draft-save-b.json")" "ok"
DRAFT_B=$(J "$OUT_DIR/r-draft-save-b.json" "d['data']")
chk "删除草稿 → HTTP 200" "$(http DELETE "/courses/draft/$DRAFT_B" "$TEA_TOKEN" - "$OUT_DIR/r-draft-del.json" | tr -d '\r')" "200"
chk "删除草稿 → 业务码 200" "$(OK200 "$OUT_DIR/r-draft-del.json")" "ok"
http GET "/courses/draft/page?pageNo=1&pageSize=200" "$TEA_TOKEN" - "$OUT_DIR/r-draft-page-del.json" >/dev/null
chk "删除后草稿箱不再出现" "$(J "$OUT_DIR/r-draft-page-del.json" "1 if any(str(x['id'])=='$DRAFT_B' for x in d['data']['list']) else 0")" "0"
DEL_DB=$(sql "USE zx_course; SELECT deleted FROM course_draft WHERE id=$DRAFT_B;")
chk "草稿为逻辑删除（deleted=1）" "$DEL_DB" "1"
chk "删除草稿不影响正式课程表" "$(sql "USE zx_course; SELECT COUNT(*) FROM course WHERE id=$COURSE_ID AND deleted=0;")" "1"

# ---- B8. 边界与校验（严格写入，不静默截断）----
body bad-empty.json '{"name":"   ","coverUrl":"","price":0,"categoryIdLv1":1,"free":0}'
http POST /courses/baseInfo/save "$TEA_TOKEN" "$OUT_DIR/bad-empty.json" "$OUT_DIR/r-bad-empty.json" >/dev/null
chk "空课程名被拒绝" "$(DENIED "$OUT_DIR/r-bad-empty.json")" "ok"

LONG_NAME=$(printf 'A%.0s' $(seq 1 200))
body bad-long.json "{\"name\":\"$LONG_NAME\",\"coverUrl\":\"\",\"price\":0,\"categoryIdLv1\":1,\"free\":0}"
http POST /courses/baseInfo/save "$TEA_TOKEN" "$OUT_DIR/bad-long.json" "$OUT_DIR/r-bad-long.json" >/dev/null
chk "超长课程名被显式拒绝（不静默截断）" "$(DENIED "$OUT_DIR/r-bad-long.json")" "ok"

body bad-free.json '{"name":"【验证】免费课挂价格","coverUrl":"","price":9900,"categoryIdLv1":1,"free":1}'
http POST /courses/baseInfo/save "$TEA_TOKEN" "$OUT_DIR/bad-free.json" "$OUT_DIR/r-bad-free.json" >/dev/null
chk "免费课挂非零价格被拒绝" "$(DENIED "$OUT_DIR/r-bad-free.json")" "ok"

body bad-ghost.json '{"id":999999999999999999,"name":"【验证】不存在的草稿","coverUrl":"","price":0,"categoryIdLv1":1,"free":0}'
http POST /courses/baseInfo/save "$TEA_TOKEN" "$OUT_DIR/bad-ghost.json" "$OUT_DIR/r-bad-ghost.json" >/dev/null
chk "更新不存在的草稿被拒绝" "$(DENIED "$OUT_DIR/r-bad-ghost.json")" "ok"

body bad-longchap.json '{"name":"【验证】超长章节名","coverUrl":"","price":0,"categoryIdLv1":1,"free":0,"catalogueList":[{"name":"'"$(printf 'B%.0s' $(seq 1 200))"'","sections":[]}]}'
http POST /courses/baseInfo/save "$TEA_TOKEN" "$OUT_DIR/bad-longchap.json" "$OUT_DIR/r-bad-longchap.json" >/dev/null
chk "超长章节名被显式拒绝" "$(DENIED "$OUT_DIR/r-bad-longchap.json")" "ok"

# ---- 管理端（员工）同样可用草稿箱 ----
body login-adm.json '{"cellPhone":"13800000001","password":"123456"}'
http POST /accounts/login - "$OUT_DIR/login-adm.json" "$OUT_DIR/r-login-adm.json" >/dev/null
ADM_TOKEN=$(J "$OUT_DIR/r-login-adm.json" "d['data']['accessToken']")
http GET "/courses/draft/page?pageNo=1&pageSize=10" "$ADM_TOKEN" - "$OUT_DIR/r-draft-adm.json" >/dev/null
chk "员工可访问草稿箱 → 业务码 200" "$(OK200 "$OUT_DIR/r-draft-adm.json")" "ok"

# =====================================================================
cleanup

LINES+=("")
LINES+=("=== 汇总 ===")
LINES+=("  通过: $PASS")
LINES+=("  失败: $FAIL")
if [ "$FAIL" -gt 0 ]; then
  LINES+=("  结论: 存在未通过项，请查看上面的 [FAIL] 行")
else
  LINES+=("  结论: 全部通过")
fi

printf '%s\n' "${LINES[@]}" | tee "$REPORT"
echo
echo "报告已写入: $REPORT"
[ "$FAIL" -eq 0 ]
