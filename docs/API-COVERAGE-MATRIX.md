# 知行智学 · 接口覆盖清单表

> 由 `scripts/api-matrix.py` 的清单来源（`logs/tmp/scan_api.py` 扫描 16 个模块的 Controller 自动生成）。
> 共 **246** 个 HTTP 端点，全部纳入 `scripts/verify-full-suite.sh` 阶段 2 的矩阵探测（匿名 / 学员 / 管理员 三种身份）。

## 汇总

| 模块 | 优先级 | 端点数 |
|---|---|---|
| zx-aigc AI 助教 | 重要 | 20 |
| zx-auth 认证/RBAC | 核心 | 28 |
| zx-course 课程 | 核心 | 27 |
| zx-data 数据看板 | 一般 | 6 |
| zx-exam 考试 | 重要 | 20 |
| zx-insight 学情分析 | 重要 | 8 |
| zx-learning 学习 | 核心 | 37 |
| zx-media 媒资 | 一般 | 9 |
| zx-message 消息 | 一般 | 3 |
| zx-pay 支付 | 一般 | 5 |
| zx-promotion 营销 | 重要 | 17 |
| zx-remark 点赞 | 一般 | 2 |
| zx-search 搜索 | 一般 | 7 |
| zx-trade 交易 | 核心 | 33 |
| zx-user 用户 | 重要 | 24 |
| **合计** | — | **246** |


## zx-aigc AI 助教（20 个端点）

| 方法 | 路径 | 说明（方法签名） | 类型 |
|---|---|---|---|
| POST | `/admin/knowledge/preview` | @RequestBody Map<String, String> body | 写 |
| POST | `/admin/knowledge/search` | @RequestBody Map<String, String> body | 写 |
| POST | `/admin/knowledge/upload` | @RequestBody Map<String, Object> body | 写 |
| POST | `/audio/stt` | — | 写 |
| POST | `/audio/tts-stream` | @RequestBody Map<String, String> body | 写 |
| POST | `/chat` | @RequestBody Map<String, String> body, @RequestHeader(value = "Last-Event-ID", required = false) Long lastEventId, @Requ | 写 |
| POST | `/chat/stop` | @RequestBody Map<String, String> body | 写 |
| GET | `/chat/templates` | — | 读 |
| POST | `/chat/text` | @RequestBody Map<String, String> body, @RequestHeader(value = "user-info", required = false) Long userId | 写 |
| DELETE | `/embedding` | @RequestParam String id | 写 |
| GET | `/embedding` | @RequestParam String text | 读 |
| POST | `/embedding` | @RequestBody Map<String, String> body | 写 |
| GET | `/embedding/search` | @RequestParam String text, @RequestParam(defaultValue = "5") int topK | 读 |
| GET | `/embedding/search/all` | @RequestParam String text | 读 |
| POST | `/session` | @RequestHeader(value = "user-info", required = false) Long userId | 写 |
| DELETE | `/session/history` | @RequestBody(required = false) Map<String, String> body, @RequestParam(required = false) String sessionId, @RequestHeade | 写 |
| GET | `/session/history` | @RequestHeader(value = "user-info", required = false) Long userId | 读 |
| PUT | `/session/history` | @RequestBody Map<String, String> body | 写 |
| GET | `/session/hot` | — | 读 |
| GET | `/session/{sessionId}` | @PathVariable String sessionId | 读 |

## zx-auth 认证/RBAC（28 个端点）

