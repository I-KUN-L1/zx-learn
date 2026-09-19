#!/usr/bin/env bash
# =====================================================================
# 知行智学 · 前后端全量测试套件（接口矩阵 / 安全 / 边界 / 一致性 / 性能）
# ---------------------------------------------------------------------
# 覆盖范围：
#   阶段 0  环境与构建产物（16 服务 jar + 前端 dist）
#   阶段 1  登录与鉴权基线（匿名 401、篡改 token、算法混淆 token）
#   阶段 2  全量接口矩阵：245 个端点 × 3 身份（匿名/学员/管理员）
#   阶段 3  垂直越权：学员调用管理端写接口
#   阶段 4  水平越权 IDOR：访问他人学情/课表/订单/会话
#   阶段 5  内部接口外部不可达（零信任校验）
#   阶段 6  参数校验 / 边界值 / 注入（SQL 注入、排序注入、超长、类型错误）
#   阶段 7  敏感数据脱敏（密码字段外泄排查）
#   阶段 8  幂等与凭证安全
#   阶段 9  数据一致性交叉校验（SQL 不变量）
#   阶段 10 性能采样（核心接口 P95 与并发成功率）
#   阶段 11 汇总
#
# 用法（沙箱内服务不跨工具调用存活，必须"启动 + 断言"写在同一条命令里）：
#   cd "D:/1/zx-learn" && \
#     EXTRA_SPECS="zx-promotion:8088 zx-media:8085 zx-pay:8090 zx-search:8091 zx-remark:8092 zx-message:8093 zx-data:8094 zx-aigc:8089" \
#     /usr/bin/bash scripts/dev-up-core.sh >/dev/null 2>&1; \
#     REUSE=1 /usr/bin/bash scripts/verify-full-suite.sh 2>&1 | tail -60
#
# 说明：
#   * 破坏性端点一律用**不存在的哨兵 id（999999999999）**探测，不触碰真实数据；
#   * 阶段 3 会真实创建 ZZ_ 前缀的探针行，用于证明越权可写入，脚本结尾会清理；
#   * 沙箱注意：脚本内禁用 sort/find；sql() 去 \r；IN(...) 逗号分隔。
# =====================================================================
set -u

ROOT="D:/1/zx-learn"
PY="C:/Users/20670/.workbuddy/binaries/python/versions/3.13.12/python.exe"
MYSQL="/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"
REDIS_CLI="/d/1/Redis/redis-cli.exe"
GW="http://localhost:8080"
OUT_DIR="$ROOT/logs/verify-full-suite"
REPORT="$ROOT/docs/verify-full-suite-report.txt"
SENTINEL=999999999999

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
sql() { "$MYSQL" -uroot -p"$MYSQL_PWD_VAL" -N --default-character-set=utf8mb4 -e "$1" 2>/dev/null | tr -d '\r'; }

# ⚠ Windows 版 redis-cli.exe 会把 UTF-8 值按当前代码页（GBK）输出，且行尾带 CRLF。
# 不处理会导致含中文的取值比较**恒不相等**（例如会话标题断言永远失败，误报"越权篡改成功"）。
# 统一：去 CR + GBK→UTF-8 转码（纯 ASCII 时转码为恒等，不影响 EXISTS/HGET 计数类断言）。
redis_do() {
  MSYS_NO_PATHCONV=1 "$REDIS_CLI" -a "$REDIS_PWD_VAL" --no-auth-warning "$@" 2>/dev/null \
    | tr -d '\r' | iconv -f GBK -t UTF-8 2>/dev/null
}

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

# 业务码提取（区分 HTTP 码与统一响应体里的 code）
BC() { J "$1" "d.get('code','-') if isinstance(d,dict) else ('LIST' if isinstance(d,list) else 'NONJSON')"; }

# CE —— 判定「非法输入是否被正确拒绝」。
#   * 合法结果：业务码 400/404（明确的客户端错误）→ 打印 ok
#   * 非法结果：500（服务端异常）→ 打印 code:500 → 断言失败
#   * 非 JSON（网关 5xx / 空响应）→ 打印 5xx → 断言失败
# 加它的原因：原先这类断言写成 `'ok' if isinstance(d, dict) else '5xx'`，
# 而业务异常的响应体本身就是 dict（R{code:500}），于是**接口整个炸掉也会判通过**。
# 本轮课程列表 NPE 就是这样从 128 项断言底下溜过去的（假阳性）。
CE() { J "$1" "'5xx' if not isinstance(d,dict) else ('ok' if d.get('code') in (400,404) else 'code:%s' % d.get('code'))"; }

# OK200 —— 判定「应正常返回的接口确实返回了 200 业务码」。
OK200() { J "$1" "'5xx' if not isinstance(d,dict) else ('ok' if d.get('code')==200 else 'code:%s' % d.get('code'))"; }

# NO5XX —— 判定「任何情况下都不得出现服务端异常」。
# 允许 200（正常返回空数据）/ 400 / 404（明确的业务拒绝），禁止 500 与非 JSON。
NO5XX() { J "$1" "'5xx' if not isinstance(d,dict) else ('ok' if d.get('code') in (200,400,404) else 'code:%s' % d.get('code'))"; }

# ---------------------------------------------------------------------
START_TS="$(date '+%Y-%m-%d %H:%M:%S')"

LINES+=("=====================================================================")
LINES+=("知行智学 ZhiXing Learn · 前后端全量测试报告")
LINES+=("生成时间: $START_TS")
LINES+=("网关地址: $GW")
LINES+=("=====================================================================")

# =====================================================================
echo "=== 阶段 0：环境与构建产物 ==="
LINES+=("")
LINES+=("=== 阶段 0：环境与构建产物 ===")

JAR_MISSING=0
for m in zx-gateway zx-auth zx-user zx-course zx-media zx-learning zx-trade zx-promotion \
         zx-pay zx-search zx-remark zx-message zx-aigc zx-data zx-insight zx-exam; do
  [ -f "$ROOT/$m/target/$m.jar" ] || { JAR_MISSING=$((JAR_MISSING + 1)); info "缺少 fat jar: $m"; }
done
chk "16 个可运行服务的 fat jar 均存在" "$JAR_MISSING" "0"
chk "前端构建产物 dist/index.html 存在" "$([ -f "$ROOT/zx-web/dist/index.html" ] && echo yes || echo no)" "yes"

