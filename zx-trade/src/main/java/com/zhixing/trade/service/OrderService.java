package com.zhixing.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhixing.api.client.course.CourseClient;
import com.zhixing.api.dto.course.CourseSimpleInfoDTO;
import com.zhixing.api.dto.trade.QuotaMsg;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.exceptions.BizIllegalException;
import com.zhixing.common.mq.MqTopics;
import com.zhixing.common.mq.RocketMQTemplate;
import com.zhixing.common.utils.SnowflakeIdGenerator;
import com.zhixing.common.utils.UserContext;
import com.zhixing.trade.domain.dto.OrderFormDTO;
import com.zhixing.trade.domain.po.Order;
import com.zhixing.trade.domain.po.OrderDetail;
import com.zhixing.trade.mapper.OrderDetailMapper;
import com.zhixing.trade.mapper.OrderMapper;
import com.zhixing.trade.mq.CouponMsg;
import com.zhixing.trade.mq.OrderCloseMsg;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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
    private final OrderMsgService orderMsgService;
    private final TradeCouponService tradeCouponService;
    private final RocketMQTemplate rocketMQTemplate;
    private final IdempotencyGuard idempotencyGuard;

    /** 超时关单延迟级别（对应 broker messageDelayLevel，默认 15 分钟） */
    @Value("${rocketmq.timeout-delay-level:15}")
    private int timeoutDelayLevel;

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
     */
    @Transactional(rollbackFor = Exception.class)
    public Long placeOrder(OrderFormDTO form) {
        if (form == null || form.getCourseId() == null) {
            throw new BadRequestException("订单内容不能为空");
        }
        CourseSimpleInfoDTO course = courseClient.queryCourseInfoById(form.getCourseId());
        if (course == null) {
            throw new BadRequestException("课程不存在");
        }
        Long userId = UserContext.getUserId();
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
        orderMapper.insert(order);

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
     * 0 元下单（免费课程直接支付到账）
     */
    @Transactional(rollbackFor = Exception.class)
    public Long freeCourse(Long courseId) {
        CourseSimpleInfoDTO course = courseClient.queryCourseInfoById(courseId);
        if (course == null) {
            throw new BadRequestException("课程不存在");
        }
        Long userId = UserContext.getUserId();
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
        orderMapper.insert(order);

        OrderDetail detail = new OrderDetail();
        detail.setOrderId(order.getId());
        detail.setCourseId(course.getId());
        detail.setName(course.getName());
        detail.setPrice(0L);
        orderDetailMapper.insert(detail);

        // 免费课直接到账：异步确认课程名额（锁定转销量）
        QuotaMsg quotaMsg = new QuotaMsg();
        quotaMsg.setOrderId(order.getId());
        quotaMsg.setCourseId(course.getId());
        quotaMsg.setUserId(userId);
        orderMsgService.enqueue(order.getId(), "quotaConfirm",
                MqTopics.TOPIC_COURSE_QUOTA, MqTopics.Tags.QUOTA_CONFIRM, quotaMsg);
        return order.getId();
    }

    /**
     * 支付回调：将待支付订单置为已支付。返回是否真正发生状态迁移（用于触发放事件）。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean markPaid(Long orderId, Integer payType) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BadRequestException("订单不存在");
        }
        if (order.getStatus() != null && order.getStatus() == STATUS_PAID) {
            log.info("订单 {} 已支付，幂等返回", orderId);
            return false;
        }
        order.setStatus(STATUS_PAID);
        order.setPayType(payType);
        order.setPayTime(LocalDateTime.now());
        orderMapper.updateById(order);
        log.info("支付回调成功：orderId={}", orderId);
        return true;
    }

    /**
     * 超时关单（延迟消息 / 定时兜底扫描统一入口）。
     * 消费流水幂等 + 条件更新双保险，避免重复关单或误关已支付订单。
     */
    @Transactional(rollbackFor = Exception.class)
    public void closeExpired(Long orderId) {
        if (orderId == null) {
            return;
        }
        String consumeKey = "order:close:" + orderId;
        if (!idempotencyGuard.tryConsume(consumeKey, MqTopics.TOPIC_ORDER_TIMEOUT,
                MqTopics.Tags.ORDER_CLOSE)) {
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
        if (rows > 0) {
            log.info("订单超时关单：orderId={}", orderId);
        }
        // 使用了优惠券：退回库存（Redis 恢复 + 异步落库）
        if (order.getCouponId() != null && order.getCouponId() > 0) {
            CouponMsg refundMsg = new CouponMsg();
            refundMsg.setAction(MqTopics.Tags.COUPON_REFUND);
            refundMsg.setUserId(order.getUserId());
            refundMsg.setCouponId(order.getCouponId());
            refundMsg.setUserCouponId(order.getCouponId());
            refundMsg.setOrderId(orderId);
            refundMsg.setAmount(order.getDeduction());
            orderMsgService.enqueue(orderId, "couponRefund",
                    MqTopics.TOPIC_COUPON_USE, MqTopics.Tags.COUPON_REFUND, refundMsg);
            tradeCouponService.restoreStock(order.getCouponId(), order.getUserId(), 1);
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
     * 报名人数（内部 Feign 接口）
     */
    public Integer countEnrollNum(Long courseId) {
        return Math.toIntExact(orderMapper.selectCount(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getCourseId, courseId)
                        .eq(Order::getStatus, STATUS_PAID)));
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

}