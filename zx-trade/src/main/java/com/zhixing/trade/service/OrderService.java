package com.zhixing.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixing.api.client.course.CourseClient;
import com.zhixing.api.client.learning.LearningClient;
import com.zhixing.api.dto.course.CourseSimpleInfoDTO;
import com.zhixing.api.dto.learning.LessonEnrollDTO;
import com.zhixing.api.dto.trade.OrderPaidMsg;
import com.zhixing.api.dto.trade.QuotaMsg;
import com.zhixing.api.dto.trade.TradeStatsDTO;
import com.zhixing.common.domain.PageDTO;
import com.zhixing.common.domain.PageQuery;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.exceptions.BizIllegalException;
import com.zhixing.common.exceptions.ForbiddenException;
import com.zhixing.common.mq.MqTopics;
import com.zhixing.common.mq.RocketMQTemplate;
import com.zhixing.common.utils.SnowflakeIdGenerator;
import com.zhixing.common.utils.TxSupport;
import com.zhixing.common.utils.UserContext;
import com.zhixing.trade.domain.dto.OrderFormDTO;
import com.zhixing.trade.domain.po.Order;
import com.zhixing.trade.domain.po.OrderDetail;
import com.zhixing.trade.domain.vo.OrderVO;
import com.zhixing.trade.mapper.OrderDetailMapper;
import com.zhixing.trade.mapper.OrderMapper;
import com.zhixing.trade.mq.CouponMsg;
import com.zhixing.trade.mq.OrderCloseMsg;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 订单服务。
 * <p>
 * 生产级下单链路（本地消息表最终一致性）：
 * <ul>
 *   <li>雪花订单号 + 订单表 order_no 唯一索引兜底（防重复下单，幂等第一层）；</li>
 *   <li>订单、明细、本地消息表在同一本地事务落库（保证消息不丢）；</li>
 *   <li>使用优惠券时先通过 Redis Lua 原子预扣库存（防超卖），随后发 MQ 异步落库；</li>
 *   <li>课程名额锁定 / 支付确认 / 关单释放均通过 zx_course_quota 事件异步完成；</li>
 *   <li>下单后发送 RocketMQ 延迟消息，超时未支付自动关单并补偿（MQ 不可用由定时兜底扫描）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderMapper orderMapper;
    private final OrderDetailMapper orderDetailMapper;
    private final CourseClient courseClient;
    private final LearningClient learningClient;
    private final OrderMsgService orderMsgService;
    private final TradeCouponService tradeCouponService;
    private final CouponStatusSyncer couponStatusSyncer;
    private final RocketMQTemplate rocketMQTemplate;
    private final IdempotencyGuard idempotencyGuard;
    private final StringRedisTemplate redisTemplate;

    /** 超时关单延迟级别（对应 broker messageDelayLevel，默认 15 分钟） */
    @Value("${rocketmq.timeout-delay-level:15}")
    private int timeoutDelayLevel;

    /**
     * 待支付订单超时分钟数（与 OrderTimeoutJob / 前端倒计时保持同一口径）。
     * 用于「我的订单」列表读取时的惰性对账关单。
     */
    @Value("${tx.order.expire-minutes:15}")
    private int expireMinutes;

    /** 订单状态：待支付 */
    public static final int STATUS_UNPAID = 0;
    /** 订单状态：已支付 */
    public static final int STATUS_PAID = 1;
    /** 订单状态：已关闭（超时未支付自动关单） */
    public static final int STATUS_CLOSED = 2;
    /** 订单状态：退款中 */
    public static final int STATUS_REFUNDING = 3;
    /** 订单状态：已退款 */
    public static final int STATUS_REFUNDED = 4;

    /**
     * 下单。同一事务内写入订单 + 明细 + 待发送（优惠券核销）消息。
     * <p>
     * 并发防重：以 Redis setIfAbsent 做 userId+courseId 粒度互斥（防双击/双开标签页
     * 同时穿透前置校验），锁在事务完成后释放（afterCompletion），10s TTL 兜底防死锁；
     * Redis 不可用时降级为前置校验 + paid_key 唯一索引兜底。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long placeOrder(OrderFormDTO form) {
        if (form == null || form.getCourseId() == null) {
            throw new BadRequestException("订单内容不能为空");
        }
        CourseSimpleInfoDTO course = courseClient.queryCourseInfoById(form.getCourseId());
        // 下架课程不允许新增下单（已存在的订单不受影响，见 CoursePurchaseGuard）
        CoursePurchaseGuard.requirePurchasable(course);
        Long userId = UserContext.getUserId();

        // 并发下单互斥（防重复提交）：粒度 user+course
        String lockKey = "order:place:" + userId + ":" + course.getId();
        boolean locked;
        try {
            locked = Boolean.TRUE.equals(
                    redisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(10)));
        } catch (Exception e) {
            log.warn("下单互斥锁获取失败（Redis 不可用），降级为前置校验+唯一索引兜底：{}", e.getMessage());
            locked = true;
        }
        if (!locked) {
            throw new BizIllegalException("该课程正在下单中，请勿重复提交");
        }
        // 锁随事务结束释放：避免"锁已删但事务未提交"窗口内第二个请求穿透前置校验
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    try {
                        redisTemplate.delete(lockKey);
                    } catch (Exception ignore) {
                        // TTL 兜底自动过期
                    }
                }
            });
        } else {
            redisTemplate.delete(lockKey);
        }

        // 重复购买前置拦截（已购/待支付单）：放在扣券之前，避免拦截时已预扣优惠券需回滚补偿
        assertNotDuplicateOrder(userId, course.getId(), course.getName());
        Long price = course.getPrice() == null ? 0L : course.getPrice();
        Long totalFee = form.getTotalFee() == null ? price : form.getTotalFee();
        boolean useCoupon = form.getCouponId() != null && form.getCouponId() > 0;

        // 金额一致性校验：防前后端篡改
        Long deduction;
        if (useCoupon) {
            if (totalFee < 0 || totalFee > price) {
                throw new BizIllegalException("优惠后实付金额非法");
            }
            deduction = price - totalFee;
        } else {
            if (!totalFee.equals(price)) {
                throw new BizIllegalException("订单金额与课程价格不一致");
            }
            deduction = 0L;
        }

        // 优惠券原子预扣库存（余量不足/超限领直接抛异常回滚）
        if (useCoupon) {
            tradeCouponService.deductStock(form.getCouponId(), userId, 1, 1);
        }

        Order order = new Order();
        order.setId(SnowflakeIdGenerator.getInstance().nextId());
        order.setOrderNo("T" + order.getId());
        order.setUserId(userId);
        order.setCourseId(course.getId());
        order.setCourseName(course.getName());
        order.setCoursePrice(price);
        order.setTotalFee(totalFee);
        order.setCouponId(useCoupon ? form.getCouponId() : null);
        order.setDeduction(deduction);
        order.setStatus(STATUS_UNPAID);
        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            // 并发兜底：uk_user_course_paid 唯一索引（生成列 paid_key）拦截双击/并发重复下单。
            // 说明已有已支付订单存在 —— 顺手自愈一次开课，避免"已支付但课表为空"把用户卡死。
            syncEnroll(userId, course.getId(), course.getName());
            throw new BizIllegalException("您已购买该课程，已为你开通学习，可直接开始学习");
        }

        OrderDetail detail = new OrderDetail();
        detail.setOrderId(order.getId());
        detail.setCourseId(course.getId());
        detail.setName(course.getName());
        detail.setPrice(price);
        orderDetailMapper.insert(detail);

        // 同事务写入"待发送"消息：优惠券核销异步落库
        if (useCoupon) {
            CouponMsg couponMsg = new CouponMsg();
            couponMsg.setAction(MqTopics.Tags.COUPON_USE);
            couponMsg.setUserId(userId);
            couponMsg.setCouponId(form.getCouponId());
            couponMsg.setUserCouponId(form.getUserCouponId());
            couponMsg.setOrderId(order.getId());
            couponMsg.setAmount(deduction);
            orderMsgService.enqueue(order.getId(), "couponUse",
                    MqTopics.TOPIC_COUPON_USE, MqTopics.Tags.COUPON_USE, couponMsg);
            // 同步回写券状态为"已使用"：券列表/我的优惠券立即更新，避免"券用掉了还显示未使用"
            couponStatusSyncer.syncUsedAfterCommit(form.getUserCouponId(), userId,
                    form.getCouponId(), order.getId());
        }

        // 同事务写入"待发送"消息：锁定课程名额（zx-course 异步消费）
        QuotaMsg quotaMsg = new QuotaMsg();
        quotaMsg.setOrderId(order.getId());
        quotaMsg.setCourseId(course.getId());
        quotaMsg.setUserId(userId);
        orderMsgService.enqueue(order.getId(), "quotaLock",
                MqTopics.TOPIC_COURSE_QUOTA, MqTopics.Tags.QUOTA_LOCK, quotaMsg);

        // 延迟消息：超时关单（MQ 不可用时由 OrderTimeoutJob 定时兜底扫描）
        OrderCloseMsg closeMsg = new OrderCloseMsg();
        closeMsg.setOrderId(order.getId());
        closeMsg.setOrderNo(order.getOrderNo());
        rocketMQTemplate.send(MqTopics.TOPIC_ORDER_TIMEOUT,
                MqTopics.Tags.ORDER_CLOSE, closeMsg, timeoutDelayLevel);

        log.info("下单成功：id={}, userId={}, courseId={}, useCoupon={}", order.getId(), userId, course.getId(), useCoupon);
        return order.getId();
    }

    /**
     * 0 元下单（免费课程直接支付到账）。
     * <p>
     * 与付费链路一致：写入订单后需发"订单支付成功"事件（TOPIC_ORDER_PAID），
     * 由 zx-learning 消费开课写课表——否则免费课购买后学习中心看不到课程。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long freeCourse(Long courseId) {
        CourseSimpleInfoDTO course = courseClient.queryCourseInfoById(courseId);
        if (course == null) {
            throw new BadRequestException("课程不存在");
        }
        Long userId = UserContext.getUserId();

        // ① 课表已开通 → 幂等成功。
        //    用户点「加入学习」本就期望"直接进入学习"，此时抛"已拥有"错误反而把人挡在门外；
        //    返回成功让前端刷新「已拥有」与课表，两端立即一致。
        Long existedOrderId = latestPaidOrderId(userId, course.getId());
        if (lessonCourseIds(userId).contains(course.getId())) {
            return existedOrderId == null ? 0L : existedOrderId;
        }
        // ② 已支付订单存在但课表缺失（支付成功时开课失败 / orderPaid 事件重试耗尽）→ 补开课后视为成功
        if (existedOrderId != null) {
            syncEnroll(userId, course.getId(), course.getName());
            log.info("免费课开课自愈：userId={}, courseId={}, orderId={}",
                    userId, course.getId(), existedOrderId);
            return existedOrderId;
        }
        // ③ 下架课程不允许新开课：前面两条（已拥有 / 已支付补开课）走的是幂等成功语义，
        //    必须放行；只有"全新购买"才拦。旧订单与已购学员的学习入口不受影响。
        CoursePurchaseGuard.requirePurchasable(course);
        // ④ 待支付订单拦截：同一课程不允许堆积多笔待支付单
        Long unpaidOrders = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .eq(Order::getCourseId, course.getId())
                .eq(Order::getStatus, STATUS_UNPAID));
        if (unpaidOrders != null && unpaidOrders > 0) {
            throw new BizIllegalException("该课程已有待支付订单，请先在「我的订单」完成支付或取消后再加入学习");
        }

        Order order = new Order();
        order.setId(SnowflakeIdGenerator.getInstance().nextId());
        order.setOrderNo("T" + order.getId());
        order.setUserId(userId);
        order.setCourseId(course.getId());
        order.setCourseName(course.getName());
        order.setCoursePrice(0L);
        order.setTotalFee(0L);
        order.setDeduction(0L);
        order.setStatus(STATUS_PAID);
        order.setPayTime(LocalDateTime.now());
        try {
            orderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            // 并发兜底：同一用户同一课程仅允许一条已支付订单。
            // 对方请求已建单成功，这里补一次开课，保证课表同样可见 ——
            // 否则会出现"提示已拥有，但我的课表里没有该课程"。
            syncEnroll(userId, course.getId(), course.getName());
            throw new BizIllegalException("该课程已在你的课程中心，可直接开始学习");
        }

        OrderDetail detail = new OrderDetail();
        detail.setOrderId(order.getId());
        detail.setCourseId(course.getId());
        detail.setName(course.getName());
        detail.setPrice(0L);
        orderDetailMapper.insert(detail);

        // 支付成功事件（与付费链路对齐）：zx-learning 消费后写课表，学习中心立即可见
        OrderPaidMsg paidMsg = new OrderPaidMsg();
        paidMsg.setOrderId(order.getId());
        paidMsg.setOrderNo(order.getOrderNo());
        paidMsg.setUserId(userId);
        paidMsg.setCourseId(course.getId());
        paidMsg.setCourseName(course.getName());
        paidMsg.setAmount(0L);
        paidMsg.setPayType(0);
        paidMsg.setPayNo("FREE-" + order.getOrderNo());
        orderMsgService.enqueue(order.getId(), "orderPaid",
                MqTopics.TOPIC_ORDER_PAID, MqTopics.Tags.ORDER_PAID, paidMsg);

        // 免费课直接到账：异步确认课程名额（锁定转销量）
        QuotaMsg quotaMsg = new QuotaMsg();
        quotaMsg.setOrderId(order.getId());
        quotaMsg.setCourseId(course.getId());
        quotaMsg.setUserId(userId);
        orderMsgService.enqueue(order.getId(), "quotaConfirm",
                MqTopics.TOPIC_COURSE_QUOTA, MqTopics.Tags.QUOTA_CONFIRM, quotaMsg);
        // 同步开课：让课表立即出现该课程，课程界面/我的课表两端「已拥有」实时一致
        enrollAfterCommit(userId, course.getId(), course.getName());
        return order.getId();
    }

    /**
     * 事务提交后同步开课（写入我的课表）。
     * <p>
     * 为什么必须放在提交之后：开课是对学习服务的远程写操作，若在事务内调用，
     * 一旦本地事务后续回滚（例如消息入队失败），就会出现"交易侧没成交、课表里却有课"
     * 的脏数据。注册 afterCommit 回调把远程调用挪到提交之后，做到本地先落地、远端再同步。
     * <p>
     * 没有活动事务时（例如被非事务方法调用）直接执行。
     */
    private void enrollAfterCommit(Long userId, Long courseId, String courseName) {
        if (userId == null || courseId == null) {
            return;
        }
        TxSupport.afterCommit(() -> syncEnroll(userId, courseId, courseName));
    }

    /**
     * 同步开课（容错）：失败只记日志，不阻断支付主链路——
     * 本地消息表里的「订单支付成功」事件仍会由 MQ 消费方补一次，最终一致。
     */
    private void syncEnroll(Long userId, Long courseId, String courseName) {
        try {
            learningClient.enrollLesson(new LessonEnrollDTO(userId, courseId, courseName));
            log.info("同步开课成功：userId={}, courseId={}", userId, courseId);
        } catch (Exception e) {
            log.warn("同步开课失败（MQ 事件将兜底重试）：userId={}, courseId={}, {}",
                    userId, courseId, e.getMessage());
        }
    }

    /**
     * 支付回调：将待支付订单置为已支付。返回是否真正发生状态迁移（用于触发放事件）。
     * <p>
     * 使用条件更新（status=待支付 才允许迁移）保证并发回调下的原子性：
     * 重复回调/并发回调仅有一个事务能真正迁移状态，其余幂等返回 false，
     * 避免重复触发后续权益发放事件。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean markPaid(Long orderId, Integer payType) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BadRequestException("订单不存在");
        }
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, STATUS_UNPAID)
                .set(Order::getStatus, STATUS_PAID)
                .set(Order::getPayType, payType)
                .set(Order::getPayTime, LocalDateTime.now()));
        if (rows > 0) {
            log.info("支付回调成功：orderId={}", orderId);
            // 同步开课：支付成功即写入课表，避免"课程界面已拥有、我的课表还没有"的割裂
            enrollAfterCommit(order.getUserId(), order.getCourseId(), order.getCourseName());
            return true;
        }
        // 条件更新未命中：重查判定是"已支付的重复回调"（幂等返回）还是"状态不允许"
        Order latest = orderMapper.selectById(orderId);
        if (latest != null && Integer.valueOf(STATUS_PAID).equals(latest.getStatus())) {
            log.info("订单 {} 已支付，幂等返回", orderId);
            return false;
        }
        throw new BizIllegalException("订单状态不允许支付");
    }

    /**
     * 超时关单（延迟消息 / 定时兜底扫描 / 学员手动取消 / 列表对账统一入口）。
     * <p>
     * 幂等策略：<b>条件更新</b>（仅 status=待支付 才能迁移到已关闭）本身就是幂等的，
     * 并发/重复调用时只有一个事务能把 rows 改为 1，其余返回 0 并跳过补偿。
     * <p>
     * 历史缺陷（已修复）：旧实现在入口处用 {@code consume_record("order:close:{id}")}
     * 做永久幂等标记，导致「第一次关单因竞态 rows=0 → 流水却已落库」之后，
     * 该订单<b>永远无法再关单</b>（前端倒计时归零仍显示待支付）。
     * 关单是可重入的对账动作，不应使用一次性消费流水，故移除该标记。
     */
    @Transactional(rollbackFor = Exception.class)
    public void closeExpired(Long orderId) {
        if (orderId == null) {
            return;
        }
        Order order = orderMapper.selectById(orderId);
        if (order == null || !Integer.valueOf(STATUS_UNPAID).equals(order.getStatus())) {
            return;
        }
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, STATUS_UNPAID)
                .set(Order::getStatus, STATUS_CLOSED)
                .set(Order::getUpdateTime, LocalDateTime.now()));
        // 竞态防护：预检与条件更新之间状态可能被支付回调改变（rows=0），
        // 此时严禁退券/释放名额，否则已支付订单会被错误补偿
        if (rows == 0) {
            log.info("订单 {} 关单条件未命中（状态已变更），跳过补偿", orderId);
            return;
        }
        log.info("订单超时关单：orderId={}", orderId);
        // 使用了优惠券：退回库存（Redis 恢复 + 异步落库 + 券状态回写）
        if (order.getCouponId() != null && order.getCouponId() > 0) {
            CouponMsg refundMsg = new CouponMsg();
            refundMsg.setAction(MqTopics.Tags.COUPON_REFUND);
            refundMsg.setUserId(order.getUserId());
            refundMsg.setCouponId(order.getCouponId());
            // 不设 userCouponId：trade_order 只存券模板 id，退回时按 orderId 反查用户券（见 markRefunded）
            refundMsg.setOrderId(orderId);
            refundMsg.setAmount(order.getDeduction());
            orderMsgService.enqueue(orderId, "couponRefund",
                    MqTopics.TOPIC_COUPON_USE, MqTopics.Tags.COUPON_REFUND, refundMsg);
            tradeCouponService.restoreStock(order.getCouponId(), order.getUserId(), 1);
            // 同步把券状态退回"未使用"：关单后券列表立即恢复可用，避免券被订单永久占住
            couponStatusSyncer.syncRefundedAfterCommit(orderId);
        }
        // 释放课程名额（关单补偿）
        QuotaMsg quotaMsg = new QuotaMsg();
        quotaMsg.setOrderId(orderId);
        quotaMsg.setCourseId(order.getCourseId());
        quotaMsg.setUserId(order.getUserId());
        orderMsgService.enqueue(orderId, "quotaRelease",
                MqTopics.TOPIC_COURSE_QUOTA, MqTopics.Tags.QUOTA_RELEASE, quotaMsg);
    }

    public Order getById(Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BadRequestException("订单不存在");
        }
        return order;
    }

    /**
     * 我的订单详情（对齐前端 OrderVO 契约：金额重命名 + 状态映射 + 明细组装）。
     * 仅允许查看本人订单，防止越权读取他人订单。
     */
    public OrderVO getMyOrderVO(Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BadRequestException("订单不存在");
        }
        if (!Objects.equals(order.getUserId(), UserContext.getUserId())) {
            throw new ForbiddenException("无权查看他人订单");
        }
        return toVO(order);
    }

    /**
     * 申请退款：仅"已支付"订单可进入"退款中"。
     */
    @Transactional(rollbackFor = Exception.class)
    public void applyRefund(Long orderId) {
        Order order = requireOrder(orderId);
        if (!Integer.valueOf(STATUS_PAID).equals(order.getStatus())) {
            throw new BizIllegalException("仅已支付订单可申请退款");
        }
        order.setStatus(STATUS_REFUNDING);
        orderMapper.updateById(order);
        log.info("订单进入退款中：orderId={}", orderId);
    }

    /**
     * 直接退款成功：仅"已支付"订单可由退款服务在满足自动退款条件时调用。
     * <p>
     * 使用条件更新保证并发安全（重复申请/与管理员审批并发时只有一次能真正迁移状态）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void markRefunded(Long orderId) {
        requireOrder(orderId);
        int rows = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .eq(Order::getStatus, STATUS_PAID)
                .set(Order::getStatus, STATUS_REFUNDED)
                .set(Order::getUpdateTime, LocalDateTime.now()));
        if (rows == 0) {
            throw new BizIllegalException("订单状态已变更，无法直接退款");
        }
        log.info("订单直接退款成功：orderId={}", orderId);
    }

    /**
     * 退款审批：通过则流转至"已退款"，拒绝则回退为"已支付"。
     */
    @Transactional(rollbackFor = Exception.class)
    public void confirmRefund(Long orderId, boolean approved) {
        Order order = requireOrder(orderId);
        if (!Integer.valueOf(STATUS_REFUNDING).equals(order.getStatus())) {
            throw new BizIllegalException("订单未处于退款中，无法审批");
        }
        order.setStatus(approved ? STATUS_REFUNDED : STATUS_PAID);
        orderMapper.updateById(order);
        log.info("退款审批完成：orderId={}, approved={}", orderId, approved);
    }

    private Order requireOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BadRequestException("订单不存在");
        }
        return order;
    }

    public List<Order> page(Long userId) {
        return orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .orderByDesc(Order::getCreateTime));
    }

    /**
     * 我的订单分页查询（对齐前端 PageDTO&lt;OrderVO&gt; 契约）。
     * <ul>
     *   <li>查询前先做一次「待支付订单对账」：把已超时或课程已拥有的僵尸待支付单关闭，
     *       保证"已拥有的课程不出现在待支付列表"、"倒计时归零后订单确实变成已关闭"；</li>
     *   <li>过滤学员侧已删除的订单（user_deleted=1），管理端不受影响；</li>
     *   <li>分页 + 可选状态过滤（1 待支付 2 已支付 3 已关闭 5 退款中 6 已退款）；</li>
     *   <li>组装明细 details（单课程订单一条），批量 Feign 查询课程封面补全。</li>
     * </ul>
     */
    public PageDTO<OrderVO> pageQuery(PageQuery query, Integer frontStatus) {
        Long userId = UserContext.getUserId();
        // 对账：关闭僵尸待支付单（不影响已支付/退款流程）
        reconcileUnpaidOrders(userId);
        Integer dbStatus = frontStatus == null ? null : toDbStatus(frontStatus);
        Page<Order> page = orderMapper.selectPage(query.toMpPage("create_time", false),
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getUserId, userId)
                        .eq(Order::getUserDeleted, 0)
                        .eq(dbStatus != null, Order::getStatus, dbStatus));
        return PageDTO.of(page, this::toVO);
    }

    /**
     * 待支付订单对账（列表/详情读取时惰性触发，与 OrderTimeoutJob 定时扫描互为兜底）。
     * <p>
     * 满足任一条件即视为"僵尸待支付单"，就地关单：
     * <ol>
     *   <li><b>已超时</b>：下单时间早于 {@code tx.order.expire-minutes}（默认 15 分钟），
     *       对应前端"剩余 MM:SS 自动关闭"倒计时归零；</li>
     *   <li><b>课程已拥有</b>：课程已进入课表（含学习进度 0）或存在已支付订单，
     *       此时订单再支付会造成重复购买，必须关闭而不是留在待支付列表。</li>
     * </ol>
     * 逐笔调用 {@link #closeExpired(Long)}，失败仅记日志，不阻断列表查询。
     *
     * @return 本次实际关闭的订单数
     */
    @Transactional(rollbackFor = Exception.class)
    public int reconcileUnpaidOrders(Long userId) {
        if (userId == null) {
            return 0;
        }
        List<Order> pendings;
        try {
            pendings = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                    .eq(Order::getUserId, userId)
                    .eq(Order::getStatus, STATUS_UNPAID)
                    .eq(Order::getUserDeleted, 0)
                    .orderByAsc(Order::getId)
                    .last("LIMIT 200"));
        } catch (Exception e) {
            log.warn("待支付订单对账查询失败，跳过本次对账：userId={}, {}", userId, e.getMessage());
            return 0;
        }
        if (pendings.isEmpty()) {
            return 0;
        }
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(Math.max(0, expireMinutes));
        Set<Long> owned = ownedCourseIds(userId);
        int closed = 0;
        for (Order o : pendings) {
            boolean expired = o.getCreateTime() == null || o.getCreateTime().isBefore(deadline);
            boolean ownedCourse = o.getCourseId() != null && owned.contains(o.getCourseId());
            if (!expired && !ownedCourse) {
                continue;
            }
            try {
                orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                        .eq(Order::getId, o.getId())
                        .eq(Order::getStatus, STATUS_UNPAID)
                        .set(Order::getStatus, STATUS_CLOSED)
                        .set(Order::getUpdateTime, LocalDateTime.now()));
                closed++;
                log.info("待支付订单对账关单：orderId={}, reason={}", o.getId(),
                        ownedCourse ? "课程已拥有" : "已超时未支付");
            } catch (Exception e) {
                log.warn("对账关单失败：orderId={}, {}", o.getId(), e.getMessage());
            }
        }
        if (closed > 0) {
            log.info("待支付订单对账完成：userId={}, 关闭 {} 笔", userId, closed);
        }
        return closed;
    }

    /**
     * 「已拥有」课程 id 集合：课程中心（课表）∪ 已支付订单。
     * 与 {@link #boughtCourseIds()} 同口径，但可指定用户 id（供后台对账使用，
     * 不依赖 {@link UserContext}）。
     */
    private Set<Long> ownedCourseIds(Long userId) {
        Set<Long> owned = new HashSet<>();
        try {
            owned.addAll(orderMapper.selectList(new LambdaQueryWrapper<Order>()
                            .select(Order::getCourseId)
                            .eq(Order::getUserId, userId)
                            .eq(Order::getStatus, STATUS_PAID))
                    .stream()
                    .map(Order::getCourseId)
                    .filter(Objects::nonNull)
                    .toList());
        } catch (Exception e) {
            log.warn("查询已支付订单课程失败：userId={}, {}", userId, e.getMessage());
        }
        owned.addAll(lessonCourseIds(userId));
        return owned;
    }

    /**
     * 学员删除订单（软删除：仅置 user_deleted=1，管理端仍保留记录）。
     * <p>
     * 允许删除的状态：已支付(2)/已关闭(3)/已退款(6) —— 即已终结、不再需要用户操作的订单；
     * 待支付(1)/退款中(5) 属于进行中状态，必须先完成支付、取消或等待退款审核，不允许直接删除，
     * 否则会掩盖待处理的交易与退款流程。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteMyOrder(Long orderId) {
        Order order = requireOrder(orderId);
        if (!Objects.equals(order.getUserId(), UserContext.getUserId())) {
            throw new ForbiddenException("无权删除他人订单");
        }
        int front = toFrontStatus(order.getStatus() == null ? STATUS_CLOSED : order.getStatus());
        if (front == 1 || front == 5) {
            throw new BizIllegalException(front == 1
                    ? "待支付订单请先支付或取消后再删除"
                    : "退款处理中的订单暂不支持删除，请等待审核完成");
        }
        if (Integer.valueOf(1).equals(order.getUserDeleted())) {
            return;
        }
        orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .set(Order::getUserDeleted, 1)
                .set(Order::getUpdateTime, LocalDateTime.now()));
        log.info("学员删除订单（软删除，管理端仍保留）：orderId={}, userId={}", orderId, order.getUserId());
    }

    /** 数据库状态 → 前端契约状态（1 待支付 2 已支付 3 已关闭 5 退款中 6 已退款） */
    private int toFrontStatus(int dbStatus) {
        return switch (dbStatus) {
            case STATUS_UNPAID -> 1;
            case STATUS_PAID -> 2;
            case STATUS_CLOSED -> 3;
            case STATUS_REFUNDING -> 5;
            case STATUS_REFUNDED -> 6;
            default -> 3;
        };
    }

    /** 前端契约状态 → 数据库状态（null/未知返回 null 表示不过滤） */
    private Integer toDbStatus(Integer frontStatus) {
        if (frontStatus == null) {
            return null;
        }
        return switch (frontStatus) {
            case 1 -> STATUS_UNPAID;
            case 2 -> STATUS_PAID;
            case 3 -> STATUS_CLOSED;
            case 5 -> STATUS_REFUNDING;
            case 6 -> STATUS_REFUNDED;
            default -> null;
        };
    }

    /** Order PO → 前端契约 VO：金额字段重命名 + 状态映射 + 明细组装 */
    public OrderVO toVO(Order order) {
        OrderVO vo = new OrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setUserId(order.getUserId());
        vo.setTotalAmount(order.getCoursePrice() == null ? 0L : order.getCoursePrice());
        vo.setRealAmount(order.getTotalFee() == null ? 0L : order.getTotalFee());
        vo.setDiscountAmount(order.getDeduction() == null ? 0L : order.getDeduction());
        vo.setCouponId(order.getCouponId());
        vo.setStatus(toFrontStatus(order.getStatus() == null ? STATUS_CLOSED : order.getStatus()));
        vo.setCreateTime(order.getCreateTime());
        vo.setPayTime(order.getPayTime());
        vo.setDetails(buildDetails(order));
        return vo;
    }

    /** 组装订单明细（单课程订单一条），并批量补全课程封面 */
    private List<OrderVO.OrderItemVO> buildDetails(Order order) {
        List<OrderDetail> details = orderDetailMapper.selectList(
                new LambdaQueryWrapper<OrderDetail>().eq(OrderDetail::getOrderId, order.getId()));
        List<OrderVO.OrderItemVO> items = new ArrayList<>();
        for (OrderDetail d : details) {
            OrderVO.OrderItemVO item = new OrderVO.OrderItemVO();
            item.setId(d.getId());
            item.setOrderId(d.getOrderId());
            item.setCourseId(d.getCourseId());
            item.setCourseName(d.getName());
            item.setPrice(d.getPrice());
            items.add(item);
        }
        fillCoverUrl(items);
        return items;
    }

    /** 批量查询课程信息补全封面（Feign 失败降级为无封面，不阻断列表渲染） */
    private void fillCoverUrl(List<OrderVO.OrderItemVO> items) {
        List<Long> courseIds = items.stream()
                .map(OrderVO.OrderItemVO::getCourseId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (courseIds.isEmpty()) {
            return;
        }
        try {
            Map<Long, String> coverMap = courseClient.queryCourseSimpleInfoList(courseIds).stream()
                    .filter(c -> c.getId() != null)
                    .collect(Collectors.toMap(CourseSimpleInfoDTO::getId,
                            c -> c.getCoverUrl() == null ? "" : c.getCoverUrl(), (a, b) -> a));
            items.forEach(item -> item.setCoverUrl(coverMap.getOrDefault(item.getCourseId(), "")));
        } catch (Exception e) {
            log.warn("查询课程封面失败，降级为无封面：{}", e.getMessage());
        }
    }

    /**
     * 报名人数（内部 Feign 接口）
     */
    public Integer countEnrollNum(Long courseId) {
        return Math.toIntExact(orderMapper.selectCount(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getCourseId, courseId)
                        .eq(Order::getStatus, STATUS_PAID)));
    }

    /**
     * 重复购买校验（下单/免费开课前置拦截）：
     * <ul>
     *   <li>课程中心（课表）已存在该课程 → 拒绝：权威口径，含学习进度 0 与退款后未清课表的课程；</li>
     *   <li>存在「已支付」订单 → 拒绝：避免重复订单与重复扣费（课表可能尚未异步开课的窗口兜底）；</li>
     *   <li>存在「待支付」订单 → 引导先支付或取消，避免同一课程堆积多笔待支付单。</li>
     * </ul>
     * 并发兜底由 trade_order.paid_key 生成列唯一索引（uk_user_course_paid）保证。
     * 课表查询失败时 fail-open：跳过课表校验，仅保留订单校验（不阻断正常购买链路）。
     * <p>
     * <b>自愈</b>：若已支付订单存在但课表没有该课程（支付成功时学习服务不可用，或本地消息表里
     * 的 orderPaid 事件重试耗尽被放弃），这里会先补开课再提示。否则用户会被永久卡死 ——
     * 再买提示"已拥有"，而"我的课表"里始终找不到该课程。
     */
    private void assertNotDuplicateOrder(Long userId, Long courseId, String courseName) {
        if (lessonCourseIds(userId).contains(courseId)) {
            throw new BizIllegalException("该课程已在你的课程中心，请勿重复购买，可直接开始学习");
        }
        Long paid = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .eq(Order::getCourseId, courseId)
                .eq(Order::getStatus, STATUS_PAID));
        if (paid != null && paid > 0) {
            // 已支付但课表缺失 → 补开课（幂等），让两端重新一致，用户可直接进入学习
            int healed = healLessonIfMissing(userId, courseId, courseName);
            log.info("已支付订单存在，重复下单拦截：userId={}, courseId={}, 补开课={}",
                    userId, courseId, healed > 0);
            throw new BizIllegalException("您已购买该课程，已为你开通学习，可直接开始学习");
        }
        Long unpaid = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .eq(Order::getCourseId, courseId)
                .eq(Order::getStatus, STATUS_UNPAID));
        if (unpaid > 0) {
            throw new BizIllegalException("该课程已有待支付订单，请先在「我的订单」完成支付或取消后再下单");
        }
    }

    /**
     * 自愈：该用户该课程已有已支付订单但课表尚未开通时，补写课表项。
     * <p>
     * 产生该脏状态的两条真实路径（本项目已实际出现过）：
     * <ol>
     *   <li>支付成功时学习服务不可用，同步开课（Feign）失败；</li>
     *   <li>本地消息表里的 {@code orderPaid} 事件因 MQ 不可用重试耗尽被放弃
     *       （order_msg.status=3、retry_count 达到 max_retry）。</li>
     * </ol>
     * 两者都让"订单已支付、课表为空"。补开课是幂等的（唯一索引 + 复活逻辑），
     * 调用失败也不抛异常，避免影响主链路。
     *
     * @return 实际执行补开课返回 1，无需补开课返回 0
     */
    private int healLessonIfMissing(Long userId, Long courseId, String courseName) {
        if (userId == null || courseId == null) {
            return 0;
        }
        if (lessonCourseIds(userId).contains(courseId)) {
            return 0;
        }
        syncEnroll(userId, courseId, courseName);
        return 1;
    }

    /** 该用户该课程最新一笔已支付订单 id（无则返回 null） */
    private Long latestPaidOrderId(Long userId, Long courseId) {
        Order paid = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .eq(Order::getCourseId, courseId)
                .eq(Order::getStatus, STATUS_PAID)
                .orderByDesc(Order::getId)
                .last("LIMIT 1"));
        return paid == null ? null : paid.getId();
    }

    /** 查询用户课程中心（课表）课程 id 集合；Feign 失败降级为空集合（fail-open） */
    private Set<Long> lessonCourseIds(Long userId) {
        try {
            List<Long> ids = learningClient.listLessonCourseIds(userId);
            return ids == null ? Set.of() : new HashSet<>(ids);
        } catch (Exception e) {
            log.warn("查询课程中心失败，降级为仅订单校验：userId={}, {}", userId, e.getMessage());
            return Set.of();
        }
    }

    /**
     * 当前用户是否已购买课程（内部 Feign 接口）
     */
    public Boolean checkCourseBought(Long courseId) {
        Long count = orderMapper.selectCount(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, UserContext.getUserId())
                .eq(Order::getCourseId, courseId)
                .eq(Order::getStatus, STATUS_PAID));
        return count > 0;
    }

    /**
     * 当前学员已拥有课程 id 集合（去重）。
     * <p>
     * 「已拥有」口径 = 课程中心（课表）存在的课程 ∪ 已支付订单课程：
     * <ul>
     *   <li>课表为准（权威）：含学习进度 0 的课程、退款后未清课表的课程；</li>
     *   <li>已支付订单兜底：覆盖支付成功但开课事件尚未消费完成的窗口期。</li>
     * </ul>
     * 供课程列表/详情展示「已拥有」标识并禁用购买入口、结算页前置过滤。
     * 课表查询失败时降级为仅返回订单已购集合。
     */
    public List<Long> boughtCourseIds() {
        Long userId = UserContext.getUserId();
        Set<Long> owned = new HashSet<>(orderMapper.selectList(new LambdaQueryWrapper<Order>()
                        .select(Order::getCourseId)
                        .eq(Order::getUserId, userId)
                        .eq(Order::getStatus, STATUS_PAID))
                .stream()
                .map(Order::getCourseId)
                .filter(Objects::nonNull)
                .toList());
        owned.addAll(lessonCourseIds(userId));
        return owned.stream().sorted().toList();
    }

    /**
     * 死信补偿：修复「订单已支付，但 zx-learning 没有收到 orderPaid 事件」导致的课表缺失。
     * <p>
     * 触发场景（本项目已实际发生过）：支付时学习服务不可用 → 同步开课（Feign）失败，
     * 同时本地消息表里的 orderPaid 事件在 MQ 不可用期间反复重试，超过 max_retry 后转死信
     * （{@code order_msg.status=3}）。事件永远不会再投递，课表就永久缺失。
     * <p>
     * 只处理「PAID 消息转死信」的订单，语义精确：不会误伤学员主动移除课表等正常业务。
     * 补开课幂等（唯一索引 + 复活逻辑），补偿成功后把死信标记为已消费，避免重复补偿。
     *
     * @param limit 单轮最多处理的订单数
     * @return 实际补开课的门数
     */
    public int reconcileDeadPaidOrders(int limit) {
        List<Long> orderIds;
        try {
            orderIds = orderMsgService.deadPaidOrderIds(limit);
        } catch (Exception e) {
            log.warn("查询支付成功死信失败：{}", e.getMessage());
            return 0;
        }
        if (orderIds == null || orderIds.isEmpty()) {
            return 0;
        }
        int healed = 0;
        for (Long orderId : orderIds) {
            try {
                Order order = orderMapper.selectById(orderId);
                if (order == null || !Objects.equals(order.getStatus(), STATUS_PAID)) {
                    // 订单不存在或未支付：无可补偿内容，直接标记消费避免每轮重复扫描
                    orderMsgService.markPaidMsgConsumed(orderId);
                    continue;
                }
                if (healLessonIfMissing(order.getUserId(), order.getCourseId(), order.getCourseName()) > 0) {
                    healed++;
                    log.info("课表对账补偿：补开课 orderId={}, userId={}, courseId={}",
                            orderId, order.getUserId(), order.getCourseId());
                }
                orderMsgService.markPaidMsgConsumed(orderId);
            } catch (Exception e) {
                log.warn("课表对账补偿失败：orderId={}, {}", orderId, e.getMessage());
            }
        }
        return healed;
    }

    /**
     * 死信重放（运维手动 / 定时任务同源逻辑）。
     * <p>
     * 覆盖 {@code orderPaid} 之外的死信（{@code LOCK}/{@code CONFIRM}/{@code USE}/{@code REFUND}）：
     * 这些 tag 原先没有任何补偿通道，投递失败即永久残留（全量测试发现 3 条历史死信长期存在）。
     * 重放只是把状态退回待投递，消费端幂等保证不会重复生效。
     *
     * @param limit 单次重放上限
     * @return 实际退回待投递的消息条数
     */
    public int replayDeadMsgs(int limit) {
        return orderMsgService.replayDeadMsgs(limit);
    }

    /** 死信快照（按 tag 计数），供看板/巡检观测；正常应为空。 */
    public java.util.Map<String, Integer> deadMsgSummary() {
        return orderMsgService.deadMsgSummary();
    }

    /**
     * 交易统计聚合（内部 Feign 接口，供管理端看板消费）：
     * 已支付订单量 / 销售额、近 7 日趋势（按支付日聚合，缺数日期补 0）、热门课程 TOP5。
     */
    public TradeStatsDTO dashboardStats() {
        // 仅统计确已支付的订单（status=PAID 且支付时间非空，防御异常数据）
        List<Order> paid = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                        .eq(Order::getStatus, STATUS_PAID)).stream()
                .filter(o -> o.getPayTime() != null)
                .toList();
        long totalSales = paid.stream()
                .mapToLong(o -> o.getTotalFee() == null ? 0L : o.getTotalFee())
                .sum();
        List<TradeStatsDTO.CourseCount> hot = paid.stream()
                .filter(o -> o.getCourseId() != null)
                .collect(Collectors.groupingBy(Order::getCourseId, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> new TradeStatsDTO.CourseCount(e.getKey(), e.getValue()))
                .toList();
        return new TradeStatsDTO((long) paid.size(), totalSales, buildDailyTrend(paid), hot);
    }

    /** 近 7 日订单趋势：按支付日聚合订单量与销售额，缺失日期补 0 */
    private List<TradeStatsDTO.TrendPoint> buildDailyTrend(List<Order> paid) {
        LocalDate today = LocalDate.now();
        Map<String, long[]> byDate = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            byDate.put(today.minusDays(i).toString(), new long[]{0, 0});
        }
        for (Order o : paid) {
            if (o.getPayTime() == null) {
                continue;
            }
            long[] slot = byDate.get(o.getPayTime().toLocalDate().toString());
            if (slot != null) {
                slot[0]++;
                slot[1] += o.getTotalFee() == null ? 0L : o.getTotalFee();
            }
        }
        return byDate.entrySet().stream()
                .map(e -> new TradeStatsDTO.TrendPoint(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .toList();
    }

}