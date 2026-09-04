package com.zhixing.learning.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.api.dto.trade.OrderPaidMsg;
import com.zhixing.learning.service.IdempotencyGuard;
import com.zhixing.learning.service.LessonService;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付成功开课消费者单测：首次消费开课、重复投递幂等跳过。
 */
@ExtendWith(MockitoExtension.class)
class OrderPaidHandlerTest {

    @Mock
    private LessonService lessonService;
    @Mock
    private IdempotencyGuard idempotencyGuard;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OrderPaidHandler handler;

    private MessageExt message() throws Exception {
        OrderPaidMsg msg = new OrderPaidMsg();
        msg.setOrderId(100L);
        msg.setUserId(1L);
        msg.setCourseId(5L);
        msg.setCourseName("Spring 实战");
        when(objectMapper.readValue(anyString(), eq(OrderPaidMsg.class))).thenReturn(msg);
        MessageExt ext = new MessageExt();
        ext.setTopic("zx_order_paid");
        ext.setTags("PAID");
        ext.setBody("{}".getBytes(StandardCharsets.UTF_8));
        return ext;
    }

    @Test
    void handleEnrollsOnFirstConsume() throws Exception {
        when(idempotencyGuard.tryConsume(anyString(), anyString(), anyString())).thenReturn(true);

        handler.handle(message());

        verify(lessonService).enroll(1L, 5L, "Spring 实战");
    }

    @Test
    void handleSkipsWhenAlreadyConsumed() throws Exception {
        when(idempotencyGuard.tryConsume(anyString(), anyString(), anyString())).thenReturn(false);

        handler.handle(message());

        verify(lessonService, never()).enroll(any(), any(), any());
    }

    @Test
    void supportsOnlyOrderPaidTopicAndTag() {
        org.junit.jupiter.api.Assertions.assertTrue(
                handler.supports("zx_order_paid", "PAID"));
        org.junit.jupiter.api.Assertions.assertFalse(
                handler.supports("zx_order_paid", "CLOSE"));
        org.junit.jupiter.api.Assertions.assertFalse(
                handler.supports("zx_coupon_use", "PAID"));
    }
}