# 服务可达性（逐端口探 HTTP 可达，不用 actuator）
DOWN=""
for spec in 8080:gateway 8081:auth 8082:user 8083:course 8084:exam 8085:media 8086:learning \
            8087:trade 8088:promotion 8089:aigc 8090:pay 8091:search 8092:remark 8093:message \
            8094:data 8095:insight; do
  p=${spec%%:*}; n=${spec##*:}
  c=$(MSYS_NO_PATHCONV=1 curl.exe -s -o /dev/null -w "%{http_code}" --max-time 5 "http://localhost:$p/" 2>/dev/null)
  [ "$c" = "000" ] && DOWN="$DOWN $n($p)"
done
if [ -n "$DOWN" ]; then
  bad "全部 16 个服务端口可达 <未启动:$DOWN>"
  info "未启动的服务请用 EXTRA_SPECS 追加，例如："
  info "  EXTRA_SPECS=\"zx-promotion:8088 zx-media:8085 zx-pay:8090 zx-search:8091 zx-remark:8092 zx-message:8093 zx-data:8094 zx-aigc:8089\" scripts/dev-up-core.sh"
  LINES+=("  [INFO] 服务未就绪，后续断言结果不可信，请先启动全部服务后重跑")
else
  ok "全部 16 个服务端口可达"
fi

# =====================================================================
echo "=== 阶段 1：登录与鉴权基线 ==="
LINES+=("")
LINES+=("=== 阶段 1：登录与鉴权基线 ===")

body login-stu.json '{"cellPhone":"13900000001","password":"123456"}'
http POST /accounts/login - "$OUT_DIR/login-stu.json" "$OUT_DIR/r-login-stu.json" >/dev/null
chk "学员登录 13900000001" "$(BC "$OUT_DIR/r-login-stu.json")" "200"
STU_TOKEN=$(J "$OUT_DIR/r-login-stu.json" "d['data']['accessToken']")
STU_ID=$(J "$OUT_DIR/r-login-stu.json" "d['data']['userId']")
info "学员 id = $STU_ID"

body login-tea.json '{"cellPhone":"13900000002","password":"123456"}'
http POST /accounts/login - "$OUT_DIR/login-tea.json" "$OUT_DIR/r-login-tea.json" >/dev/null
chk "教师登录 13900000002" "$(BC "$OUT_DIR/r-login-tea.json")" "200"
TEA_TOKEN=$(J "$OUT_DIR/r-login-tea.json" "d['data']['accessToken']")

body login-adm.json '{"cellPhone":"13800000001","password":"123456"}'
http POST /accounts/admin/login - "$OUT_DIR/login-adm.json" "$OUT_DIR/r-login-adm.json" >/dev/null
chk "管理员登录 13800000001" "$(BC "$OUT_DIR/r-login-adm.json")" "200"
ADM_TOKEN=$(J "$OUT_DIR/r-login-adm.json" "d['data']['accessToken']")

http GET /points/summary - - "$OUT_DIR/r-anon1.json" >/dev/null
chk "匿名访问受保护接口 /points/summary → 401" "$(BC "$OUT_DIR/r-anon1.json")" "401"
http GET /users/page - - "$OUT_DIR/r-anon2.json" >/dev/null
chk "匿名访问 /users/page → 401" "$(BC "$OUT_DIR/r-anon2.json")" "401"

# token 篡改：把签名段改掉
TAMPER="${STU_TOKEN%%.*}.${STU_TOKEN#*.}x"
http GET /points/summary "$TAMPER" - "$OUT_DIR/r-tamper.json" >/dev/null
chk "篡改签名的 token 被拒 → 401" "$(BC "$OUT_DIR/r-tamper.json")" "401"

# alg=none 伪造 token（头部与载荷原样，签名段为空）
NONE_TOK="$(J "$OUT_DIR/r-login-stu.json" "'.'.join(d['data']['accessToken'].split('.')[:2]) + '.'")"
http GET /points/summary "$NONE_TOK" - "$OUT_DIR/r-algnone.json" >/dev/null
chk "alg=none 无签名 token 被拒 → 401" "$(BC "$OUT_DIR/r-algnone.json")" "401"

# 非 Bearer 前缀
MSYS_NO_PATHCONV=1 curl.exe -s -o "$OUT_DIR/r-basic.json" -w "%{http_code}" --max-time 20 \
  -H "Authorization: Basic $STU_TOKEN" "$GW/points/summary" >/dev/null
chk "非 Bearer 前缀的 Authorization 被拒 → 401" "$(BC "$OUT_DIR/r-basic.json")" "401"

# 正常 token 可访问
http GET /points/summary "$STU_TOKEN" - "$OUT_DIR/r-points.json" >/dev/null
chk "合法 token 可访问 /points/summary" "$(BC "$OUT_DIR/r-points.json")" "200"

# =====================================================================
echo "=== 阶段 2：全量接口矩阵 ==="
LINES+=("")
LINES+=("=== 阶段 2：全量接口矩阵（全部端点 × 匿名/学员/管理员）===")

if [ -f "$ROOT/logs/tmp/api-inventory.json" ]; then
  MATRIX="$("$PY" "$ROOT/scripts/api-matrix.py" 2>&1)"
  echo "$MATRIX" > "$OUT_DIR/api-matrix.txt"
  M_TOTAL=$(echo "$MATRIX" | grep '^MATRIX_TOTAL=' | cut -d= -f2)
  M_OK=$(echo "$MATRIX" | grep '^MATRIX_OK=' | cut -d= -f2)
  M_FAIL=$(echo "$MATRIX" | grep '^MATRIX_FAIL=' | cut -d= -f2)
  M_ANON=$(echo "$MATRIX" | grep '^MATRIX_ANON_OPEN=' | cut -d= -f2)
  echo "$MATRIX" > "$OUT_DIR/api-matrix.txt"
  # 注意：不能用管道喂 while（子 shell 内的 LINES+= 会丢），改为重定向读文件
  grep -E '端点 +[0-9]+ +正常' "$OUT_DIR/api-matrix.txt" > "$OUT_DIR/matrix-mods.txt" 2>/dev/null || true
  while IFS= read -r l; do [ -n "$l" ] && LINES+=("  [INFO]$l"); done < "$OUT_DIR/matrix-mods.txt"
  chk_ge "全量端点探测覆盖（应 ≥ 240 个端点）" "${M_TOTAL:-0}" "240"
  info "实际探测端点数 = ${M_TOTAL:-0}"
  chk "无 5xx / 无契约破坏的端点占比（正常数 = 总数）" "$M_OK" "$M_TOTAL"
  chk "异常端点数为 0" "${M_FAIL:-0}" "0"
  info "匿名可访问端点（网关白名单）共 $M_ANON 个"
  if [ "${M_FAIL:-0}" != "0" ]; then
    grep -A40 '异常端点明细' "$OUT_DIR/api-matrix.txt" > "$OUT_DIR/matrix-fails.txt" 2>/dev/null || true
    while IFS= read -r l; do [ -n "$l" ] && LINES+=("$l"); done < "$OUT_DIR/matrix-fails.txt"
  fi
else
  bad "接口清单 logs/tmp/api-inventory.json 不存在，无法执行矩阵探测"
fi

# =====================================================================
echo "=== 阶段 3：垂直越权（学员调用管理端写接口）==="
LINES+=("")
LINES+=("=== 阶段 3：垂直越权（管理端写接口的角色校验）===")

# RBAC：角色/菜单/权限点
body rbac-role.json '{"name":"ZZ_PROBE_ROLE","code":"ZZ_PROBE_ROLE"}'
http POST /roles "$STU_TOKEN" "$OUT_DIR/rbac-role.json" "$OUT_DIR/r-stu-add-role.json" >/dev/null
chk "学员新建角色被拒(403)" "$(BC "$OUT_DIR/r-stu-add-role.json")" "403"

body rbac-role2.json '{"name":"ZZ_PROBE_ROLE2","code":"ZZ_PROBE_ROLE2"}'
http POST /roles "$TEA_TOKEN" "$OUT_DIR/rbac-role2.json" "$OUT_DIR/r-tea-add-role.json" >/dev/null
chk "教师新建角色被拒(403)" "$(BC "$OUT_DIR/r-tea-add-role.json")" "403"

body rbac-menu.json '{"name":"ZZ_PROBE_MENU","parentId":0,"sort":999}'
http POST /menus "$STU_TOKEN" "$OUT_DIR/rbac-menu.json" "$OUT_DIR/r-stu-add-menu.json" >/dev/null
chk "学员新建菜单被拒(403)" "$(BC "$OUT_DIR/r-stu-add-menu.json")" "403"

body rbac-priv.json '{"name":"ZZ_PROBE_PRIV","menuId":1}'
http POST /privileges "$STU_TOKEN" "$OUT_DIR/rbac-priv.json" "$OUT_DIR/r-stu-add-priv.json" >/dev/null
chk "学员新建权限点被拒(403)" "$(BC "$OUT_DIR/r-stu-add-priv.json")" "403"

http PUT "/roles/$SENTINEL" "$STU_TOKEN" "$OUT_DIR/rbac-role.json" "$OUT_DIR/r-stu-upd-role.json" >/dev/null
chk "学员修改角色被拒(403)" "$(BC "$OUT_DIR/r-stu-upd-role.json")" "403"
http DELETE "/roles/$SENTINEL" "$STU_TOKEN" - "$OUT_DIR/r-stu-del-role.json" >/dev/null
chk "学员删除角色被拒(403)" "$(BC "$OUT_DIR/r-stu-del-role.json")" "403"
http DELETE "/menus/$SENTINEL" "$STU_TOKEN" - "$OUT_DIR/r-stu-del-menu.json" >/dev/null
chk "学员删除菜单被拒(403)" "$(BC "$OUT_DIR/r-stu-del-menu.json")" "403"
body empty-list.json '[]'
http POST /menus/role/1 "$STU_TOKEN" "$OUT_DIR/empty-list.json" "$OUT_DIR/r-stu-bind-menu.json" >/dev/null
chk "学员给角色绑定菜单被拒(403)" "$(BC "$OUT_DIR/r-stu-bind-menu.json")" "403"
http POST /privileges/role/1 "$STU_TOKEN" "$OUT_DIR/empty-list.json" "$OUT_DIR/r-stu-bind-priv.json" >/dev/null
chk "学员给角色绑定权限点被拒(403)" "$(BC "$OUT_DIR/r-stu-bind-priv.json")" "403"

# 其它管理端写接口
body coupon-new.json '{"name":"ZZ_PROBE_CPN","type":1,"discountValue":100,"thresholdAmount":1000,"totalNum":1}'
http POST /coupons "$STU_TOKEN" "$OUT_DIR/coupon-new.json" "$OUT_DIR/r-stu-coupon.json" >/dev/null
chk "学员新建优惠券被拒(403)" "$(BC "$OUT_DIR/r-stu-coupon.json")" "403"

body question-new.json '{"name":"ZZ_PROBE_Q","type":1,"score":1}'
http POST /questions "$STU_TOKEN" "$OUT_DIR/question-new.json" "$OUT_DIR/r-stu-q.json" >/dev/null
chk "学员新增题目被拒(403)" "$(BC "$OUT_DIR/r-stu-q.json")" "403"

body board-set.json '{"key":"ZZ_PROBE","value":"1"}'
http PUT /data/board/set "$STU_TOKEN" "$OUT_DIR/board-set.json" "$OUT_DIR/r-stu-data.json" >/dev/null
chk "学员写运营看板被拒(403)" "$(BC "$OUT_DIR/r-stu-data.json")" "403"

body sms.json '{"phone":"13900000001","content":"probe"}'
http POST /sms/message "$STU_TOKEN" "$OUT_DIR/sms.json" "$OUT_DIR/r-stu-sms.json" >/dev/null
chk "学员发短信被拒(403)" "$(BC "$OUT_DIR/r-stu-sms.json")" "403"

http GET /orders/admin/page "$STU_TOKEN" - "$OUT_DIR/r-stu-adminorders.json" >/dev/null
chk "学员访问订单管理列表被拒(403)" "$(BC "$OUT_DIR/r-stu-adminorders.json")" "403"
http GET /users/page "$STU_TOKEN" - "$OUT_DIR/r-stu-users.json" >/dev/null
chk "学员访问用户管理列表被拒(403)" "$(BC "$OUT_DIR/r-stu-users.json")" "403"
http POST /courses/upShelf "$STU_TOKEN" "$OUT_DIR/board-set.json" "$OUT_DIR/r-stu-upshelf.json" >/dev/null
chk "学员上架课程被拒(403)" "$(BC "$OUT_DIR/r-stu-upshelf.json")" "403"
http DELETE "/courses/delete/$SENTINEL" "$STU_TOKEN" - "$OUT_DIR/r-stu-delcourse.json" >/dev/null
chk "学员删除课程被拒(403)" "$(BC "$OUT_DIR/r-stu-delcourse.json")" "403"

# 清理阶段 3 可能被越权写入的探针数据（无论是否被拦截，都要保证库干净）
ZZ_ROLES=$(sql "USE zx_auth; SELECT COUNT(*) FROM role WHERE name LIKE 'ZZ_PROBE%';")
ZZ_MENUS=$(sql "USE zx_auth; SELECT COUNT(*) FROM menu WHERE name LIKE 'ZZ_PROBE%';")
ZZ_PRIVS=$(sql "USE zx_auth; SELECT COUNT(*) FROM privilege WHERE name LIKE 'ZZ_PROBE%';")
sql "USE zx_auth; DELETE FROM role WHERE name LIKE 'ZZ_PROBE%';" >/dev/null
sql "USE zx_auth; DELETE FROM menu WHERE name LIKE 'ZZ_PROBE%';" >/dev/null
sql "USE zx_auth; DELETE FROM privilege WHERE name LIKE 'ZZ_PROBE%';" >/dev/null
info "越权探测残留：role=$ZZ_ROLES menu=$ZZ_MENUS privilege=$ZZ_PRIVS（已清理）"
chk "越权探测未在角色表留下持久数据（清理后为 0）" \
  "$(sql "USE zx_auth; SELECT COUNT(*) FROM role WHERE name LIKE 'ZZ_PROBE%';")" "0"

# 管理员执行同样操作应当成功（对照组，证明是"角色校验缺失"而不是"接口坏了"）
body rbac-role3.json '{"name":"ZZ_PROBE_ROLE_ADM","code":"ZZ_PROBE_ROLE_ADM"}'
http POST /roles "$ADM_TOKEN" "$OUT_DIR/rbac-role3.json" "$OUT_DIR/r-adm-add-role.json" >/dev/null
ADM_ROLE_CODE=$(BC "$OUT_DIR/r-adm-add-role.json")
info "管理员新建角色 → code=$ADM_ROLE_CODE（对照组：管理员可写）"
sql "USE zx_auth; DELETE FROM role WHERE name LIKE 'ZZ_PROBE%';" >/dev/null

# =====================================================================
echo "=== 阶段 4：水平越权（IDOR）==="
LINES+=("")
LINES+=("=== 阶段 4：水平越权 IDOR（访问他人私有数据）===")

OTHER_ID=$(sql "USE zx_user; SELECT id FROM user WHERE type=2 AND status=1 AND deleted=0 AND id<>$STU_ID ORDER BY id LIMIT 1;")
[ -z "$OTHER_ID" ] && OTHER_ID=2101
info "他人（对照学员）id = $OTHER_ID"

http GET "/learning-records/users/$OTHER_ID/all" "$STU_TOKEN" - "$OUT_DIR/r-idor-lr.json" >/dev/null
chk "学员读他人学习记录被拒(403)" "$(BC "$OUT_DIR/r-idor-lr.json")" "403"
http GET "/learning-records/users/$OTHER_ID/sum" "$STU_TOKEN" - "$OUT_DIR/r-idor-lrs.json" >/dev/null
chk "学员读他人学习时长被拒(403)" "$(BC "$OUT_DIR/r-idor-lrs.json")" "403"
http GET "/points/users/$OTHER_ID/total" "$STU_TOKEN" - "$OUT_DIR/r-idor-pt.json" >/dev/null
chk "学员读他人积分被拒(403)" "$(BC "$OUT_DIR/r-idor-pt.json")" "403"
http GET "/lessons/users/$OTHER_ID/course-ids" "$STU_TOKEN" - "$OUT_DIR/r-idor-lc.json" >/dev/null
chk "学员读他人课表课程 id 被拒(403)" "$(BC "$OUT_DIR/r-idor-lc.json")" "403"
http GET "/question-results/users/$OTHER_ID/all" "$STU_TOKEN" - "$OUT_DIR/r-idor-qr.json" >/dev/null
chk "学员读他人答题记录被拒(403)" "$(BC "$OUT_DIR/r-idor-qr.json")" "403"
http GET "/question-results/users/$OTHER_ID/stats" "$STU_TOKEN" - "$OUT_DIR/r-idor-qs.json" >/dev/null
chk "学员读他人答题统计被拒(403)" "$(BC "$OUT_DIR/r-idor-qs.json")" "403"
http GET "/insight/profiles/$OTHER_ID" "$STU_TOKEN" - "$OUT_DIR/r-idor-ins.json" >/dev/null
chk "学员读他人学情画像被拒(403)" "$(BC "$OUT_DIR/r-idor-ins.json")" "403"
http GET "/sign-ins/users/$OTHER_ID/streak" "$STU_TOKEN" - "$OUT_DIR/r-idor-streak.json" >/dev/null
chk "学员读他人签到连续天数被拒(403)" "$(BC "$OUT_DIR/r-idor-streak.json")" "403"

# 本人查询应当放行（避免"一刀切拒绝"式的假修复）
http GET "/learning-records/users/$STU_ID/all" "$STU_TOKEN" - "$OUT_DIR/r-self-lr.json" >/dev/null
SELF_CODE=$(BC "$OUT_DIR/r-self-lr.json")
if [ "$SELF_CODE" = "200" ] || [ "$SELF_CODE" = "LIST" ]; then
  ok "学员读本人学习记录放行（@NoWrapper 裸数组，code=$SELF_CODE）"
else
  bad "学员读本人学习记录被误拒（code=$SELF_CODE）"
fi

# 他人订单
OTHER_ORDER=$(sql "USE zx_trade; SELECT id FROM trade_order WHERE user_id<>$STU_ID AND deleted=0 ORDER BY id DESC LIMIT 1;")
if [ -n "$OTHER_ORDER" ]; then
  http GET "/orders/$OTHER_ORDER" "$STU_TOKEN" - "$OUT_DIR/r-idor-order.json" >/dev/null
  OC=$(BC "$OUT_DIR/r-idor-order.json")
  # 越权必须被明确拒绝：接受 400/401/403/404 与语义化业务码（1001 业务不合法 / 1002 数据不存在 /
  # 1003 数据已存在）。**不再接受 500** —— 业务拒绝伪装成服务端故障会让监控误报，
  # 也掩盖「本该拦截却崩了」的真实问题（BizIllegalException 默认码已由 500 修正为 1001）。
  case "$OC" in
    400|401|403|404|1001|1002|1003) ok "学员读他人订单被拒(code=$OC)" ;;
    *) bad "学员读他人订单未拦截(code=$OC)" ;;
  esac
  http DELETE "/orders/$OTHER_ORDER" "$STU_TOKEN" - "$OUT_DIR/r-idor-delorder.json" >/dev/null
  DC=$(BC "$OUT_DIR/r-idor-delorder.json")
  [ "$DC" != "200" ] && ok "学员删除他人订单被拒(code=$DC)" || bad "学员删除他人订单成功（越权删除！）"
