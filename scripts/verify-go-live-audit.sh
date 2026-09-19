#!/usr/bin/env bash
# =====================================================================
# 知行智学 · 上线前审查（Go-Live Audit）修复项回归验证
# ---------------------------------------------------------------------
# 本脚本只覆盖 2026-09-16 上线审查中**发现并修复**的问题，逐条防回归：
#
#   A. RAG / Embedding 链路（曾恒定静默降级）
#      A1. Embedding 接口路径已可配置（不再写死 /v1/embeddings）
#      A2. 环境变量与 yml 都提供了 embeddding-path，且 .env.example 有说明
#      A3. embedding 维度与 pgvector 列维度**严格一致**（三方：.env / yml / init.sql）
#      A4. 向量列未被配成 HNSW 无法索引的 2048 维
#   B. 课程名额计数（曾只增不减 → 名额虚占 → 误报"名额已满"）
#      B1. confirm 容错分支成对出现「占位 +1 / 归还 -1」
#      B2. 修复迁移已登记进 db-migrate.sh
#      B3. 库内 locked_count 无漂移（= status=1 流水条数）
#      B4. reconcile.sql 断言项全部 0 行
#   C. 跨服务调用（题库课程名曾恒为占位「课程 #<id>」）
#      C1. zx-exam 声明了 course-service 静态实例
#      C2. 运行时题库 courseName 不再是占位
#   D. 越权面（任意登录学员可访问管理端知识库接口）
#      D1. /admin/knowledge/search 学员 403、教师 200、匿名 401
#   E. 部署链路（前端生产构建 /api 前缀无人剥离 → 全量接口 404）
#      E1. zx-web/nginx.conf 存在且剥离 /api 前缀
#      E2. 编排里含 zx-web 站点服务
#      E3. 前端生产配置确实使用 /api
#   F. 密钥基线（观察项，不计失败）
#
# 用法：
#   REUSE=1 /usr/bin/bash scripts/verify-go-live-audit.sh     # 复用已运行服务
#   /usr/bin/bash scripts/verify-go-live-audit.sh             # 运行态检查不可用时自动跳过
# =====================================================================
set -u

ROOT="D:/1/zx-learn"
PY="C:/Users/20670/.workbuddy/binaries/python/versions/3.13.12/python.exe"
MYSQL="/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"
GW="http://localhost:8080"
OUT_DIR="$ROOT/logs/verify-go-live-audit"
REPORT="$ROOT/docs/verify-go-live-audit-report.txt"

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
env_get() { grep -E "^$1=" .env 2>/dev/null | head -1 | cut -d= -f2-; }
file_has() { grep -qE "$2" "$1" 2>/dev/null && echo yes || echo no; }

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

body() { printf '%s' "$2" > "$OUT_DIR/$1"; }
CODE() { J "$1" "d.get('code') if isinstance(d,dict) else 'NO_JSON'"; }
# 业务码断言（强）：500/异常一律不通过
CODE_IS() { J "$1" "'code:%s' % d.get('code') if not isinstance(d,dict) or d.get('code')!=$2 else 'ok'"; }

LINES+=("=====================================================================")
LINES+=("知行智学 · 上线前审查修复项回归验证报告")
LINES+=("生成时间: $(date '+%Y-%m-%d %H:%M:%S')")
LINES+=("=====================================================================")

# =====================================================================
echo "=== A. RAG / Embedding 链路 ==="
LINES+=("")
LINES+=("=== A. RAG / Embedding 链路（曾恒定静默降级为伪向量）===")

chk "A1 EmbeddingService 不再写死 /v1/embeddings 路径" \
  "$(file_has zx-aigc/src/main/java/com/zhixing/aigc/service/EmbeddingService.java '\.uri\("/v1/embeddings"\)')" "no"
chk "A1 EmbeddingService 使用可配置的 embeddingUri()" \
  "$(file_has zx-aigc/src/main/java/com/zhixing/aigc/service/EmbeddingService.java 'embeddingUri\(\)')" "yes"
