# ============================================================
# 知行智学 zx-web 前端镜像（多阶段构建）
#
# 阶段 1：Node 构建静态产物（vite build → /app/dist）
# 阶段 2：Nginx 托管静态资源 + 反向代理到网关
#
# 构建（在仓库根目录执行；zx-web 已在根 .dockerignore 中排除，所以**上下文必须是 zx-web**）：
#   docker build -t zx-learn/zx-web:latest ./zx-web
# 或直接用编排文件：docker compose -f docker-compose.prod.yml up -d --build zx-web
#
# 关键点：nginx.conf 里的 `proxy_pass http://zx-gateway:8080/;` 负责剥离前端的 /api 前缀
#        （网关路由不含 /api），缺了它生产环境所有接口都会 404。
# ============================================================

# ---------- 阶段 1：构建 ----------
FROM node:22-alpine AS build
WORKDIR /app

# 依赖清单在 zx-web/ 目录下用 pnpm 管理（有 pnpm-lock.yaml）
RUN corepack enable

# 先只拷贝依赖清单，让依赖层可复用缓存
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

# 再拷贝源码构建（.dockerignore 已排除 node_modules / dist）
COPY . .
# 用 production 模式构建：读取 .env.production（VITE_API_BASE_URL=/api）
RUN pnpm build

# ---------- 阶段 2：运行 ----------
FROM nginx:1.27-alpine

# 站点配置（含 /api 反代剥离与 SSE 免缓冲）
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=3s --retries=5 --start-period=10s \
    CMD wget -qO- http://127.0.0.1/healthz || exit 1