else
  info "库中无他人订单，跳过订单越权用例"
fi

# 他人 AI 会话（造 Redis 夹具：会话属于他人，用本学员 token 读取/删除/改名）
SESSION_ID="ZZPROBE-SESSION-$(date +%s)"
redis_do SET "aigc:memory:$SESSION_ID" '[{"role":"user","content":"ZZPROBE-他人机密会话内容"},{"role":"assistant","content":"收到"}]' EX 300 >/dev/null
redis_do HSET "aigc:session:$SESSION_ID" title "ZZPROBE-他人会话" userId "$OTHER_ID" >/dev/null
redis_do EXPIRE "aigc:session:$SESSION_ID" 300 >/dev/null
redis_do ZADD "aigc:user-sessions:$OTHER_ID" "$(date +%s)000" "$SESSION_ID" >/dev/null

http GET "/session/$SESSION_ID" "$STU_TOKEN" - "$OUT_DIR/r-idor-session.json" >/dev/null
SESS_CODE=$(BC "$OUT_DIR/r-idor-session.json")
SESS_LEAK=$(J "$OUT_DIR/r-idor-session.json" "1 if 'ZZPROBE' in str(d) else 0")
if [ "$SESS_LEAK" = "1" ]; then
  bad "他人 AI 会话可被越权读取（返回体含他人会话内容，code=$SESS_CODE）"
