package com.zhixing.trade.service;

import com.zhixing.api.client.course.CourseClient;
import com.zhixing.api.dto.course.CourseSimpleInfoDTO;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.exceptions.BizIllegalException;
import com.zhixing.common.mq.MqTopics;
import com.zhixing.common.mq.RocketMQTemplate;
import com.zhixing.common.utils.UserContext;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhixing.trade.domain.dto.OrderFormDTO;
import com.zhixing.trade.domain.po.Order;
import com.zhixing.trade.domain.po.OrderDetail;
import com.zhixing.trade.mapper.OrderDetailMapper;
import com.zhixing.trade.mapper.OrderMapper;
import com.zhixing.trade.mq.CouponMsg;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 下单链路单测：金额一致性 / 优惠券预扣与异步核销队列 / 名额锁定事件 / 支付回调幂等 / 超时关单补偿
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderDetailMapper orderDetailMapper;
    @Mock
    private CourseClient courseClient;
    @Mock
    private OrderMsgService orderMsgService;
    @Mock
    private TradeCouponService tradeCouponService;
    @Mock
    private RocketMQTemplate rocketMQTemplate;
    @Mock
    private IdempotencyGuard idempotencyGuard;

    @InjectMocks
    private OrderService service;

    @BeforeEach
    void setUp() {
        UserContext.setUser(1L);
        ReflectionTestUtils.setField(service, "timeoutDelayLevel", 15);
        // 纯 Mock 单测无 Spring 上下文，手动初始化 MyBatis-Plus 元数据，
        // 否则 LambdaUpdateWrapper.set() 会因缺少 TableInfo 缓存而报错
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Order.class);
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    private CourseSimpleInfoDTO course(Long price) {
        CourseSimpleInfoDTO c = new CourseSimpleInfoDTO();
        c.setId(5L);
        c.setName("Spring 实战");
        c.setPrice(price);
        return c;
    }

    @Test
    void placeOrderSucceedsWithConsistentAmount() {
        when(courseClient.queryCourseInfoById(5L)).thenReturn(course(100L));
        OrderFormDTO form = new OrderFormDTO();
        form.setCourseId(5L);
        form.setTotalFee(100L);

        Long id = service.placeOrder(form);

        assertNotNull(id);
        verify(orderMapper).insert(any(Order.class));
        verify(orderDetailMapper).insert(any(OrderDetail.class));
        // 无优惠券：不触发库存预扣，不登记核销消息
        verify(tradeCouponService, never()).deductStock(anyLong(), anyLong(), anyInt(), anyInt());
        verify(orderMsgService, never()).enqueue(anyLong(), eq("couponUse"), any(), any(), any());
        // 下单即登记"锁定课程名额"本地消息
        verify(orderMsgService).enqueue(eq(id), eq("quotaLock"),
                eq(MqTopics.TOPIC_COURSE_QUOTA), eq(MqTopics.Tags.QUOTA_LOCK), any());
        // 下单即投递超时关单延迟消息（延迟级别 15 = 15 分钟）
        verify(rocketMQTemplate).send(eq(MqTopics.TOPIC_ORDER_TIMEOUT),
                eq(MqTopics.Tags.ORDER_CLOSE), any(), eq(15));
    }

    @Test
    void placeOrderWithCouponDeductStockAndEnqueueUse() {
        when(courseClient.queryCourseInfoById(5L)).thenReturn(course(100L));
        OrderFormDTO form = new OrderFormDTO();
        form.setCourseId(5L);
        form.setCouponId(9L);
        form.setUserCouponId(88L);
        form.setTotalFee(70L); // 优惠 30

        Long id = service.placeOrder(form);

        verify(tradeCouponService).deductStock(eq(9L), eq(1L), anyInt(), anyInt());
        verify(orderMsgService).enqueue(eq(id), eq("couponUse"),
                eq(MqTopics.TOPIC_COUPON_USE), eq(MqTopics.Tags.COUPON_USE), any(CouponMsg.class));
    }

    @Test
    void placeOrderRejectsInconsistentAmount() {
        when(courseClient.queryCourseInfoById(5L)).thenReturn(course(100L));
        OrderFormDTO form = new OrderFormDTO();
        form.setCourseId(5L);
        form.setTotalFee(80L); // 篡改金额：80 != 100

        assertThrows(BizIllegalException.class, () -> service.placeOrder(form));
        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    void placeOrderRejectsUnknownCourse() {
        when(courseClient.queryCourseInfoById(999L)).thenReturn(null);
        OrderFormDTO form = new OrderFormDTO();
        form.setCourseId(999L);

        assertThrows(BadRequestException.class, () -> service.placeOrder(form));
    }

    @Test
    void markPaidTransitionsAndPublishesAfter() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus(0);
        when(orderMapper.selectById(1L)).thenReturn(order);

        boolean changed = service.markPaid(1L, 2);

        assertTrue(changed);
        assertEquals(1, order.getStatus());
        verify(orderMapper).updateById(order);
    }

    @Test
    void markPaidIdempotentWhenAlreadyPaid() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus(1);
        when(orderMapper.selectById(1L)).thenReturn(order);

        boolean changed = service.markPaid(1L, 2);

        assertFalse(changed);
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    @Test
    void closeExpiredCancelsAndRestoresCouponAndQuota() {
        when(idempotencyGuard.tryConsume(any(), any(), any())).thenReturn(true);
        Order order = new Order();
        order.setId(1L);
        order.setUserId(1L);
        order.setCourseId(5L);
        order.setStatus(0);
        order.setCouponId(9L);
        order.setDeduction(30L);
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.update(any(), any())).thenReturn(1);

        service.closeExpired(1L);

        verify(orderMsgService).enqueue(eq(1L), eq("couponRefund"),
                eq(MqTopics.TOPIC_COUPON_USE), eq(MqTopics.Tags.COUPON_REFUND), any(CouponMsg.class));
        verify(tradeCouponService).restoreStock(eq(9L), eq(1L), anyInt());
        // 关单补偿：释放课程名额
        verify(orderMsgService).enqueue(eq(1L), eq("quotaRelease"),
                eq(MqTopics.TOPIC_COURSE_QUOTA), eq(MqTopics.Tags.QUOTA_RELEASE), any());
    }

    @Test
    void closeExpiredSkipsAlreadyPaidOrder() {
        when(idempotencyGuard.tryConsume(any(), any(), any())).thenReturn(true);
        Order order = new Order();
        order.setId(1L);
        order.setStatus(1);
        when(orderMapper.selectById(1L)).thenReturn(order);

        service.closeExpired(1L);

        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    void closeExpiredIdempotentWhenConsumeKeyExists() {
        when(idempotencyGuard.tryConsume(any(), any(), any())).thenReturn(false);

        service.closeExpired(1L);

        verify(orderMapper, never()).selectById(anyLong());
        verify(orderMapper, never()).update(any(), any());
    }

    @Test
    void checkCourseBoughtReturnsTrueWhenPaidExist() {
        when(orderMapper.selectCount(any())).thenReturn(1L);
        assertTrue(service.checkCourseBought(5L));
    }

    // ==================== 本地消息表：同一事务多条 outbox ====================

    @Test
    void placeOrderWithCouponEnqueuesBothMessagesInSameTransaction() {
        // 使用优惠券下单：优惠券核销消息与名额锁定消息在同一事务内先后登记（Outbox 同事务）
        when(courseClient.queryCourseInfoById(5L)).thenReturn(course(100L));
        OrderFormDTO form = new OrderFormDTO();
        form.setCourseId(5L);
        form.setCouponId(9L);
        form.setUserCouponId(88L);
        form.setTotalFee(70L);

        Long id = service.placeOrder(form);

        org.mockito.InOrder inOrder = inOrder(orderMsgService);
        inOrder.verify(orderMsgService).enqueue(eq(id), eq("couponUse"),
                eq(MqTopics.TOPIC_COUPON_USE), eq(MqTopics.Tags.COUPON_USE), any(CouponMsg.class));
        inOrder.verify(orderMsgService).enqueue(eq(id), eq("quotaLock"),
                eq(MqTopics.TOPIC_COURSE_QUOTA), eq(MqTopics.Tags.QUOTA_LOCK), any());
        // 订单与消息同事务：订单落库发生在消息登记之前
        inOrder.verify(orderMsgService, never()).enqueue(anyLong(), eq("quotaRelease"), any(), any(), any());
    }

    @Test
    void freeCourseEnqueuesQuotaConfirmWithoutTimeoutMessage() {
        // 免费课直接到账：只登记名额确认消息，不发超时关单延迟消息
        when(courseClient.queryCourseInfoById(6L)).thenReturn(course(0L));

        Long id = service.freeCourse(6L);

        assertNotNull(id);
        verify(orderMsgService).enqueue(eq(id), eq("quotaConfirm"),
                eq(MqTopics.TOPIC_COURSE_QUOTA), eq(MqTopics.Tags.QUOTA_CONFIRM), any());
        verify(rocketMQTemplate, never()).send(any(), any(), any(), anyInt());
    }

    // ==================== 超时关单：并发竞态与补偿 ====================

    @Test
    void closeExpiredStillEnqueuesReleaseWhenConditionalUpdateMisses() {
        // 条件更新未命中（如已被并发关单）：补偿消息仍登记，
        // 由 order_msg.uk_biz_key 唯一索引挡住重复，保证补偿不丢
        when(idempotencyGuard.tryConsume(any(), any(), any())).thenReturn(true);
        Order order = new Order();
        order.setId(1L);
        order.setUserId(1L);
        order.setCourseId(5L);
        order.setStatus(0);
        when(orderMapper.selectById(1L)).thenReturn(order);
        when(orderMapper.update(any(), any())).thenReturn(0);

        service.closeExpired(1L);

        verify(orderMsgService).enqueue(eq(1L), eq("quotaRelease"),
                eq(MqTopics.TOPIC_COURSE_QUOTA), eq(MqTopics.Tags.QUOTA_RELEASE), any());
        // 无优惠券：不登记退券消息、不恢复库存
        verify(orderMsgService, never()).enqueue(anyLong(), eq("couponRefund"), any(), any(), any());
        verify(tradeCouponService, never()).restoreStock(anyLong(), anyLong(), anyInt());
    }
}
