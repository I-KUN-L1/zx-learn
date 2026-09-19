#!/usr/bin/env bash
# =============================================================================
# 上线前全量回归（一条命令跑完）
#
# 背景：沙箱里「后台服务不跨工具调用存活」——服务一旦随命令结束就被回收。
# 因此必须把「启动 16 服务 + 全部验证套件」写在**同一条命令**内，
# 否则会出现"刚起好服务、下一条命令就 0/16 LISTENING"的假失败。
#
# 用法：
#   /usr/bin/bash scripts/run-final-verify.sh
#   SKIP_UP=1 /usr/bin/bash scripts/run-final-verify.sh   # 复用已在跑的服务
#
# 产出：logs/tmp/final2/SUMMARY.txt（每套件的 退出码 + 通过/失败行）
# =============================================================================
set -u
cd "$(dirname "$0")/.." || exit 1

OUT=logs/tmp/final2
mkdir -p "$OUT"

# ---- 阶段 0：启动全部 16 个服务（Redis + 8 核心 + 8 扩展） ----
if [ "${SKIP_UP:-0}" != "1" ]; then
  echo "[INFO] 启动 16 服务（Redis + 8 核心 + 8 扩展），就绪等待上限 12min ……"
  EXTRA_SPECS="zx-media:8085 zx-promotion:8088 zx-aigc:8089 zx-pay:8090 zx-search:8091 zx-remark:8092 zx-message:8093 zx-data:8094" \
    /usr/bin/bash scripts/dev-up-core.sh > "$OUT/up.log" 2>&1
  echo "[INFO] dev-up-core.sh 退出码=$?"
  tail -3 "$OUT/up.log" 2>/dev/null
fi

# ---- 端口自检：数清楚到底几个在 LISTENING（别只看 java.exe 数量） ----
# ⚠ sort/find/timeout 一律走绝对路径：C:\Windows\System32 下的同名 Windows 程序会遮蔽
# GNU coreutils，报中文参数错误并**静默返回空结果**（曾致 sort -u 恒为 0、前端套件误判）。
SORT_BIN="/usr/bin/sort"; [ -x "$SORT_BIN" ] || SORT_BIN=sort
TIMEOUT_BIN="/usr/bin/timeout"; [ -x "$TIMEOUT_BIN" ] || TIMEOUT_BIN=timeout
UP=$(netstat -ano 2>/dev/null | grep LISTENING \
     | grep -oE ":(8080|8081|8082|8083|8084|8085|8088|8089|8090|8091|8092|8093|8094|8095) " \
     | "$SORT_BIN" -u | wc -l | tr -d ' ')
echo "[INFO] 当前 LISTENING 服务端口数 = $UP / 14（8080-8095 范围内共 14 个端口；另有 zx-common/zx-api 为库不打端口）"

# ---- 阶段 1：依次跑全部验证套件 ----
SUITES="verify-full-suite
verify-frontend
verify-admin-student-fixes
verify-modules-e2e
verify-course-ui-and-catalogue
verify-lesson-consistency
verify-coupon-status-sync
verify-purchase-guard
verify-draft-and-covers
verify-go-live-audit
e2e-full-verify"

: > "$OUT/SUMMARY.txt"
for s in $SUITES; do
  echo "=============== $s 开始 ==============="
  REUSE=1 "$TIMEOUT_BIN" 900 /usr/bin/bash "scripts/$s.sh" > "$OUT/$s.log" 2>&1
  rc=$?
  # 抓取「通过/失败/断言」这类汇总行（中文与英文两种风格都覆盖）
  line=$(grep -aE "通过|失败|PASS=|FAIL=|断言总数|Tests run" "$OUT/$s.log" 2>/dev/null | tail -2 | tr '\n' ' ')
  printf '%-32s rc=%-4s %s\n' "$s" "$rc" "$line" >> "$OUT/SUMMARY.txt"
  echo "[$s] rc=$rc  $line"
done

echo
echo "================= SUMMARY ================="
cat "$OUT/SUMMARY.txt"