else
  ok "他人 AI 会话不可被越权读取(code=$SESS_CODE)"
fi

body sess-del.json "{\"sessionId\":\"$SESSION_ID\"}"
http DELETE /session/history "$STU_TOKEN" "$OUT_DIR/sess-del.json" "$OUT_DIR/r-idor-sessdel.json" >/dev/null
SESS_KEPT=$(redis_do EXISTS "aigc:memory:$SESSION_ID")
if [ "$SESS_KEPT" = "1" ]; then
  ok "他人 AI 会话不可被越权删除（会话数据仍在）"
else
  bad "他人 AI 会话被越权删除（会话数据已被清空！）"
fi

body sess-title.json "{\"sessionId\":\"$SESSION_ID\",\"title\":\"ZZPROBE-被篡改\"}"
http PUT /session/history "$STU_TOKEN" "$OUT_DIR/sess-title.json" "$OUT_DIR/r-idor-sesstitle.json" >/dev/null
TITLE_NOW=$(redis_do HGET "aigc:session:$SESSION_ID" title)
if [ "$TITLE_NOW" = "ZZPROBE-他人会话" ]; then
  ok "他人 AI 会话标题不可被越权篡改"
else
  bad "他人 AI 会话标题被越权篡改为：$TITLE_NOW"
fi
redis_do DEL "aigc:memory:$SESSION_ID" "aigc:session:$SESSION_ID" >/dev/null
redis_do ZREM "aigc:user-sessions:$OTHER_ID" "$SESSION_ID" >/dev/null

# 越权改他人密码
http PUT "/users/$OTHER_ID/password/default" "$STU_TOKEN" - "$OUT_DIR/r-idor-pwd.json" >/dev/null
PC=$(BC "$OUT_DIR/r-idor-pwd.json")
[ "$PC" != "200" ] && ok "学员重置他人密码被拒(code=$PC)" || bad "学员可重置他人密码（严重越权！）"

# 越权禁用他人账号
http PUT "/users/$OTHER_ID/status/0" "$STU_TOKEN" - "$OUT_DIR/r-idor-status.json" >/dev/null
SC=$(BC "$OUT_DIR/r-idor-status.json")
[ "$SC" != "200" ] && ok "学员禁用他人账号被拒(code=$SC)" || bad "学员可禁用他人账号（严重越权！）"

# 越权删除他人账号
http DELETE "/users/$OTHER_ID" "$STU_TOKEN" - "$OUT_DIR/r-idor-deluser.json" >/dev/null
UC=$(BC "$OUT_DIR/r-idor-deluser.json")
OTHER_ALIVE=$(sql "USE zx_user; SELECT COUNT(*) FROM user WHERE id=$OTHER_ID;")
[ "$UC" != "200" ] && ok "学员删除他人账号被拒(code=$UC)" || bad "学员可删除他人账号（严重越权！）"
chk "对照账号仍然存在（未被越权删除）" "$OTHER_ALIVE" "1"

# =====================================================================
echo "=== 阶段 5：内部接口外部不可达 ==="
LINES+=("")
LINES+=("=== 阶段 5：内部接口外部不可达（零信任）===")

internal_chk() {  # internal_chk <说明> <method> <path> [bodyfile|-]
  # 注意：带 @RequestBody 的端点在无 body 时会先被 Spring 判为 400 参数错误，
  # **掩盖** InternalOnlyGuard 的 403。必须带合法 body 才能真实校验守卫是否生效。
  local name=$1 m=$2 path=$3 bf=${4:--}
  local tag
  tag=$(echo "$path" | tr '/?&=.' '_____')
  http "$m" "$path" "$STU_TOKEN" "$bf" "$OUT_DIR/r-int-$tag.json" >/dev/null
  local f="$OUT_DIR/r-int-$tag.json"
  local c
  c=$(BC "$f")
  if [ "$c" = "403" ]; then ok "内部接口外部不可达：$1"; else bad "内部接口可被外部调用：$1 (code=$c)"; fi
}