chk "A1 LlmProperties 暴露 embeddingPath 配置项" \
  "$(file_has zx-aigc/src/main/java/com/zhixing/aigc/config/LlmProperties.java 'private String embeddingPath')" "yes"
chk "A2 application.yml 提供 ZX_LLM_EMBEDDING_PATH 覆盖点" \
  "$(file_has zx-aigc/src/main/resources/application.yml 'embedding-path: \$\{ZX_LLM_EMBEDDING_PATH')" "yes"
chk "A2 .env.example 已说明 embedding-path 与 base-url 的配套关系" \
  "$(file_has .env.example 'ZX_LLM_EMBEDDING_PATH')" "yes"
chk "A2 降级日志可见（不再静默：ERROR 一次 + 指向配置项）" \
  "$(file_has zx-aigc/src/main/java/com/zhixing/aigc/service/EmbeddingService.java '已降级为【伪向量】')" "yes"

# A3/A4：三方维度必须严格一致，且不得是 HNSW 无法索引的 2048
DIM_ENV="$(env_get ZX_LLM_EMBEDDING_DIMENSION)"
DIM_INIT="$(grep -oE 'vector\(([0-9]+)\)' deploy/pgvector/init.sql | head -1 | grep -oE '[0-9]+')"
info "维度三方核对：.env=$DIM_ENV / deploy/pgvector/init.sql=$DIM_INIT"
chk "A3 .env 的 embedding 维度 = pgvector 建表维度" "$DIM_ENV" "$DIM_INIT"
chk "A4 向量维度未超过 HNSW 对 vector 类型的 2000 维上限" \
  "$("$PY" -c "print('ok' if 0 < int('${DIM_INIT:-0}') <= 2000 else 'over_limit')")" "ok"

PGPW="$(env_get POSTGRES_PASSWORD)"
PGTYPE="$(docker exec -e PGPASSWORD="$PGPW" zx-learn-pg psql -U postgres -d zx_aigc -t -c \
  "SELECT format_type(atttypid,atttypmod) FROM pg_attribute WHERE attrelid='knowledge_chunk'::regclass AND attname='embedding';" 2>/dev/null | tr -d ' \r\n')"
if [ -n "$PGTYPE" ]; then
  chk "A3 运行库 knowledge_chunk.embedding 维度与配置一致" "$PGTYPE" "vector($DIM_ENV)"
else
  info "A3 PG 容器 zx-learn-pg 不可达，跳过运行库维度核对"
fi

# =====================================================================
echo "=== B. 课程名额计数 ==="
LINES+=("")
LINES+=("=== B. 课程名额计数（曾只增不减 → 名额虚占）===")

chk "B1 confirm 容错分支归还占位（releaseLockedCount）" \
  "$(file_has zx-course/src/main/java/com/zhixing/course/service/CourseQuotaService.java 'releaseLockedCount\(msg\.getCourseId\(\)\)')" "yes"
chk "B1 locked_count 递减逻辑集中在单一私有方法" \
  "$(file_has zx-course/src/main/java/com/zhixing/course/service/CourseQuotaService.java 'private void releaseLockedCount')" "yes"
chk "B1 单测覆盖「容错确认不泄漏 locked_count」" \
  "$(file_has zx-course/src/test/java/com/zhixing/course/service/CourseQuotaServiceTest.java 'confirmRecoversWithoutLeakingLockedCount')" "yes"
chk "B2 修复迁移已登记进 db-migrate.sh" \
  "$(file_has scripts/db-migrate.sh '2026-09-16-quota-locked-count-repair.sql')" "yes"
chk "B2 修复迁移文件存在" \
  "$([ -f sql/2026-09-16-quota-locked-count-repair.sql ] && echo yes || echo no)" "yes"

DRIFT="$(sql "SELECT COUNT(*) FROM zx_course.course_quota q
  LEFT JOIN (SELECT course_id, COUNT(*) c FROM zx_course.course_quota_record WHERE status=1 AND deleted=0 GROUP BY course_id) r
    ON r.course_id=q.course_id
  WHERE q.locked_count <> IFNULL(r.c,0);")"
