# 知行智学（zx-learn）数据库设计

> 数据库初始化脚本：[sql/init.sql](../sql/init.sql)
> 设计原则：**按服务分库**（Database per Service），每个服务独立数据库，避免跨库强耦合。
> 公共字段约定（BasePO）：`create_time / update_time / creater / updater / deleted`（逻辑删除）。

---

## 1. 分库总览

| 数据库 | 归属服务 | 说明 |
|---|---|---|
| zx_auth | auth | 认证与 RBAC |
| zx_user | user | 用户 |
| zx_course | course | 课程 |
| zx_exam / zx_media / zx_learning / zx_trade / zx_promotion / zx_pay / zx_search / zx_remark / zx_message / zx_data | 对应骨架服务 | 骨架阶段使用内存存储，生产可参照下表补表 |

## 2. zx_auth（认证库）

### role 角色表
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| name | VARCHAR(64) | 角色名称 |
| code | VARCHAR(64) | 角色编码 |
| remark | VARCHAR(255) | 备注 |
| + 公共字段 | | |

### menu 菜单表
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| parent_id | BIGINT | 父菜单 id |
| name | VARCHAR(64) | 菜单名称 |
| path | VARCHAR(255) | 路由地址 |
| component | VARCHAR(255) | 组件路径 |
| icon / sort / type / status | | 图标/排序/类型/状态 |

### privilege 权限表
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| menu_id | BIGINT | 所属菜单 |
| method | VARCHAR(16) | 请求方法 |
| uri | VARCHAR(255) | 请求路径 |
| name / description | | 权限名/描述 |

### account_role / role_menu / role_privilege 关联表
角色-账号、角色-菜单、角色-权限 三张关联表实现 RBAC。

### login_record 登录记录表
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| user_id | BIGINT | 用户 id |
| cell_phone | VARCHAR(20) | 手机号 |
| ipv4 | VARCHAR(64) | 登录 IP |
| login_type | INT | 1 学员 / 2 员工 |
| login_time | DATETIME | 登录时间 |

## 3. zx_user（用户库）

### user 用户表
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| cell_phone | VARCHAR(20) | 手机号（登录凭证） |
| username | VARCHAR(64) | 用户名 |
| password | VARCHAR(128) | 密码（BCrypt） |
| name | VARCHAR(64) | 姓名 |
| type | INT | 1 员工 / 2 学员 / 3 教师 |
| status | INT | 0 禁用 / 1 正常 |

### user_detail 用户详情表
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键（与 user 一致） |
| age / gender / image / intro | | 年龄/性别/头像/简介 |
| role | VARCHAR | 扩展角色 |

## 4. zx_course（课程库）

### course 正式课程表
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| name | VARCHAR(64) | 课程名称 |
| cover_url | VARCHAR(255) | 封面 |
| price | INT | 价格（分） |
| category_id_lv1/2/3 | BIGINT | 三级分类 |
| teacher_id | BIGINT | 授课教师 |
| free | TINYINT | 是否免费 |
| description | TEXT | 课程简介 |
| status | INT | 0 下架 / 1 上架 |
| publish_times | INT | 发布次数 |

### course_draft 课程草稿表
与 course 同构，编辑期使用；上架时同步到 course。

### course_catalogue 课程目录表
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| course_id | BIGINT | 所属课程 |
| name | VARCHAR(255) | 章节名 |
| index | INT | 排序 |
| level | INT | 层级（章/节） |
| type | INT | 类型（视频/图文） |
| media_id / video_id | BIGINT | 关联媒资 |

### category 课程分类表
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| name | VARCHAR(255) | 分类名 |
| parent_id | BIGINT | 父分类 |
| sort | INT | 排序 |
| level | INT | 层级 |

## 5. 已落地服务表结构与状态机（trade / promotion / learning）

> 下表由 MyBatis-Plus + MySQL 持久化支撑，完整 DDL 以 [sql/init.sql](../sql/init.sql) 为权威来源，均含公共字段
> `id, create_time, update_time, creater, updater, deleted`（逻辑删除）。