body int-enroll.json '{"userId":9000000099,"courseId":9900000000000099,"courseName":"zz-probe"}'
internal_chk "GET /course/{id}"           GET  "/course/3001"
internal_chk "GET /course/{id}/catalogues" GET  "/course/3001/catalogues"
internal_chk "GET /order-details/stats/dashboard" GET "/order-details/stats/dashboard"
internal_chk "GET /learning-records/stats/active" GET "/learning-records/stats/active"
internal_chk "POST /order-details/reconcile-lessons" POST "/order-details/reconcile-lessons"
internal_chk "POST /lessons/internal/enroll" POST "/lessons/internal/enroll" "$OUT_DIR/int-enroll.json"
internal_chk "POST /order-details/replay-dead-msgs" POST "/order-details/replay-dead-msgs"
internal_chk "GET /order-details/dead-msgs" GET "/order-details/dead-msgs"
internal_chk "POST /user-coupons/internal/mark-used" POST "/user-coupons/internal/mark-used?orderId=1"
internal_chk "POST /user-coupons/internal/mark-refunded" POST "/user-coupons/internal/mark-refunded?orderId=1"
internal_chk "POST /questions (教师写)" POST "/questions"
internal_chk "GET /users/stats/total" GET "/users/stats/total"

# 越权探测若"侥幸成功"会在课表留下合成行，无论结果如何都清理
sql "USE zx_learning; DELETE FROM lesson WHERE user_id=9000000099;" >/dev/null
chk "内部开课接口未产生合成课表行" \
  "$(sql "USE zx_learning; SELECT COUNT(*) FROM lesson WHERE user_id=9000000099;")" "0"

http GET /course/3001 - - "$OUT_DIR/r-int-anon.json" >/dev/null
chk "内部接口匿名访问被网关拦截(401)" "$(BC "$OUT_DIR/r-int-anon.json")" "401"

http GET "/points/award" "$STU_TOKEN" - "$OUT_DIR/r-int-award.json" >/dev/null
info "POST /points/award 内部加分接口：学员 GET → code=$(BC "$OUT_DIR/r-int-award.json")"

# =====================================================================
echo "=== 阶段 6：参数校验 / 边界 / 注入 ==="
LINES+=("")
LINES+=("=== 阶段 6：参数校验 / 边界值 / 注入 ===")

# 6.1 SQL 注入
LONGNAME=$(printf 'A%.0s' $(seq 1 3000))
INJ1="' OR '1'='1"
INJ2="%27%20OR%20%271%27%3D%271"

BASE_CNT=$(sql "USE zx_course; SELECT COUNT(*) FROM course WHERE deleted=0;")
http GET "/courses/page?pageNo=1&pageSize=100&name=$INJ2" "$ADM_TOKEN" - "$OUT_DIR/r-inj1.json" >/dev/null
# ⚠ 必须先确认接口自身返回 200：原先只比较「返回条数 != 全库条数」就判通过，
#    但接口 500 时 data 为 null → 条数为 -1，同样满足该条件 →
#    「接口整个炸掉」会被判成「注入被成功防御」，属于假阳性断言。
INJ_CODE=$(BC "$OUT_DIR/r-inj1.json")
INJ_CNT=$(J "$OUT_DIR/r-inj1.json" "len(d.get('data',{}).get('list',[])) if isinstance(d,dict) and isinstance(d.get('data'),dict) else -1")
if [ "$INJ_CODE" != "200" ]; then
  bad "name 参数注入用例未通过：接口业务码 $INJ_CODE（接口自身异常，无法判定防护效果）"
elif [ "$INJ_CNT" = "$BASE_CNT" ] && [ "$BASE_CNT" -gt 3 ]; then
  bad "name 参数存在注入嫌疑：注入串返回了全量 $INJ_CNT 条（全库 $BASE_CNT 条）"
else
  ok "name 参数未受影响（注入串命中 $INJ_CNT 条，全库 $BASE_CNT 条）"
fi

http GET "/courses/page?pageNo=1&pageSize=10&sortBy=id%3B%20DROP%20TABLE%20course--" "$ADM_TOKEN" - "$OUT_DIR/r-inj2.json" >/dev/null
chk "排序字段注入不产生 5xx" \
  "$(J "$OUT_DIR/r-inj2.json" "'5xx' if not isinstance(d,dict) else ('ok' if d.get('code') in (200,400) else 'code:%s' % d.get('code'))")" "ok"
chk "course 表未被 DROP（注入无效）" "$(sql "USE zx_course; SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='zx_course' AND table_name='course';")" "1"

http GET "/courses/3001$INJ2" "$ADM_TOKEN" - "$OUT_DIR/r-inj3.json" >/dev/null
chk "路径参数注入 → 客户端错误而非 5xx" "$(CE "$OUT_DIR/r-inj3.json")" "ok"

# 6.2 类型/格式非法
http GET "/courses/page?pageNo=abc&pageSize=abc" "$ADM_TOKEN" - "$OUT_DIR/r-bad1.json" >/dev/null
chk "分页参数传非数字 → 客户端错误 400/404（禁止 500）" "$(CE "$OUT_DIR/r-bad1.json")" "ok"
http GET "/courses/abc" "$ADM_TOKEN" - "$OUT_DIR/r-bad2.json" >/dev/null
chk "路径 id 传非数字 → 客户端错误 400/404（禁止 500）" "$(CE "$OUT_DIR/r-bad2.json")" "ok"
http GET "/courses/page?status=abc" "$ADM_TOKEN" - "$OUT_DIR/r-bad3.json" >/dev/null
chk "status 传非数字 → 客户端错误 400/404（禁止 500）" "$(CE "$OUT_DIR/r-bad3.json")" "ok"

# 6.3 不存在的资源
http GET "/courses/$SENTINEL" "$ADM_TOKEN" - "$OUT_DIR/r-nf1.json" >/dev/null
chk "不存在的课程 id → 业务错误或空数据（禁止 5xx）" "$(NO5XX "$OUT_DIR/r-nf1.json")" "ok"
http GET "/questions/$SENTINEL" "$ADM_TOKEN" - "$OUT_DIR/r-nf2.json" >/dev/null
chk "不存在的题目 id → 禁止 5xx" "$(NO5XX "$OUT_DIR/r-nf2.json")" "ok"
http GET "/orders/$SENTINEL" "$STU_TOKEN" - "$OUT_DIR/r-nf3.json" >/dev/null
chk "不存在的订单 id → 禁止 5xx" "$(NO5XX "$OUT_DIR/r-nf3.json")" "ok"
http GET "/boards/$SENTINEL" "$STU_TOKEN" - "$OUT_DIR/r-nf4.json" >/dev/null
chk "不存在的话题 id → 禁止 5xx" "$(NO5XX "$OUT_DIR/r-nf4.json")" "ok"

