# 前端「打开后一片空白」排查与验证报告

日期：2026-09-12
结论：**前端代码没有问题**；空白页来自「启动方式 / 访问方式」错误，另修复了一处会导致整页空白的路由守卫缺陷。

---

## 一、现象

启动后端与前端后，浏览器打开界面只有空白，无任何内容。

## 二、实测排查过程

| 步骤 | 做法 | 结果 |
|---|---|---|
| 1 | 检查端口监听 | `5173`（前端）、`8080~8095`（后端）**全部无监听** —— 前后端当时都没真正跑起来 |
| 2 | 检查中间件 | MySQL(3306) 在跑；**Redis(6379) 未启动** |
| 3 | 用 Vite 启动前端（`node node_modules/vite/bin/vite.js`） | 启动正常，`Local: http://localhost:5173/` |
| 4 | 无头浏览器（Edge headless）渲染 8 个路由 | 全部正常渲染：#app 内容 4.9KB~17.6KB，标题正确，无控制台报错 |
| 5 | 用 `file://` 打开 `zx-web/index.html` | `#app` 仅 95 字节 → **白屏** |
| 6 | 用 `file://` 打开 `zx-web/dist/index.html` | `#app` 仅 41 字节 → **完全白屏** |
| 7 | 检查 `pnpm` | **本机未安装 pnpm**（`command not found`），而 README 写的是 `pnpm dev` |
| 8 | 验证 `npm run dev` | 正常启动，`VITE v5.4.21 ready in 2142 ms`，5173 LISTENING |

## 三、根因

1. **访问方式错误（主因）**：项目使用原生 ESM + `/src`、`/assets` 绝对路径，必须由 HTTP 服务托管。
   直接用浏览器打开 `index.html` / `dist/index.html`（`file://`）会被浏览器拦截模块加载，**必然白屏**。
   必须访问 Vite 控制台打印的 `Local: http://localhost:5173/`。
2. **启动命令不可用**：本机未安装 pnpm，按 README 执行 `pnpm dev` 直接失败 → 开发服务器从未真正启动。
   本机 npm 可用（10.9.7），`npm run dev` 可正常启动。
3. **附带修复：路由守卫缺陷（会产生真白屏）**
   未登录时直接访问受控路由（如 `/exam`、`/admin/orders`、`/teacher/answers`、`/insight`），
   弹窗选择「暂不登录」会走 `return false` **中止导航**；此时首次导航没有任何已匹配路由，
   `RouterView` 无内容可渲染 → 整页空白且无法自行恢复。
   已在 `zx-web/src/router/guard.ts` 修复：仅在「页面内跳转」时留在原页，首次导航回首页。

## 四、修复内容

| 文件 | 改动 |
|---|---|
| `zx-web/src/router/guard.ts` | 取消登录弹窗后，首次导航回退到首页，杜绝空白页 |
| `README.md` | 第 6 节补充 `npm run dev` 备选命令与「禁止 file:// 打开」警告；FAQ 增加「前端页面全白」条目 |
| `zx-web/README.md` | 快速开始补充 npm 等价命令与 `file://` 白屏说明 |

## 五、修复后全链路验证（2026-09-12 16:04）

启动 9 个服务（gateway/auth/user/course/exam/trade/insight/learning/promotion）+ Redis：

```
全部就绪（轮询 2）
学员登录 code=200  token长度=184
教师登录 code=200  token长度=184
优惠券分页        -> code=200
课程分页          -> code=200（total=13）
课程分类          -> code=200
购物车            -> code=200
我的订单          -> code=200（total=10）
学员题目          -> code=200
学情画像          -> code=200（abilities/trends 齐全）
权限隔离：
  学员 → 管理端订单  HTTP 200 / body.code=403  无权限访问该资源
  学员 → 教师学情    HTTP 200 / body.code=403
  教师 → 管理端订单  HTTP 200 / body.code=403
  未登录 → 购物车    HTTP 401
前端 5173 LISTENING
```

原始日志：`logs/blank-page-verify-2026-09-12.txt`

## 六、正确的启动方式（本机）

```bash
# 1) 中间件（Redis 必启，MySQL 需已运行）
/d/1/Redis/redis-server.exe /d/1/Redis/redis.windows.conf

# 2) 后端（Git Bash；脚本会清除宿主注入的 SERVER__PORT/SERVER__HOST）
bash scripts/dev-start-backend.sh

# 3) 前端
cd zx-web && npm run dev      # 本机无 pnpm，用 npm

# 4) 浏览器访问控制台打印的地址（不要用 file:// 打开 html）
http://localhost:5173
```
