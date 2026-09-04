package com.zhixing.trade.service;

import com.zhixing.api.dto.trade.OrderPaidMsg;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.mq.MqTopics;
import com.zhixing.trade.domain.dto.OrderFormDTO;
import com.zhixing.trade.domain.po.Order;
import com.zhixing.trade.domain.po.PayRecord;
import com.zhixing.trade.mapper.PayRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付回调单测：验签 / 流水幂等 / 更新订单并发布支付成功事件
 */
@ExtendWith(MockitoExtension.class)
class PayServiceTest {

    @Mock
    private PayRecordMapper payRecordMapper;
    @Mock
    private OrderService orderService;
    @Mock
    private OrderMsgService orderMsgService;
    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private PayService payService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(payService, "callbackSecret", "test-secret");
        // 让模拟事务直接执行回调（lenient：部分纯验签用例不使用）
        lenient().when(transactionTemplate.execute(any()))
                .thenAnswer(inv -> {
                    TransactionCallback<?> cb = inv.getArgument(0);
                    return cb.doInTransaction(null);
                });
    }

    private OrderFormDTO callback(Long orderId, Long amount, String payNo, String sign) {
        OrderFormDTO form = new OrderFormDTO();
        form.setId(orderId);
        form.setTotalFee(amount);
        form.setPayNo(payNo);
        form.setSign(sign);
        form.setPayType(2);
        return form;
    }

    @Test
    void verifySignPassesWithCorrectSign() {
        String sign = payService.buildSign(1L, 100L, "P1");
        assertDoesNotThrow(() -> payService.verifySign(callback(1L, 100L, "P1", sign)));
    }

    @Test
    void verifySignRejectsTamperedSign() {
        OrderFormDTO form = callback(1L, 100L, "P1", "bad-sign");
        assertThrows(BadRequestException.class, () -> payService.verifySign(form));
    }

    @Test
    void payCallbackPublishesOrderPaidEventOnFirstProcess() {
        when(payRecordMapper.insert(any(PayRecord.class))).thenReturn(1);
        when(orderService.markPaid(1L, 2)).thenReturn(true);
        Order order = new Order();
        order.setId(1L);
        order.setOrderNo("T1");
        order.setUserId(1L);
        order.setCourseId(5L);
        order.setTotalFee(100L);
        when(orderService.getById(1L)).thenReturn(order);

        payService.payCallback(callback(1L, 100L, "P1", payService.buildSign(1L, 100L, "P1")));

        verify(orderService).markPaid(1L, 2);
        verify(orderMsgService).enqueue(eq(1L), eq("orderPaid"),
                eq(MqTopics.TOPIC_ORDER_PAID), eq(MqTopics.Tags.ORDER_PAID), any(OrderPaidMsg.class));
        // 名额确认事件同事务登记
        verify(orderMsgService).enqueue(eq(1L), eq("quotaConfirm"),
                eq(MqTopics.TOPIC_COURSE_QUOTA), eq(MqTopics.Tags.QUOTA_CONFIRM), any());
    }

    @Test
    void payCallbackIdempotentWhenSamePayNoRepeat() {
        when(payRecordMapper.insert(any(PayRecord.class)))
                .thenThrow(new DuplicateKeyException("duplicate pay_no"));

        payService.payCallback(callback(1L, 100L, "P1", payService.buildSign(1L, 100L, "P1")));

        // 重复流水：不更新订单、不发事件
        verify(orderService, never()).markPaid(anyLong(), any());
        verify(orderMsgService, never()).enqueue(anyLong(), any(), any(), any(), any());
    }

}