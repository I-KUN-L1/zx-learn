#!/usr/bin/env bash
# ==========================================================
# 知行智学 ZhiXing Learn —— 后端一键启动（本地开发）
# ----------------------------------------------------------
# 用法：
#   bash scripts/dev-start-backend.sh                # 启动全部 16 个服务
#   bash scripts/dev-start-backend.sh zx-user zx-course zx-auth zx-gateway
#                                                    # 只启动指定服务（最小链路）
#
# 为什么需要这个脚本？（后端"无法启动"的根因）
# ----------------------------------------------------------
# 某些宿主/IDE 环境（例如 WorkBuddy 沙箱终端）会向子进程注入：
#     SERVER__PORT=<宿主自身占用的端口>
#     SERVER__HOST=127.0.0.1
# Spring Boot 的"松散绑定"会把 SERVER__PORT 解析为 server.port，
# 优先级高于 application.yml（OS 环境变量 > 配置文件），于是所有服务
# 都去抢占宿主那个端口 → 端口冲突 → APPLICATION FAILED TO START。
# 表现：不指定端口时必然启动失败，报 "Identify and stop the process
#        that's listening on port <xxxxx>"。
#
# 本脚本在启动前清除这两个注入变量，并把 java.io.tmpdir 指向仓库内
# 可写目录（沙箱下 %TMP% 可能指向 C:\Windows\ 导致 AccessDenied），
# 从而保证 16 个服务按各自 application.yml 的端口正常启动。
# ----------------------------------------------------------
# 可用环境变量覆盖：
#   JAVA_HOME   指定 JDK 21 根目录
#   MVN         指定 mvn 可执行文件（默认自动探测 mvn / mvn.cmd）
# ==========================================================
set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# ---------- 定位 Maven ----------
if [ -z "${MVN:-}" ]; then
  if command -v mvn >/dev/null 2>&1; then
    MVN="$(command -v mvn)"
  elif [ -x "/d/1/apache-maven-3.9.6/bin/mvn.cmd" ]; then
    MVN="/d/1/apache-maven-3.9.6/bin/mvn.cmd"
  else
    echo "错误：未找到 Maven，请设置环境变量 MVN=/path/to/mvn" >&2
    exit 1
  fi
fi

TMP_LOCAL="$ROOT/logs/tmp"
mkdir -p "$TMP_LOCAL" logs

ALL=(zx-user zx-course zx-auth zx-gateway zx-exam zx-media zx-learning \
     zx-trade zx-promotion zx-aigc zx-pay zx-search zx-remark zx-message \
     zx-data zx-insight)

MODULES=("$@")
[ ${#MODULES[@]} -eq 0 ] && MODULES=("${ALL[@]}")

echo "仓库根目录: $ROOT"
echo "Maven     : $MVN"
echo "JDK       : ${JAVA_HOME:-<继承当前环境>}"
echo "启动服务  : ${MODULES[*]}"
echo "----------------------------------------------------------"

for m in "${MODULES[@]}"; do
  # 关键：unset 清除宿主注入的 SERVER__PORT / SERVER__HOST（不要用 `env -u`：
  # PATH 里的 ~/.local/bin/env 是只改 PATH 的存根脚本、没有 exec "$@"，会静默不启动进程）
  unset SERVER__PORT SERVER__HOST
  JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=$TMP_LOCAL" \
      MSYS_NO_PATHCONV=1 "$MVN" -pl "$m" spring-boot:run > "logs/$m.dev.log" 2>&1 &
  echo "  ➜ $m 已启动 (pid=$!)  日志: logs/$m.dev.log"
  sleep 1
done

echo "----------------------------------------------------------"
echo "已后台启动 ${#MODULES[@]} 个服务。查看某服务日志：tail -f logs/<module>.dev.log"
echo "网关统一入口：http://localhost:8080   接口文档：http://localhost:{port}/doc.html"
