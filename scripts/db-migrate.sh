#!/usr/bin/env bash
# =====================================================================
# 知行智学 数据库初始化 / 迁移执行器
#
# 用法：
#   bash scripts/db-migrate.sh              # 结构 + 增量迁移（幂等，可重复执行）
#   bash scripts/db-migrate.sh --with-seed  # 额外写入演示种子数据（sql/test-data.sql）
#   MYSQL_HOST=db MYSQL_PORT=3306 bash scripts/db-migrate.sh   # 指定目标库
#
# 设计要点：
#   1. **幂等**：全部脚本均为 CREATE ... IF NOT EXISTS / INSERT IGNORE /
#      条件 UPDATE，重复执行不报错、不产生重复数据；
#   2. **顺序固定**：init.sql（基线）→ 按日期递增的增量迁移 → 可选种子数据。
#      顺序错乱（先跑增量再跑基线）会导致增量里的数据修复语句找不到表；
#   3. **字符集**：Windows 版 mysql.exe 默认按当前代码页（GBK）解析脚本文件，
#      UTF-8 汉字的尾字节会吞掉紧随其后的反引号 → 报 ERROR 1064 语法错误。
#      必须显式 --default-character-set=utf8mb4（此坑已实测踩过）；
#   4. **退出码**：管道里取的是最后一个命令（grep）的退出码，会静默吞掉 mysql 的报错。
#      必须用 PIPESTATUS[0] 拿 mysql 的真实退出码。
# =====================================================================
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# ---------- mysql 客户端定位 ----------
MYSQL_BIN="${MYSQL_BIN:-/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe}"
if [ ! -x "$MYSQL_BIN" ]; then
    MYSQL_BIN="$(command -v mysql || true)"
fi
if [ -z "$MYSQL_BIN" ]; then
    echo "[FAIL] 找不到 mysql 客户端：请安装 MySQL 客户端，或设置 MYSQL_BIN 指向 mysql 可执行文件" >&2
    exit 2
fi

# ---------- 连接参数（优先 .env，其次环境变量默认值） ----------
# shellcheck disable=SC1091
set -a; [ -f ./.env ] && . ./.env; set +a
DB_HOST="${MYSQL_HOST:-localhost}"
DB_PORT="${MYSQL_PORT:-3306}"
DB_USER="${MYSQL_USERNAME:-root}"
DB_PASS="${MYSQL_ROOT_PASSWORD:-${MYSQL_PASSWORD:-}}"
if [ -z "$DB_PASS" ]; then
    echo "[FAIL] 未取到 MySQL 口令：请在仓库根 .env 配置 MYSQL_ROOT_PASSWORD（或 MYSQL_PASSWORD）" >&2
    exit 2
fi

echo "目标：${DB_USER}@${DB_HOST}:${DB_PORT}"
echo "客户端：${MYSQL_BIN}"
echo

OK_CNT=0
FAIL_CNT=0

run_sql() {  # run_sql <sql 文件>
    local f="$1"
    if [ ! -f "$f" ]; then
        echo "  [SKIP] $f 不存在"
        return 0
    fi
    local out
    out=$(MSYS_NO_PATHCONV=1 "$MYSQL_BIN" \
        -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" \
        --default-character-set=utf8mb4 \
        < "$f" 2>&1)
    local rc=$?
    # shellcheck disable=SC2001
    out=$(printf '%s\n' "$out" | grep -v "Using a password" || true)
    if [ "$rc" -eq 0 ]; then
        echo "  [OK]   $f"
        [ -n "$out" ] && printf '%s\n' "$out" | sed 's/^/         | /'
        OK_CNT=$((OK_CNT + 1))
    else
        echo "  [FAIL] $f (exit=$rc)"
        printf '%s\n' "$out" | sed 's/^/         | /'
        FAIL_CNT=$((FAIL_CNT + 1))
    fi
    return "$rc"
}

echo "=== 1/3 基线结构（建库建表）==="
run_sql "sql/init.sql"
[ "$FAIL_CNT" -gt 0 ] && { echo; echo "[ABORT] 基线失败，后续增量不再执行（先修基线再重跑）"; exit 1; }

echo
echo "=== 2/3 增量迁移（按日期顺序，均幂等）==="
MIGRATIONS=(
    "sql/2026-09-11-exam-and-admin-module.sql"
    "sql/2026-09-12-duplicate-purchase-guard.sql"
    "sql/2026-09-13-profile-points-discussion.sql"
    "sql/2026-09-14-course-content-and-catalogue.sql"
    "sql/2026-09-14-order-delete-and-close-fix.sql"
    "sql/2026-09-15-lesson-reconcile-and-self-heal.sql"
    "sql/2026-09-15-coupon-status-sync.sql"
    "sql/2026-09-15-pay-order-persistence.sql"
    "sql/2026-09-16-draft-box-and-course-covers.sql"
    "sql/2026-09-16-quota-locked-count-repair.sql"
)
for m in "${MIGRATIONS[@]}"; do
    run_sql "$m"
done

if [ "${1:-}" = "--with-seed" ]; then
    echo
    echo "=== 3/3 演示种子数据（--with-seed）==="
    run_sql "sql/test-data.sql"
else
    echo
    echo "=== 3/3 演示种子数据：已跳过（加 --with-seed 启用）==="
fi

echo
echo "=== 汇总：成功 ${OK_CNT} / 失败 ${FAIL_CNT} ==="
if [ "$FAIL_CNT" -gt 0 ]; then
    exit 1
fi
echo "迁移完成。建议执行数据一致性对账："
echo "  mysql -uroot -p --default-character-set=utf8mb4 < sql/reconcile.sql"
echo "  判定：DEAD_MSG / STUCK_PENDING_MSG / PAID_WITHOUT_LESSON / PAID_WITHOUT_QUOTA_CONFIRM /"
echo "        CLOSED_WITHOUT_COUPON_REFUND / CLOSED_WITHOUT_QUOTA_RELEASE / QUOTA_COUNT_MISMATCH /"
echo "        SOLD_BELOW_CONFIRMED  这些断言类查询必须返回 0 行；"
echo "        EXCLUDED_* / SOLD_DELTA_OVERVIEW 为观察项，有结果属正常（详见脚本头部口径说明）。"
