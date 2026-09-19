#!/usr/bin/env bash
# =====================================================================
# 知行智学 · 核心链路一键启动（Redis + 8 服务）
# ---------------------------------------------------------------------
# 用途：本地联调 / 端到端验证前拉起最小可用链路。
#   Redis(6379) + gateway(8080) + auth(8081) + user(8082) + course(8083)
#   + exam(8084) + learning(8086) + trade(8087) + insight(8095)
#
# 用法：
#   /usr/bin/bash scripts/dev-up-core.sh          # 启动并等待全部就绪
#   SKIP_WAIT=1 /usr/bin/bash scripts/dev-up-core.sh   # 只发起启动不等就绪
#   EXTRA_SPECS="zx-promotion:8088" /usr/bin/bash scripts/dev-up-core.sh
#       # 额外拉起不在默认清单里的服务（如营销服务，优惠券相关验证需要）
#       # 只影响本进程，不改变默认清单
#
# 沙箱/Windows 注意事项（踩坑记录）：
#   * 必须清除宿主注入的 SERVER__PORT / SERVER__HOST，否则所有服务抢同一端口；
#     ⚠ 用脚本内 `unset`，**不要用 `env -u`** —— PATH 里的 ~/.local/bin/env 是只改 PATH 的
#     存根脚本（无 exec "$@"），会让 java 静默不启动（日志 0 字节、端口不监听）；
#   * java.io.tmpdir 必须写 Windows 风格路径（D:/...），Unix 路径 JVM 不认；
#   * Redis 配置路径也必须 Windows 风格，否则 "Failed to open the .conf file"；
#   * /tmp/system32 会用 Windows 版 sort/find 遮蔽 GNU 工具 → 脚本内禁用。
# =====================================================================
set -u

ROOT="D:/1/zx-learn"
cd "$ROOT" || exit 1
mkdir -p "$ROOT/logs/svc" "$ROOT/logs/tmp"

# 清除宿主注入的 SERVER__PORT / SERVER__HOST：否则被 Spring 松散绑定成 server.port，
# 9 个服务会抢同一个端口（必须放在这里，早于任何 java 启动）
unset SERVER__PORT SERVER__HOST

REDIS_DIR="/d/1/Redis"

port_busy() {
  netstat -ano 2>/dev/null | grep LISTENING | grep ":$1 " | awk '{print $5}' | awk '!seen[$0]++' | grep -q .
}
svc_up() {
  local p=$1
  MSYS_NO_PATHCONV=1 curl.exe -s -o /dev/null --max-time 3 "http://localhost:$p/" >/dev/null 2>&1 && return 0
  port_busy "$p" && return 0
  return 1
}

# ---------- Redis ----------
if port_busy 6379; then
  echo "[INFO] Redis 已在运行 (6379)"
else
  echo "[INFO] 启动 Redis ..."
  ( cd "$REDIS_DIR" && MSYS_NO_PATHCONV=1 ./redis-server.exe redis.windows.conf \
      > "$ROOT/logs/svc/redis.log" 2>&1 & )
  for i in $(seq 1 20); do
    port_busy 6379 && break
    sleep 1
  done
  port_busy 6379 && echo "[OK]   Redis 已就绪 (6379)" || echo "[FAIL] Redis 启动失败，见 logs/svc/redis.log"
fi

# ---------- 后端服务 ----------
# 默认核心链路；EXTRA_SPECS 可按需追加（如 EXTRA_SPECS="zx-promotion:8088"），
# 供只在该场景下才需要的服务使用，避免每次都多起 JVM。
# CORE_SPECS 可整体替换默认清单——机器内存吃紧时只起最小必要集合，别一次拉起全部 JVM
# （每个服务 -Xmx340m + 元空间/线程栈，实测 16 个 JVM 峰值可达 10GB+，叠加 Maven 构建会拖垮整机）。
#   CORE_SPECS="zx-auth:8081 zx-user:8082 zx-promotion:8088 zx-message:8093 zx-gateway:8080" \
#     /usr/bin/bash scripts/dev-up-core.sh
SPECS="${CORE_SPECS:-zx-user:8082 zx-course:8083 zx-auth:8081 zx-learning:8086 zx-exam:8084 zx-trade:8087 zx-insight:8095 zx-gateway:8080} ${EXTRA_SPECS:-}"

for spec in $SPECS; do
  m=${spec%%:*}
  p=${spec##*:}
  if port_busy "$p"; then
    echo "[INFO] $m 已在运行 (:$p)，跳过"
    continue
  fi
  # 用 setsid 语义（nohup + &）脱离当前会话，避免父进程退出时被回收。
  # 注意：这里不要写 `env -u SERVER__PORT ...`（曾经这么写是对的）——
  # PATH 里存在用户级存根 ~/.local/bin/env（只改 PATH、没有 exec "$@"），
  # 会让 `env -u X java ...` 静默退出 0 且**根本不启动 java**（表现为日志 0 字节、端口不监听）。
  # 改为在脚本内 unset，对子进程一样生效且不依赖任何 env 实现。
  MSYS_NO_PATHCONV=1 nohup \
    java -Djava.io.tmpdir="$ROOT/logs/tmp" -Dfile.encoding=UTF-8 -Xmx340m \
    -jar "$ROOT/$m/target/$m.jar" --server.port="$p" \
    > "$ROOT/logs/svc/$m.log" 2>&1 &
  echo "[INFO] 已发起 $m (:$p)"
done

if [ "${SKIP_WAIT:-0}" = "1" ]; then
  echo "[INFO] SKIP_WAIT=1，不等待就绪"
  exit 0
fi

# 沙箱内 JVM 冷启动可能被排队到 7 分钟以上，等待上限给足 12 分钟
echo "[INFO] 等待服务就绪（最长 12 分钟）..."
deadline=$(( $(date +%s) + 720 ))
while [ "$(date +%s)" -lt "$deadline" ]; do
  pending=""
  for spec in $SPECS; do
    p=${spec##*:}
    m=${spec%%:*}
    svc_up "$p" || pending="$pending $m"
  done
  [ -z "$pending" ] && { echo "[OK]   全部服务已就绪"; exit 0; }
  echo "  ...等待中:$pending"
  sleep 15
done

echo "[WARN] 等待超时，仍未就绪的服务:"
for spec in $SPECS; do
  p=${spec##*:}
  m=${spec%%:*}
  svc_up "$p" || echo "   - $m (:$p) 见 logs/svc/$m.log"
done
exit 1