| 方法 | 路径 | 说明（方法签名） | 类型 |
|---|---|---|---|
| POST | `/accounts/admin/login` | @RequestBody LoginFormDTO loginFormDTO, HttpServletRequest request, HttpServletResponse response | 写 |
| POST | `/accounts/login` | @RequestBody LoginFormDTO loginFormDTO, HttpServletRequest request, HttpServletResponse response | 写 |
| POST | `/accounts/logout` | HttpServletResponse response | 写 |
| POST | `/accounts/password/first-change` | @RequestBody FirstChangePasswordDTO dto | 写 |
| GET | `/accounts/refresh` | HttpServletRequest request | 读 |
| GET | `/jwks` | — | 读 |
| GET | `/menus` | — | 读 |
| POST | `/menus` | @RequestBody Menu menu | 写 |
| GET | `/menus/me` | — | 读 |
| GET | `/menus/parent/{pid}` | @PathVariable Long pid | 读 |
| DELETE | `/menus/role/{roleId}` | @PathVariable Long roleId | 写 |
| POST | `/menus/role/{roleId}` | @PathVariable Long roleId, @RequestBody List<Long> menuIds | 写 |
| DELETE | `/menus/{id}` | @PathVariable Long id | 写 |
| GET | `/menus/{id}` | @PathVariable Long id | 读 |
| PUT | `/menus/{id}` | @PathVariable Long id, @RequestBody Menu menu | 写 |
| GET | `/privileges` | PageQuery query | 读 |
| POST | `/privileges` | @RequestBody Privilege privilege | 写 |
| GET | `/privileges/options/{menuId}` | @PathVariable Long menuId | 读 |
| DELETE | `/privileges/role/{roleId}` | @PathVariable Long roleId | 写 |
| POST | `/privileges/role/{roleId}` | @PathVariable Long roleId, @RequestBody List<Long> privilegeIds | 写 |
| DELETE | `/privileges/{id}` | @PathVariable Long id | 写 |
| PUT | `/privileges/{id}` | @PathVariable Long id, @RequestBody Privilege privilege | 写 |
| GET | `/roles` | — | 读 |
| POST | `/roles` | @RequestBody Role role | 写 |
| GET | `/roles/list` | — | 读 |
| DELETE | `/roles/{id}` | @PathVariable Long id | 写 |
| GET | `/roles/{id}` | @PathVariable Long id | 读 |
| PUT | `/roles/{id}` | @PathVariable Long id, @RequestBody Role role | 写 |

## zx-course 课程（27 个端点）

| 方法 | 路径 | 说明（方法签名） | 类型 |
|---|---|---|---|
| GET | `/catalogues/batchQuery` | @RequestParam("ids") List<Long> ids | 读 |
| GET | `/catalogues/course/{courseId}` | @PathVariable Long courseId | 读 |
| GET | `/catalogues/querySectionInfoById/{id}` | @PathVariable Long id | 读 |
| POST | `/categorys/add` | @RequestBody Category category | 写 |
| GET | `/categorys/all` | — | 读 |
| PUT | `/categorys/disableOrEnable` | @RequestBody Category category | 写 |
| GET | `/categorys/getAllOfOneLevel` | — | 读 |
| GET | `/categorys/list` | @RequestParam(required = false) Long parentId | 读 |
| PUT | `/categorys/update` | @RequestBody Category category | 写 |
| DELETE | `/categorys/{id}` | @PathVariable Long id | 写 |
| GET | `/categorys/{id}` | @PathVariable Long id | 读 |
| GET | `/course/all` | — | 读 |
| GET | `/course/catalogues` | @RequestParam("ids") List<Long> ids | 读 |
| GET | `/course/name` | @RequestParam("name") String name | 读 |
| GET | `/course/simpleInfo` | @RequestParam("ids") List<Long> ids | 读 |
| GET | `/course/{id}` | @PathVariable Long id | 读 |
| GET | `/course/{id}/catalogues` | @PathVariable("id") Long id | 读 |
| GET | `/course/{id}/searchInfo` | @PathVariable Long id | 读 |
| POST | `/courses/baseInfo/save` | @RequestBody CourseFormDTO form | 写 |
| GET | `/courses/baseInfo/{id}` | @PathVariable Long id | 读 |
| GET | `/courses/checkBeforeUpShelf/{id}` | @PathVariable Long id | 读 |
| GET | `/courses/checkName` | @RequestParam String name | 读 |
| DELETE | `/courses/delete/{id}` | @PathVariable Long id | 写 |
| POST | `/courses/downShelf` | @RequestBody CourseFormDTO form | 写 |
| GET | `/courses/page` | PageQuery query, @RequestParam(required = false) String name, @RequestParam(required = false) Integer status | 读 |
| POST | `/courses/upShelf` | @RequestBody CourseFormDTO form | 写 |
| GET | `/courses/{id}` | @PathVariable Long id | 读 |

## zx-data 数据看板（6 个端点）