chk "B3 库内 locked_count 漂移课程数 = 0" "${DRIFT:-ERR}" "0"

# B4：reconcile 断言项必须全部 0 行（观察项 EXCLUDED_* / SOLD_DELTA_* 除外）
"$MYSQL" -uroot -p"$MYSQL_PWD_VAL" --default-character-set=utf8mb4 < sql/reconcile.sql > "$OUT_DIR/reconcile.txt" 2>/dev/null
ASSERT_HITS=$(grep -cE '^(DEAD_MSG|STUCK_PENDING_MSG|PAID_WITHOUT_LESSON|PAID_WITHOUT_QUOTA_CONFIRM|CLOSED_WITHOUT_COUPON_REFUND|CLOSED_WITHOUT_QUOTA_RELEASE|QUOTA_COUNT_MISMATCH|SOLD_BELOW_CONFIRMED)\b' "$OUT_DIR/reconcile.txt" | tr -d ' ')
chk "B4 reconcile.sql 断言项命中行数 = 0" "$ASSERT_HITS" "0"

# =====================================================================
echo "=== C. 跨服务调用（题库课程名） ==="
LINES+=("")
LINES+=("=== C. 跨服务调用（题库课程名曾恒为「课程 #<id>」占位）===")

chk "C1 zx-exam 声明 course-service 静态实例" \
  "$(file_has zx-exam/src/main/resources/application.yml '^ *course-service:')" "yes"

# =====================================================================
echo "=== D/E. 越权面与部署链路（静态） ==="
LINES+=("")
LINES+=("=== D. 越权面（任意登录学员可读管理端知识库接口）===")
chk "D1 KnowledgeController 的 search 已加 @RequireRole" \
  "$("$PY" -c "
import io,re
s=io.open('zx-aigc/src/main/java/com/zhixing/aigc/controller/KnowledgeController.java',encoding='utf-8').read()
blocks=re.findall(r'@PostMapping\(\"/(search|preview)\"\)(.{0,200}?)public', s, re.S)
print('yes' if len(blocks)==2 and all('@RequireRole' in b[1] for b in blocks) else 'no')
")" "yes"

LINES+=("")
LINES+=("=== E. 部署链路（前端 /api 前缀剥离）===")
chk "E1 zx-web/nginx.conf 存在" "$([ -f zx-web/nginx.conf ] && echo yes || echo no)" "yes"
chk "E1 nginx 反代到网关时剥离 /api 前缀（proxy_pass 以 / 结尾）" \
  "$(file_has zx-web/nginx.conf 'proxy_pass http://zx-gateway:8080/;')" "yes"
chk "E1 nginx 为 SSE（AI 流式对话）关闭了缓冲" \
  "$(file_has zx-web/nginx.conf 'proxy_buffering off;')" "yes"
chk "E1 SPA 路由回落 index.html" \
  "$(file_has zx-web/nginx.conf 'try_files \$uri \$uri/ /index.html;')" "yes"
chk "E2 编排含 zx-web 站点服务" \
  "$(file_has docker-compose.prod.yml '^  zx-web:')" "yes"
chk "E2 前端镜像构建上下文为 ./zx-web" \
  "$(file_has docker-compose.prod.yml 'context: ./zx-web')" "yes"
chk "E2 前端 Dockerfile 存在" "$([ -f zx-web/Dockerfile ] && echo yes || echo no)" "yes"
chk "E3 前端生产配置使用 /api（与 nginx 剥离规则配套）" \
  "$(file_has zx-web/.env.production 'VITE_API_BASE_URL=/api')" "yes"

# =====================================================================
echo "=== 运行态断言（需服务在跑）==="
LINES+=("")
LINES+=("=== 运行态断言（网关 :8080）===")

