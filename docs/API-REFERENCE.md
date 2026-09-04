# 知行智学（zx-learn）接口参考

> 统一响应格式：`{ "code": 200, "msg": "OK", "data": ..., "requestId": "..." }`
> 未登录返回 401：`{ "code": 401, "msg": "未登录或登录已过期" }`
> 在线调试：各服务启动后访问 `http://localhost:{port}/doc.html`（Knife4j / OpenAPI3）

---

## 1. 认证服务（auth · 8081，经网关 8080）

| 方法 | 路径 | 说明 | 鉴权 |
|---|---|---|---|
| POST | /accounts/admin/login | 管理员/员工登录 | 白名单 |
| POST | /accounts/login | 学员登录 | 白名单 |
| POST | /accounts/refresh | 刷新 accessToken | 白名单 |
| GET | /accounts/me | 当前用户信息 | ✅ |
| GET | /jwks | JWT 公钥 | 白名单 |
| GET/POST/PUT/DELETE | /menus、/roles、/privileges | RBAC 管理 | ✅ |

## 2. 用户服务（user · 8082）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /users/detail/{isStaff} | 校验账号密码（Feign 内部） |
| GET | /users/page | 用户分页查询 |
| POST | /users | 新增用户 |
| PUT | /users/{id} | 更新用户 |
| PUT | /users/{id}/password | 修改/重置密码 |
| PUT | /users/{id}/status/{status} | 启停用 |
| GET | /students、/teachers、/staffs | 按角色查询 |

## 3. 课程服务（course · 8083）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /courses | 保存课程草稿 |
| GET | /courses/{id} | 查询课程详情（含目录） |
| PUT | /courses/{id}/up | 上架（草稿→正式） |
| PUT | /courses/{id}/down | 下架 |
| DELETE | /courses/{id} | 删除课程 |
| GET | /courses/page | 课程分页 |
| GET | /courses/name/check | 名称唯一性校验 |
| GET | /categorys/tree | 分类树 |
| GET | /catalogues | 课程目录 |

## 4. AI 助教（aigc · 8089）

| 方法 | 路径 | 说明 | 响应 |
|---|---|---|---|
| POST | /chat/text | 文本对话（非流式） | JSON |
| POST | /chat | 流式对话 | SSE |
| POST | /chat/stop | 停止生成 | JSON |
| GET | /chat/templates | 提问模板 | JSON |
| GET/POST/DELETE | /session | 会话管理 | JSON |
| GET/POST | /embedding | 向量检索（可插拔） | JSON |
| GET/POST | /audio | 语音接口（可插拔） | JSON |

SSE 流式事件格式（`ChatEventVO`）：

```
event: start     → {"type":"start","agentType":"RECOMMEND"}
event: delta     → {"type":"delta","content":"..."}
event: end       → {"type":"end"}
```

## 5. 考试服务（exam · 8084，骨架）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /questions | 新增题目 |
| PUT | /questions/{id} | 更新题目 |
| DELETE | /questions/{id} | 删除题目 |
| GET | /questions/{id} | 查询题目 |
| GET | /questions/list?ids= | 批量查询 |
| GET | /questions/scores?ids= | 批量查分值 |
| GET | /questions/numOfTeacher | 教师题目数 |

## 6. 媒资服务（media · 8085，骨架）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /medias | 媒资分页 |
| POST | /medias | 保存媒资信息 |
| GET | /medias/signature/upload | 上传签名 |
| GET | /medias/signature/play | 播放签名 |
| GET | /medias/signature/preview | 预览签名 |
| DELETE | /medias/{mediaId} | 删除媒资 |
| DELETE | /medias | 批量删除 |
| POST | /files/upload | 文件上传 |

## 7. 学习服务（learning · 8086，骨架）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /lessons/page | 我的课表 |
| GET | /lessons/now | 最近学习课程 |
| GET | /lessons/{courseId} | 课程学习状态 |
| GET | /lessons/{courseId}/count | 学习人数 |
| GET | /lessons/{courseId}/valid | 是否有权学习 |
| POST | /lessons/plans | 学习计划 |
| GET | /lessons/plans | 学习计划列表 |

## 8. 交易服务（trade · 8087，骨架）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /orders/placeOrder | 下单 |
| GET | /orders/page | 订单列表 |
| GET | /orders/{id} | 订单详情 |
| POST | /orders/freeCourse/{courseId} | 0 元下单 |
| GET | /carts | 购物车（骨架） |
| POST | /refund-apply | 退款申请（骨架） |
| GET | /order-details/enrollNum | 报名人数（Feign） |
| GET | /order-details/course/{id} | 是否购买（Feign） |

## 9. 营销服务（promotion · 8088，骨架）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /coupons | 新增优惠券 |
| GET | /coupons/page | 优惠券列表 |
| GET | /coupons/{id} | 优惠券详情 |
| PUT | /coupons/{id}/issue | 发放 |
| PUT | /coupons/{id}/pause | 暂停 |
| DELETE | /coupons/{id} | 删除 |

## 10. 支付服务（pay · 8090，骨架）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /pay-channels/list | 支付渠道列表 |
| POST | /pay-orders | 创建支付单 |
| GET | /pay-orders/{bizOrderId}/status | 查询支付状态 |
| POST | /notify/alipay | 支付宝回调 |
| POST | /notify/wxpay | 微信回调 |

## 11. 搜索服务（search · 8091，骨架）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /courses/portal | 课程门户搜索 |
| GET | /courses/name?keyword= | 按名称查课程 id |
| GET | /recommend/best | 精品推荐 |
| GET | /recommend/new | 新课推荐 |
| GET | /recommend/free | 免费好课 |
| POST | /interests | 保存兴趣 |
| GET | /interests | 兴趣列表 |

## 12. 点赞服务（remark · 8092，骨架）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /likes | 点赞/取消点赞（幂等切换） |
| GET | /likes/list?bizIds= | 批量查询点赞状态 |

## 13. 消息服务（message · 8093，骨架）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | /sms/message | 发送短信（异步） |
| POST | /inboxes | 发送站内信 |
| GET | /inboxes | 收件箱列表 |

## 14. 数据看板（data · 8094，骨架）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /data/board | 运营看板 |
| PUT | /data/board/set | 写入看板数据 |
| GET | /data/today | 今日数据 |
| GET | /data/top10 | Top10 排行 |