| 方法 | 路径 | 说明（方法签名） | 类型 |
|---|---|---|---|
| GET | `/data/board` | — | 读 |
| PUT | `/data/board/set` | @RequestBody Map<String, String> data | 写 |
| GET | `/data/today` | — | 读 |
| PUT | `/data/today/set` | @RequestBody Map<String, String> data | 写 |
| GET | `/data/top10` | — | 读 |
| PUT | `/data/top10/set` | @RequestBody Map<String, String> data | 写 |

## zx-exam 考试（20 个端点）

| 方法 | 路径 | 说明（方法签名） | 类型 |
|---|---|---|---|
| POST | `/question-results` | @RequestBody List<QuestionResult> results | 写 |
| GET | `/question-results/mine` | — | 读 |
| GET | `/question-results/mine/stats` | — | 读 |
| GET | `/question-results/mine/wrong` | — | 读 |
| POST | `/question-results/single` | @RequestBody QuestionResult result | 写 |
| GET | `/question-results/teacher/overview` | — | 读 |
| GET | `/question-results/teacher/question/{questionId}` | @PathVariable Long questionId | 读 |
| GET | `/question-results/users/{userId}/all` | @PathVariable Long userId | 读 |
| GET | `/question-results/users/{userId}/stats` | @PathVariable Long userId | 读 |
| POST | `/questions` | @RequestBody Question question | 写 |
| GET | `/questions/all` | — | 读 |
| GET | `/questions/list` | @RequestParam(name = "ids", required = false) List<Long> ids | 读 |
| GET | `/questions/numOfTeacher` | @RequestParam(required = false) Long teacherId | 读 |
| GET | `/questions/page` | PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) Integer type, @RequestP | 读 |
| GET | `/questions/scores` | @RequestParam(name = "ids", required = false) List<Long> ids | 读 |
| GET | `/questions/teacher/page` | PageQuery query, @RequestParam(required = false) String keyword, @RequestParam(required = false) Integer type, @RequestP | 读 |
| DELETE | `/questions/{id}` | @PathVariable Long id | 写 |
| GET | `/questions/{id}` | @PathVariable Long id | 读 |
| PUT | `/questions/{id}` | @PathVariable Long id, @RequestBody Question question | 写 |
| PUT | `/questions/{id}/publish` | @PathVariable Long id, @RequestParam(name = "published", defaultValue = "true") boolean published | 写 |

## zx-insight 学情分析（8 个端点）

| 方法 | 路径 | 说明（方法签名） | 类型 |
|---|---|---|---|
| GET | `/insight/dashboard` | — | 读 |
| GET | `/insight/learning-path` | — | 读 |
| GET | `/insight/profiles/mine` | — | 读 |
| GET | `/insight/profiles/{userId}` | @PathVariable Long userId | 读 |
| POST | `/insight/reports/generate` | — | 写 |
| POST | `/insight/reports/generate/{userId}` | @PathVariable Long userId | 写 |
| GET | `/insight/reports/latest` | — | 读 |
| GET | `/insight/teacher/students/{userId}` | @PathVariable Long userId | 读 |

## zx-learning 学习（37 个端点）