### zx_trade.trade_order / trade_order_detail 订单
订单状态机：**0 待支付 → 1 已支付 → 3 退款中 → 4 已退款**；待支付超时或主动取消到 **2 已关闭**，已支付后不可再关闭。
- 订单号 `order_no` 由**雪花算法**生成（沉淀于 zx-common），`uk_order_no` 唯一索引作为首道幂等。
- 金额一致性：下单时经 `CourseClient`（zx-api Feign）拉取课程价格对账，课程服务不可用时降级拒绝下单。
- 关联表：`trade_order_detail`（订单明细）、`trade_pay_record`（支付回调流水，`pay_no` 幂等）、`coupon_use_record`（核销流水）、`order_msg`（Outbox 本地消息表）。

### zx_promotion.coupon / user_coupon 优惠券
券状态机：**0 未开始 → 1 进行中 → 2 已结束 / 3 已下架**；读取时按失效时间自动将非下架券流转为已结束。
- 用户券状态机：**0 未使用 → 1 已使用 / 2 已过期**。
- 限领数量：领取校验 `issued_num < total_num`；兑换码一次性核销由 `coupon.exchange_code` 唯一索引（`uk_exchange_code`）+ `user_coupon` 上 `uk_user_coupon(user_id, coupon_id)` 双唯一索引兜底，防并发重复领取/核销。
- 秒杀增量（详见 [SECKILL.md](SECKILL.md)）：`user_coupon.coupon_code`（`SK+雪花` 券码，`uk_coupon_code` 唯一）；
  `consume_record` MQ 消费流水表（`uk_consume_key`，秒杀异步落库消费端幂等第二层，与其他库同构）。

### zx_learning.lesson / learning_record / sign_in / note 学习
- `lesson` 我的课表：`user_id, course_id, course_name, plan(JSON)`，`uk_user_course(user_id, course_id)` 唯一，新增前经 `CourseClient` 校验课程存在。
- `learning_record` 学习记录：学习进度**单调递增不可倒退**，课时完成（≥100）记 `finished`。
- `sign_in` 签到：`uk_user_date(user_id, sign_date)` 唯一索引实现**按日幂等**，连续签到天数在事务内递增计分。
- `note` 学习笔记：按用户归属隔离，仅本人可改删。

### 生产扩展参考（仍为骨架位）

### zx_pay.pay_order
`id, biz_order_no, user_id, amount, channel, status(0待支付/1成功/2失败), notify_url`

### zx_remark.liked_record
`id, biz_id, user_id, like_time`

### zx_message.inbox
`id, user_id, title, content, read_status, create_time`

### zx_trade 交易最终一致性表族（详见 docs/TRADE-CONSISTENCY.md）
- `order_msg` 本地消息表（Outbox）：与订单同事务落库，`uk_biz_key(order_id:biz_type)` 防重；
  状态机 0 待投递 → 1 已投递 → 2 已消费 / 3 死信（超 5 次指数退避重试转死信，人工处理）。
- `trade_pay_record` 支付回调流水：`uk_pay_no` 唯一索引实现回调幂等（第一层）。
- `consume_record` MQ 消费流水表：`uk_consume_key` 唯一索引，消费端幂等第二层
  （zx_course / zx_learning 库内同构表，配合各业务唯一键形成双层幂等）。
- `course_quota` 课程名额（zx_course 库）：`uk_course` 唯一，`quota` 为 NULL 表示不限名额，
  `locked_count` 为在途锁定数；锁定条件更新 `locked_count < quota OR quota IS NULL` 防超卖。
- `course_quota_record` 名额变更流水（zx_course 库）：`uk_order` 唯一，状态机
  1 已锁定 → 2 已确认（locked-1, sold+1）/ 0 已释放（locked-1）。
- `coupon_use_record` 优惠券核销流水：`uk_order` 唯一保证一单只核销一次，退回置 status=0。