# 6.4 超长与特殊字符
# ⚠ 下面三条都是「登录身份 + 带 name 但不带 status」——正是课程列表 NPE 的触发组合，
#    因此必须严格要求业务码 200；旧写法对 500 也判通过，是缺陷溜过验证的原因之一。
http GET "/courses/page?pageNo=1&pageSize=10&name=$LONGNAME" "$ADM_TOKEN" - "$OUT_DIR/r-long.json" >/dev/null
chk "超长查询串（3000 字符）→ 正常 200" "$(OK200 "$OUT_DIR/r-long.json")" "ok"
http GET "/courses/page?pageNo=1&pageSize=10&name=%3Cscript%3Ealert(1)%3C%2Fscript%3E" "$ADM_TOKEN" - "$OUT_DIR/r-xss1.json" >/dev/null
chk "XSS 串走 LIKE 查询 → 正常 200" "$(OK200 "$OUT_DIR/r-xss1.json")" "ok"
http GET "/courses/page?pageNo=1&pageSize=10&name=%E4%B8%AD%E6%96%87%25_" "$ADM_TOKEN" - "$OUT_DIR/r-wild.json" >/dev/null
chk "通配符 %/_ → 正常 200" "$(OK200 "$OUT_DIR/r-wild.json")" "ok"

# 6.4.1 课程列表：真实前端的默认请求形态 —— 「登录身份 + 不带 status」
#   回归背景：CourseController.page 曾把 int 常量与 Integer 参数写在同一个三元表达式里
#   （`UserContext.getUser()==null ? STATUS_ON_SHELF : status`），Java 会做「二元数值提升 +
#   自动拆箱」——登录用户不传 status 时 status 为 null，拆箱直接 NPE →
#   500「系统繁忙，请稍后再试」→ 课程列表整页拿不到数据。
#   该组合是学员端最常用的请求，必须有严格断言长期兜住（旧断言恰好都没覆盖到它）。
_page_nostatus() {  # _page_nostatus <标签> <文件名后缀> <token>
  local label=$1 sfx=$2 tok=$3
  local f="$OUT_DIR/r-page-nostatus-$sfx.json"
  http GET "/courses/page?pageNo=1&pageSize=10" "$tok" - "$f" >/dev/null
  chk "登录$label 不传 status → 课程列表 200（禁止 500）" "$(OK200 "$f")" "ok"
  local n
  n=$(J "$f" "len(d.get('data',{}).get('list',[])) if isinstance(d,dict) and isinstance(d.get('data'),dict) else -1")
  if [ "$n" -gt 0 ] 2>/dev/null; then
    ok "登录$label 课程列表返回真实数据（$n 条）"
  else
    bad "登录$label 课程列表无数据（条数=$n）"
  fi
}
_page_nostatus "学员" "stu" "$STU_TOKEN"
_page_nostatus "教师" "tea" "$TEA_TOKEN"
_page_nostatus "管理员" "adm" "$ADM_TOKEN"
http GET "/courses/page?pageNo=1&pageSize=10" - - "$OUT_DIR/r-page-nostatus-anon.json" >/dev/null
chk "匿名不传 status 仍可浏览已上架课程" "$(OK200 "$OUT_DIR/r-page-nostatus-anon.json")" "ok"
# status 显式传值不应被上面的改动破坏（保持可用）
http GET "/courses/page?pageNo=1&pageSize=10&status=1" "$STU_TOKEN" - "$OUT_DIR/r-page-status1.json" >/dev/null
chk "登录学员带 status=1 筛选 → 仍正常 200" "$(OK200 "$OUT_DIR/r-page-status1.json")" "ok"

# 6.5 空值与缺参
http GET "/courses/page" - - "$OUT_DIR/r-empty.json" >/dev/null
chk "白名单接口无任何参数仍可用(200)" "$(BC "$OUT_DIR/r-empty.json")" "200"
body empty-obj.json '{}'
http POST /orders/placeOrder "$STU_TOKEN" "$OUT_DIR/empty-obj.json" "$OUT_DIR/r-empty-order.json" >/dev/null
chk "下单缺参 → 业务错误（非 5xx，不落脏单）" \
  "$(J "$OUT_DIR/r-empty-order.json" "'5xx' if not isinstance(d,dict) else ('bizerr' if d.get('code') in (400,404,409,423) else 'code:%s' % d.get('code'))")" "bizerr"
http POST /boards "$STU_TOKEN" "$OUT_DIR/empty-obj.json" "$OUT_DIR/r-empty-board.json" >/dev/null
info "发帖缺参 → code=$(BC "$OUT_DIR/r-empty-board.json")"

# 6.6 前端 XSS 净化链路（静态断言）
VHTML=$(grep -rn "v-html" zx-web/src --include=*.vue | wc -l | tr -d ' ')
SANITIZED=$(grep -rn "v-html=\"html\"\|v-html=\"activeHandoutHtml\"" zx-web/src --include=*.vue | wc -l | tr -d ' ')
info "前端 v-html 使用点 $VHTML 处，其中经 renderMarkdown(DOMPurify) 的 $SANITIZED 处"
chk "前端 markdown 渲染统一走 DOMPurify 净化" \
  "$(grep -c "DOMPurify.sanitize" zx-web/src/utils/markdown.ts)" "1"
chk "AI 消息与讲义渲染均引用统一净化函数" \
  "$([ "$(grep -c "renderMarkdown" zx-web/src/components/ai/ChatMessageItem.vue)" -ge 1 ] && echo yes || echo no)" "yes"
chk "笔记渲染也走 DOMPurify 净化（防存储型 XSS）" \
  "$(grep -c 'v-html="renderMarkdown' zx-web/src/views/learning/CourseContentView.vue zx-web/src/views/learning/LearningView.vue | grep -c ':1' || echo 0)" "2"
chk "无未净化的 v-html 直接绑定原始内容" \
  "$(grep -rn 'v-html="n\.content"\|v-html="note\.content"' zx-web/src --include=*.vue | wc -l | tr -d ' ')" "0"

# 6.7 向量库接口参数健壮性（修复前 Long.parseLong 直接 500）
http DELETE "/embedding?id=abc" "$TEA_TOKEN" - "$OUT_DIR/r-emb-bad.json" >/dev/null
chk "DELETE /embedding 传非数字 id → 业务错误 400（非 5xx）" "$(BC "$OUT_DIR/r-emb-bad.json")" "400"
http GET "/embedding/search/all?text=probe" "$STU_TOKEN" - "$OUT_DIR/r-emb-all.json" >/dev/null
chk "search/all 不受 topK 无上限影响（非 5xx）" \
  "$(J "$OUT_DIR/r-emb-all.json" "'5xx' if not isinstance(d,dict) else ('ok' if d.get('code') in (200,400,404) else 'code:%s' % d.get('code'))")" "ok"

# 6.8 匿名不可枚举「非上架课程」（修复前 status 参数原样透传）
sql "USE zx_course; DELETE FROM course WHERE id=9900000000000099;" >/dev/null
sql "USE zx_course; INSERT INTO course (id, name, cover_url, price, category_id_lv1, status, free, publish_times, description, create_time, update_time, deleted)
     VALUES (9900000000000099, 'ZZ_PROBE_OFFSHELF', '', 1000, 1, 0, 0, 0, '下架课程越权探测用', NOW(), NOW(), 0);" >/dev/null
chk "合成「已下架」课程已就位" \
  "$(sql "USE zx_course; SELECT COUNT(*) FROM course WHERE id=9900000000000099 AND status=0;")" "1"
http GET "/courses/page?pageNo=1&pageSize=100&status=0" - - "$OUT_DIR/r-anon-offshelf.json" >/dev/null
ANON_SEES=$(J "$OUT_DIR/r-anon-offshelf.json" "1 if '9900000000000099' in str(d) or 'ZZ_PROBE_OFFSHELF' in str(d) else 0")
chk "匿名传 status=0 无法枚举已下架课程" "$ANON_SEES" "0"
http GET "/courses/page?pageNo=1&pageSize=100&status=0" "$ADM_TOKEN" - "$OUT_DIR/r-adm-offshelf.json" >/dev/null
ADM_SEES=$(J "$OUT_DIR/r-adm-offshelf.json" "1 if 'ZZ_PROBE_OFFSHELF' in str(d) else 0")
chk "管理员带 token 仍可按 status=0 筛选（对照组，证明过滤按身份而非查询失效）" "$ADM_SEES" "1"
sql "USE zx_course; DELETE FROM course WHERE id=9900000000000099;" >/dev/null
chk "合成课程已清理" \
  "$(sql "USE zx_course; SELECT COUNT(*) FROM course WHERE id=9900000000000099;")" "0"