| 方法 | 路径 | 说明（方法签名） | 类型 |
|---|---|---|---|
| POST | `/boards` | @RequestBody Board board | 写 |
| GET | `/boards/page` | PageQuery query, @RequestParam(value = "courseId", required = false) Long courseId | 读 |
| DELETE | `/boards/{id}` | @PathVariable Long id | 写 |
| GET | `/boards/{id}` | @PathVariable Long id | 读 |
| GET | `/learning-records/my` | — | 读 |
| POST | `/learning-records/progress` | @RequestBody LearningProgressDTO form | 写 |
| GET | `/learning-records/stats/active` | — | 读 |
| GET | `/learning-records/users/{userId}/all` | @PathVariable("userId") Long userId | 读 |
| GET | `/learning-records/users/{userId}/sum` | @PathVariable("userId") Long userId | 读 |
| POST | `/lessons/internal/enroll` | @RequestBody LessonEnrollDTO dto | 内部/Feign |
| GET | `/lessons/mine/course-ids` | — | 读 |
| GET | `/lessons/now` | — | 读 |
| GET | `/lessons/page` | PageQuery query | 读 |
| GET | `/lessons/plans` | — | 读 |
| POST | `/lessons/plans` | @RequestBody Map<String, Object> plan | 写 |
| GET | `/lessons/users/{userId}/course-ids` | @PathVariable("userId") Long userId | 读 |
| DELETE | `/lessons/{courseId}` | @PathVariable Long courseId | 写 |
| GET | `/lessons/{courseId}` | @PathVariable Long courseId | 读 |
| GET | `/lessons/{courseId}/count` | @PathVariable Long courseId | 读 |
| GET | `/lessons/{courseId}/valid` | @PathVariable Long courseId | 读 |
| POST | `/notes` | @RequestBody Note note | 写 |
| GET | `/notes/page` | PageQuery query, @RequestParam(required = false) Long courseId, @RequestParam(required = false) Long lessonId | 读 |
| DELETE | `/notes/{id}` | @PathVariable Long id | 写 |
| PUT | `/notes/{id}` | @PathVariable Long id, @RequestBody Note note | 写 |
| POST | `/points/award` | @RequestBody PointsAwardDTO award | 写 |
| GET | `/points/rank` | @RequestParam(value = "top", defaultValue = "10") Integer top | 读 |
| GET | `/points/records/page` | PageQuery query | 读 |
| GET | `/points/summary` | — | 读 |
| GET | `/points/users/{userId}/total` | @PathVariable("userId") Long userId | 读 |
| POST | `/replies` | @RequestBody BoardReply reply | 写 |
| DELETE | `/replies/{id}` | @PathVariable Long id | 写 |
| GET | `/sign-ins` | — | 读 |
| POST | `/sign-ins` | — | 写 |
| GET | `/sign-ins/today` | — | 读 |
| GET | `/sign-ins/users/{userId}/streak` | @PathVariable("userId") Long userId | 读 |
| GET | `/sign-records` | — | 读 |
| POST | `/sign-records` | — | 写 |

## zx-media 媒资（9 个端点）

| 方法 | 路径 | 说明（方法签名） | 类型 |
|---|---|---|---|
| POST | `/files` | @RequestParam("file") MultipartFile file | 写 |
| GET | `/files/view/{*key}` | @PathVariable String key, HttpServletResponse response | 读 |
| DELETE | `/medias` | @RequestBody Long[] mediaIds | 写 |
| GET | `/medias` | — | 读 |
| POST | `/medias` | @RequestBody Map<String, Object> media | 写 |
| GET | `/medias/signature/play` | @RequestParam Long mediaId | 读 |
| GET | `/medias/signature/preview` | @RequestParam Long mediaId | 读 |
| GET | `/medias/signature/upload` | — | 读 |
| DELETE | `/medias/{mediaId}` | @PathVariable Long mediaId | 写 |

## zx-message 消息（3 个端点）

| 方法 | 路径 | 说明（方法签名） | 类型 |
|---|---|---|---|
| GET | `/inboxes` | — | 读 |
| POST | `/inboxes` | @RequestBody Map<String, Object> message | 写 |
| POST | `/sms/message` | @RequestBody Map<String, Object> message | 写 |

## zx-pay 支付（5 个端点）

| 方法 | 路径 | 说明（方法签名） | 类型 |
|---|---|---|---|
| POST | `/notify/alipay` | @RequestBody Map<String, Object> body | 写 |
| POST | `/notify/wxpay` | @RequestBody Map<String, Object> body | 写 |
| GET | `/pay-channels/list` | — | 读 |
| POST | `/pay-orders` | @RequestBody Map<String, Object> request | 写 |
| GET | `/pay-orders/{bizOrderId}/status` | @PathVariable Long bizOrderId | 读 |

## zx-promotion 营销（17 个端点）