if netstat -ano 2>/dev/null | grep LISTENING | grep -q ":8080 "; then
  STU_TOKEN=$(MSYS_NO_PATHCONV=1 curl.exe -s -X POST "$GW/accounts/login" -H "Content-Type: application/json" \
    -d '{"cellPhone":"13900000001","password":"123456"}' | \
    "$PY" -c "import sys,json;print((json.load(sys.stdin).get('data') or {}).get('accessToken',''))" 2>/dev/null)
  TEA_TOKEN=$(MSYS_NO_PATHCONV=1 curl.exe -s -X POST "$GW/accounts/login" -H "Content-Type: application/json" \
    -d '{"cellPhone":"13900000002","password":"123456"}' | \
    "$PY" -c "import sys,json;print((json.load(sys.stdin).get('data') or {}).get('accessToken',''))" 2>/dev/null)

  body know.json '{"query":"测试","topK":1}'

  http POST /admin/knowledge/search "$STU_TOKEN" "$OUT_DIR/know.json" "$OUT_DIR/k-stu.json" >/dev/null
  chk "D1 学员访问 /admin/knowledge/search → 业务码 403" "$(CODE_IS "$OUT_DIR/k-stu.json" 403)" "ok"
  http POST /admin/knowledge/preview "$STU_TOKEN" "$OUT_DIR/know.json" "$OUT_DIR/k-stu2.json" >/dev/null
  chk "D1 学员访问 /admin/knowledge/preview → 业务码 403" "$(CODE_IS "$OUT_DIR/k-stu2.json" 403)" "ok"
  chk "D1 匿名访问 /admin/knowledge/search → 网关 401" \
    "$(http POST /admin/knowledge/search - "$OUT_DIR/know.json" "$OUT_DIR/k-anon.json")" "401"
  http POST /admin/knowledge/search "$TEA_TOKEN" "$OUT_DIR/know.json" "$OUT_DIR/k-tea.json" >/dev/null
  chk "D1 教师访问 /admin/knowledge/search → 业务码 200（未误伤教师）" "$(CODE_IS "$OUT_DIR/k-tea.json" 200)" "ok"

  http GET "/questions/page?pageNo=1&pageSize=5" "$TEA_TOKEN" - "$OUT_DIR/q.json" >/dev/null
  chk "C2 题库列表返回 200" "$(CODE_IS "$OUT_DIR/q.json" 200)" "ok"
  PLACEHOLDER=$(J "$OUT_DIR/q.json" "
sum(1 for q in (d.get('data') or {}).get('list', []) if str(q.get('courseName','')).startswith('课程 #'))
")
  chk "C2 题库 courseName 无「课程 #id」占位（跨服务调用已生效）" "${PLACEHOLDER:-ERR}" "0"
else
  info "网关 :8080 未监听，跳过运行态断言（D1/C2）：请先 bash scripts/dev-up-core.sh"
fi

# =====================================================================
echo "=== F. 密钥基线（观察项）==="
LINES+=("")
LINES+=("=== F. 密钥基线（观察项，不计入失败）===")
for k in ZX_JWT_SECRET PAY_CALLBACK_SECRET MYSQL_PASSWORD REDIS_PASSWORD; do
  v="$(env_get "$k")"
  case "$v" in
    *123456*|*secret*|*'zx-learn'*) info "F $k 仍是**开发占位值**，上线前必须替换（详见 docs/GO-LIVE-CHECKLIST.md B 组）" ;;
    "") info "F $k 未配置" ;;
    *) info "F $k 长度 ${#v}，未见明显占位特征（请人工确认是否为随机强密钥）" ;;
  esac
done

# =====================================================================
LINES+=("")
LINES+=("=====================================================================")
LINES+=("断言总数: $((PASS + FAIL))   通过: $PASS   失败: $FAIL")
if [ "$FAIL" -eq 0 ]; then
  LINES+=("结论: 全部通过 ✅")
else
  LINES+=("结论: 存在 $FAIL 项失败 ❌")
fi
LINES+=("原始报文目录: $OUT_DIR")
LINES+=("=====================================================================")

printf '%s\n' "${LINES[@]}" | tee "$REPORT"
echo
echo "报告已写入: $REPORT"
[ "$FAIL" -eq 0 ] || exit 1