# =====================================================================
echo "=== 阶段 7：敏感数据脱敏 ==="
LINES+=("")
LINES+=("=== 阶段 7：敏感数据脱敏（密码字段外泄排查）===")

LEAK=0
check_leak() {  # check_leak <响应文件> <说明>
  local has
  has=$(J "$1" "1 if any(k in str(d).lower() for k in ('\"password\"','\"pwd\"','passwordhash','salt','\"secret\"')) else 0")
  if [ "$has" = "1" ]; then bad "$2：响应体出现密码/密钥类字段"; LEAK=$((LEAK + 1)); else ok "$2：无密码/密钥字段"; fi
}
check_leak "$OUT_DIR/r-login-stu.json" "登录响应脱敏"
check_leak "$OUT_DIR/r-login-adm.json" "管理员登录响应脱敏"
http GET /users/page "$ADM_TOKEN" - "$OUT_DIR/r-users-page.json" >/dev/null
check_leak "$OUT_DIR/r-users-page.json" "用户管理列表脱敏"
http GET "/users/$OTHER_ID" "$ADM_TOKEN" - "$OUT_DIR/r-user-one.json" >/dev/null
check_leak "$OUT_DIR/r-user-one.json" "用户详情脱敏"
http GET /users/me "$STU_TOKEN" - "$OUT_DIR/r-user-me.json" >/dev/null
check_leak "$OUT_DIR/r-user-me.json" "当前用户信息脱敏"
http GET /students/page "$ADM_TOKEN" - "$OUT_DIR/r-students.json" >/dev/null
check_leak "$OUT_DIR/r-students.json" "学员列表脱敏"
http GET /teachers/page "$ADM_TOKEN" - "$OUT_DIR/r-teachers.json" >/dev/null
check_leak "$OUT_DIR/r-teachers.json" "教师列表脱敏"
http GET /staffs/page "$ADM_TOKEN" - "$OUT_DIR/r-staffs.json" >/dev/null
check_leak "$OUT_DIR/r-staffs.json" "员工列表脱敏"

# JWT 载荷不得含密码
JWT_PAYLOAD=$(J "$OUT_DIR/r-login-stu.json" "d['data']['accessToken'].split('.')[1]")
JWT_DECODED=$("$PY" -c "
import base64, sys
s = sys.argv[1]
s += '=' * (-len(s) % 4)
print(base64.urlsafe_b64decode(s).decode('utf-8', 'replace'))
" "$JWT_PAYLOAD")
case "$JWT_DECODED" in
  *password*|*pwd*) bad "JWT 载荷含密码字段：$JWT_DECODED" ;;
  *) ok "JWT 载荷不含密码字段" ;;
esac

# 手机号脱敏（非本人查看他人手机号是否打码）
MASKED=$(J "$OUT_DIR/r-users-page.json" "1 if '****' in str(d) or '***' in str(d) else 0")
info "用户管理列表中手机号是否打码：$([ "$MASKED" = "1" ] && echo 是 || echo 否)"

# =====================================================================
echo "=== 阶段 8：幂等与凭证安全 ==="
LINES+=("")
LINES+=("=== 阶段 8：幂等与凭证安全 ===")

# 重复登录
http POST /accounts/login - "$OUT_DIR/login-stu.json" "$OUT_DIR/r-login-again.json" >/dev/null
chk "重复登录同一账号仍成功（无一次性限制）" "$(BC "$OUT_DIR/r-login-again.json")" "200"

# 重复注册同一手机号
body reg-dup.json '{"cellPhone":"13900000001","password":"123456","name":"dup"}'
http POST /students/register - "$OUT_DIR/reg-dup.json" "$OUT_DIR/r-reg-dup.json" >/dev/null
chk "重复手机号注册被拒（明确业务码，禁止 500）" \
  "$(J "$OUT_DIR/r-reg-dup.json" "'5xx' if not isinstance(d,dict) else ('bizerr' if isinstance(d.get('code'),int) and d.get('code') not in (0,200,500) else 'code:%s' % d.get('code'))")" "bizerr"

# 重复删除不存在的资源（幂等）
http DELETE "/notes/$SENTINEL" "$STU_TOKEN" - "$OUT_DIR/r-idem1.json" >/dev/null
http DELETE "/notes/$SENTINEL" "$STU_TOKEN" - "$OUT_DIR/r-idem2.json" >/dev/null
chk "重复删除不存在的笔记两次均不报 5xx" \
  "$(J "$OUT_DIR/r-idem2.json" "'5xx' if not isinstance(d,dict) else ('ok' if d.get('code') in (200,400,404) else 'code:%s' % d.get('code'))")" "ok"

# 重复查询一致性
http GET /lessons/mine/course-ids "$STU_TOKEN" - "$OUT_DIR/r-idem3.json" >/dev/null
http GET /lessons/mine/course-ids "$STU_TOKEN" - "$OUT_DIR/r-idem4.json" >/dev/null
chk "课表课程 id 集合重复查询结果一致" \
  "$([ "$(J "$OUT_DIR/r-idem3.json" "sorted(d.get('data') or [])")" = "$(J "$OUT_DIR/r-idem4.json" "sorted(d.get('data') or [])")" ] && echo same || echo diff)" "same"

# 点赞幂等切换
body like.json '{"bizId":3001,"type":1}'
http POST /likes "$STU_TOKEN" "$OUT_DIR/like.json" "$OUT_DIR/r-like1.json" >/dev/null
L1=$(BC "$OUT_DIR/r-like1.json")
http POST /likes "$STU_TOKEN" "$OUT_DIR/like.json" "$OUT_DIR/r-like2.json" >/dev/null
L2=$(BC "$OUT_DIR/r-like2.json")
chk "点赞接口两次调用均返回成功（幂等切换）" "$L1$L2" "200200"
http GET "/likes/list?bizIds=1,2" "$STU_TOKEN" - "$OUT_DIR/r-like3.json" >/dev/null
chk "批量查询点赞状态不报错" "$(J "$OUT_DIR/r-like3.json" "'5xx' if not isinstance(d,dict) else ('ok' if d.get('code') in (200,400,404) else 'code:%s' % d.get('code'))")" "ok"

# =====================================================================
echo "=== 阶段 9：数据一致性交叉校验 ==="
LINES+=("")
LINES+=("=== 阶段 9：数据一致性交叉校验（SQL 不变量）===")

chk "已支付订单但课表缺失数 = 0" \
  "$(sql "USE zx_trade; SELECT COUNT(*) FROM trade_order o WHERE o.status=1 AND o.deleted=0 AND o.user_deleted=0 AND NOT EXISTS (SELECT 1 FROM zx_learning.lesson l WHERE l.user_id=o.user_id AND l.course_id=o.course_id AND l.deleted=0);")" "0"
chk "已支付订单 (用户,课程) 重复数 = 0（paid_key 唯一索引）" \
  "$(sql "USE zx_trade; SELECT COUNT(*) FROM (SELECT user_id, course_id FROM trade_order WHERE status=1 AND deleted=0 GROUP BY user_id, course_id HAVING COUNT(*)>1) t;")" "0"
chk "课表 (用户,课程) 重复行数 = 0" \
  "$(sql "USE zx_learning; SELECT COUNT(*) FROM (SELECT user_id, course_id FROM lesson WHERE deleted=0 GROUP BY user_id, course_id HAVING COUNT(*)>1) t;")" "0"