| 方法 | 路径 | 说明（方法签名） | 类型 |
|---|---|---|---|
| POST | `/coupons` | @RequestBody CouponFormDTO form | 写 |
| GET | `/coupons/page` | PageQuery query, @RequestParam(required = false) String name, @RequestParam(required = false) Integer status, @RequestPa | 读 |
| POST | `/coupons/seckill/reconcile/{couponId}` | @PathVariable Long couponId | 写 |
| POST | `/coupons/seckill/warmup/{couponId}` | @PathVariable Long couponId | 写 |
| DELETE | `/coupons/{id}` | @PathVariable Long id | 写 |
| GET | `/coupons/{id}` | @PathVariable Long id | 读 |
| PUT | `/coupons/{id}/issue` | @PathVariable Long id | 写 |
| PUT | `/coupons/{id}/pause` | @PathVariable Long id | 写 |
| GET | `/user-coupons` | @RequestParam(required = false) Integer status | 读 |
| POST | `/user-coupons/claim` | @RequestBody(required = false) Map<String, Object> body, @RequestParam(required = false) Long couponId | 写 |
| POST | `/user-coupons/internal/mark-refunded` | @RequestParam Long orderId | 内部/Feign |
| POST | `/user-coupons/internal/mark-used` | @RequestParam(required = false) Long userCouponId, @RequestParam(required = false) Long userId, @RequestParam(required = | 内部/Feign |
| POST | `/user-coupons/redeem` | @RequestParam Long couponId, @RequestParam String code | 写 |
| GET | `/user-coupons/rules` | @RequestParam("ids") List<Long> ids | 读 |
| POST | `/user-coupons/seckill/{couponId}` | @PathVariable Long couponId | 写 |
| GET | `/user-coupons/seckill/{couponId}/result` | @PathVariable Long couponId | 读 |
| POST | `/user-coupons/{id}/use` | @PathVariable Long id, @RequestParam(required = false) Long orderId | 写 |

## zx-remark 点赞（2 个端点）

| 方法 | 路径 | 说明（方法签名） | 类型 |
|---|---|---|---|
| POST | `/likes` | @RequestBody Map<String, Long> body | 写 |
| GET | `/likes/list` | @RequestParam("bizIds") List<Long> bizIds | 读 |

## zx-search 搜索（7 个端点）

| 方法 | 路径 | 说明（方法签名） | 类型 |
|---|---|---|---|
| GET | `/courses/name` | @RequestParam String keyword | 读 |
| GET | `/courses/portal` | @RequestParam(required = false) String keyword | 读 |
| GET | `/interests` | — | 读 |
| POST | `/interests` | @RequestBody Map<String, Object> interest | 写 |
| GET | `/recommend/best` | — | 读 |
| GET | `/recommend/free` | — | 读 |
| GET | `/recommend/new` | — | 读 |

## zx-trade 交易（33 个端点）

| 方法 | 路径 | 说明（方法签名） | 类型 |
|---|---|---|---|
| DELETE | `/carts` | @RequestBody(required = false) List<Long> ids | 写 |
| GET | `/carts` | — | 读 |
| POST | `/carts` | @RequestBody Cart item | 写 |
| DELETE | `/carts/course/{courseId}` | @PathVariable Long courseId | 写 |
| DELETE | `/carts/{id}` | @PathVariable Long id | 写 |
| GET | `/order-details/course/{id}` | @PathVariable("id") Long courseId | 读 |
| GET | `/order-details/enrollNum` | @RequestParam Long courseId | 读 |
| POST | `/order-details/reconcile-lessons` | @RequestParam(value = "limit", required = false) Integer limit | 写 |
| GET | `/order-details/stats/dashboard` | — | 读 |
| GET | `/orders/admin/export` | AdminOrderQueryDTO query | 读 |
| GET | `/orders/admin/page` | AdminOrderQueryDTO query | 读 |
| PUT | `/orders/admin/refund/audit` | @RequestBody Map<String, Object> body | 写 |
| GET | `/orders/admin/refunds` | @RequestParam(required = false) Integer pageNo, @RequestParam(required = false) Integer pageSize, @RequestParam(required | 读 |
| GET | `/orders/admin/statistics` | — | 读 |
| GET | `/orders/admin/users/{userId}/courses` | @PathVariable Long userId | 读 |
| DELETE | `/orders/admin/{id}` | @PathVariable Long id | 写 |
| GET | `/orders/admin/{id}` | @PathVariable Long id | 读 |
| PUT | `/orders/admin/{id}/status` | @PathVariable Long id, @RequestParam Integer status | 写 |
| GET | `/orders/bought-course-ids` | — | 读 |
| POST | `/orders/freeCourse/{courseId}` | @PathVariable Long courseId | 写 |
| GET | `/orders/page` | PageQuery query, @RequestParam(required = false) Integer status | 读 |
| POST | `/orders/pay/callback` | @RequestBody OrderFormDTO body | 写 |
| POST | `/orders/pay/mock/{id}` | @PathVariable Long id | 写 |
| GET | `/orders/pay/sign` | @RequestParam Long orderId, @RequestParam Long amount, @RequestParam String payNo | 读 |
| POST | `/orders/placeOrder` | @RequestBody OrderFormDTO form | 写 |
| DELETE | `/orders/{id}` | @PathVariable Long id | 写 |
| GET | `/orders/{id}` | @PathVariable Long id | 读 |
| POST | `/orders/{id}/refund` | @PathVariable Long id, @RequestBody(required = false) Map<String, Object> body | 写 |
| POST | `/orders/{id}/timeout` | @PathVariable Long id | 写 |
| POST | `/refund-apply` | @RequestBody Map<String, Object> apply | 写 |
| PUT | `/refund-apply/approval` | @RequestBody Map<String, Object> body | 写 |
| GET | `/refund-apply/page` | — | 读 |
| GET | `/refund-apply/{id}` | @PathVariable Long id | 读 |

## zx-user 用户（24 个端点）

| 方法 | 路径 | 说明（方法签名） | 类型 |
|---|---|---|---|
| GET | `/staffs/page` | — | 读 |
| GET | `/students/page` | — | 读 |
| PUT | `/students/password` | @RequestBody Map<String, String> body | 写 |
| POST | `/students/register` | @RequestBody UserFormDTO form | 写 |
| GET | `/teachers/page` | — | 读 |
| POST | `/teachers/register` | @RequestBody UserFormDTO form | 写 |
| POST | `/users` | @RequestBody UserFormDTO form | 写 |
| PUT | `/users` | @RequestBody UserFormDTO form | 写 |
| POST | `/users/bootstrap/admin` | @RequestBody BootstrapAdminDTO dto | 写 |
| GET | `/users/bootstrap/admin-exists` | — | 读 |
| PUT | `/users/bootstrap/password` | @RequestBody PasswordChangeDTO dto | 写 |
| GET | `/users/checkCellphone` | @RequestParam String cellphone | 读 |
| POST | `/users/detail/{isStaff}` | @RequestBody LoginFormDTO loginFormDTO, @PathVariable("isStaff") boolean isStaff | 写 |
| GET | `/users/ids` | @RequestParam("phone") String phone | 读 |
| GET | `/users/list` | @RequestParam("ids") List<Long> ids | 读 |
| GET | `/users/me` | — | 读 |
| GET | `/users/page` | PageQuery query, @RequestParam(required = false) Integer type | 读 |
| GET | `/users/stats/total` | — | 读 |
| DELETE | `/users/{id}` | @PathVariable Long id | 写 |
| GET | `/users/{id}` | @PathVariable Long id | 读 |
| PUT | `/users/{id}` | @PathVariable Long id, @RequestBody UserFormDTO form | 写 |
| PUT | `/users/{id}/password/default` | @PathVariable Long id | 写 |
| PUT | `/users/{id}/status/{status}` | @PathVariable Long id, @PathVariable Integer status | 写 |
| GET | `/users/{id}/type` | @PathVariable("id") Long id | 读 |

---

## 测试用例覆盖映射

| 测试维度 | 覆盖方式 | 对应脚本/阶段 |
|---|---|---|
| 接口可达性 / 契约一致性 | 全部 246 端点 × 匿名/学员/管理员 | `verify-full-suite.sh` 阶段 2 |
| 鉴权（匿名 401 / 角色 403） | 白名单与受保护接口双端验证 | 阶段 1、阶段 5 |
| 垂直越权（越权调用管理端） | 角色/菜单/权限/券/题目/看板/短信/课程/用户 13 类写接口 | 阶段 3 |
| 水平越权（IDOR） | 学情/积分/课表/答题/签到/订单/会话/账号 12 类私有资源 | 阶段 4 |
| 参数校验与边界 | 注入串、超长、类型错误、缺参、不存在资源、通配符 | 阶段 6 |
| 敏感数据脱敏 | 7 类响应体 + JWT 载荷 | 阶段 7 |
| 幂等与凭证安全 | 重复登录/注册/删除/查询、点赞切换 | 阶段 8 |
| 数据一致性 | 9 条 SQL 不变量（课表/订单/券/目录/死信） | 阶段 9 |
| 性能 | 9 个核心接口各 20 次采样 P95 + 200 并发 | 阶段 10 |
| 前端功能/UI/联调/性能 | 构建、类型、路由、状态、空错态、请求层、体积 | `verify-frontend.sh` 8 阶段 |