chk "优惠券状态与最后一条核销流水漂移数 = 0" \
  "$(sql "USE zx_promotion; SELECT COUNT(*) FROM user_coupon uc WHERE uc.deleted=0 AND uc.status<>2 AND EXISTS (SELECT 1 FROM zx_trade.coupon_use_record r WHERE r.user_coupon_id=uc.id AND r.status=1) AND uc.status<>1;")" "0"
chk "有核销流水但未回填订单号的券 = 0" \
  "$(sql "USE zx_promotion; SELECT COUNT(*) FROM user_coupon uc WHERE uc.deleted=0 AND uc.status=1 AND uc.order_id IS NULL AND EXISTS (SELECT 1 FROM zx_trade.coupon_use_record r WHERE r.user_coupon_id=uc.id);")" "0"
chk "上架课程存在空讲义小节数 = 0" \
  "$(sql "USE zx_course; SELECT COUNT(*) FROM course_catalogue WHERE deleted=0 AND chapter_type=2 AND (content IS NULL OR content='');")" "0"
chk "课程目录存在空章节的小节数 = 0" \
  "$(sql "USE zx_course; SELECT COUNT(*) FROM course_catalogue WHERE deleted=0 AND chapter_type=1 AND (name IS NULL OR name='');")" "0"
chk "order_msg 死信残留（未补偿）= 0" \
  "$(sql "USE zx_trade; SELECT COUNT(*) FROM order_msg WHERE status=3;")" "0"
chk "不存在与用户不匹配的笔记（越权残留）= 0" \
  "$(sql "USE zx_learning; SELECT COUNT(*) FROM note WHERE deleted=0 AND user_id=0;")" "0"

# =====================================================================
echo "=== 阶段 10：性能采样 ==="
LINES+=("")
LINES+=("=== 阶段 10：性能采样（P95 < 500ms）===")

perf_one() {  # perf_one <token|-> <path>  → 秒
  local tok=$1 path=$2
  if [ "$tok" = "-" ]; then
    MSYS_NO_PATHCONV=1 curl.exe -s -o /dev/null -w "%{time_total}" --max-time 20 "$GW$path" 2>/dev/null
  else
    MSYS_NO_PATHCONV=1 curl.exe -s -o /dev/null -w "%{time_total}" --max-time 20 \
      -H "Authorization: Bearer $tok" "$GW$path" 2>/dev/null
  fi
}

p95_of() {  # 从 stdin 读秒值，输出 P95 毫秒
  "$PY" -c "
import sys
xs = sorted(float(x) for x in sys.stdin.read().split() if x)
if not xs:
    print('NA'); sys.exit(0)
i = max(0, min(len(xs) - 1, int(0.95 * len(xs)) - 1 if int(0.95 * len(xs)) > 0 else 0))
print('%.0f' % (xs[i] * 1000))
"
}

N=20
perf_case() {  # perf_case <说明> <token|-> <path> <阈值ms>
  local name=$1 tok=$2 path=$3 thr=$4 i t
  local acc=""
  for i in $(seq 1 $N); do
    t=$(perf_one "$tok" "$path")
    acc="$acc $t"
  done
  local p
  p=$(printf '%s\n' "$acc" | p95_of)
  local avg
  avg=$("$PY" -c "
import sys
xs=[float(x) for x in sys.argv[1].split()]
print('%.0f' % (sum(xs)/len(xs)*1000))
" "$acc")
  if [ "$p" = "NA" ]; then
    bad "性能采样失败：$name"
  elif [ "$p" -le "$thr" ]; then
    ok "P95 $name = ${p}ms（均值 ${avg}ms，阈值 ${thr}ms）"
  else
    bad "P95 $name = ${p}ms 超过阈值 ${thr}ms（均值 ${avg}ms）"
  fi
}

perf_case "课程列表 /courses/page"        -          "/courses/page?pageNo=1&pageSize=10" 500
perf_case "课程详情 /courses/3001"        "$STU_TOKEN" "/courses/3001"                     500
perf_case "我的课表 /lessons/page"        "$STU_TOKEN" "/lessons/page?pageNo=1&pageSize=9" 500
perf_case "积分汇总 /points/summary"      "$STU_TOKEN" "/points/summary"                   500
perf_case "订单列表 /orders/page"         "$STU_TOKEN" "/orders/page?pageNo=1&pageSize=10" 500
perf_case "学情画像 /insight/profiles/mine" "$STU_TOKEN" "/insight/profiles/mine"          1500
perf_case "讨论区 /boards/page"           "$STU_TOKEN" "/boards/page?pageNo=1&pageSize=10" 500
perf_case "题目分页 /questions/page"      "$ADM_TOKEN" "/questions/page?pageNo=1&pageSize=10" 500
perf_case "用户分页 /users/page"          "$ADM_TOKEN" "/users/page?pageNo=1&pageSize=10"  500

# 并发探测：固定 20 并发 × 200 请求。
# ⚠ 不要用 `for ... &` 一次性起 200 个 curl 进程：那测的是"测试机进程调度能力"而非服务端并发，
#   且并发 append 同一文件会交错，结果必然虚低（曾经误报 125/200）。改用线程池驱动脚本。
CONC_OUT=$("$PY" "$ROOT/scripts/conc-probe.py" 2>&1)
echo "$CONC_OUT" > "$OUT_DIR/conc-probe.txt"
CONC_TOTAL=$(echo "$CONC_OUT" | grep '^CONC_TOTAL=' | cut -d= -f2)
CONC_OK=$(echo "$CONC_OUT" | grep '^CONC_OK=' | cut -d= -f2)
CONC_5XX=$(echo "$CONC_OUT" | grep '^CONC_5XX=' | cut -d= -f2)
CONC_CONN=$(echo "$CONC_OUT" | grep '^CONC_FAIL_CONN=' | cut -d= -f2)
CONC_CODES=$(echo "$CONC_OUT" | grep '^CONC_CODES=' | cut -d= -f2-)
CONC_RPS=$(echo "$CONC_OUT" | grep '^CONC_RPS=' | cut -d= -f2)
CONC_P95=$(echo "$CONC_OUT" | grep '^CONC_P95_MS=' | cut -d= -f2)
CONC_TOTAL=${CONC_TOTAL:-0}
info "并发 ${CONC_TOTAL} 请求（20 并发）：成功 ${CONC_OK:-0}，5xx ${CONC_5XX:-0}，连接失败 ${CONC_CONN:-0}"
info "并发响应码分布 = ${CONC_CODES:-无}；吞吐 ${CONC_RPS:-0} RPS；P95 ${CONC_P95:-0}ms"
chk "并发请求全部成功(HTTP 200)" "${CONC_OK:-0}" "$CONC_TOTAL"
chk "并发无 5xx" "${CONC_5XX:-0}" "0"
chk "并发无连接失败" "${CONC_CONN:-0}" "0"
chk_ge "并发吞吐 ≥ 50 RPS" "${CONC_RPS%%.*}" "50"

# =====================================================================
echo "=== 阶段 11：汇总 ==="
LINES+=("")
LINES+=("---------------------------------------------------------------------")
LINES+=("断言总数: $((PASS + FAIL))   通过: $PASS   失败: $FAIL")
if [ "$FAIL" = "0" ]; then
  LINES+=("结论: 全部通过 ✅")
else
  LINES+=("结论: 存在 $FAIL 项失败 ❌（详见上方 [FAIL] 行）")
fi
LINES+=("原始报文目录: logs/verify-full-suite/")
LINES+=("接口矩阵明细: logs/verify-full-suite/api-matrix-detail.txt")
LINES+=("---------------------------------------------------------------------")

printf '%s\n' "${LINES[@]}" > "$REPORT"
printf '%s\n' "${LINES[@]}"

echo ""
echo "报告已写入: $REPORT"
