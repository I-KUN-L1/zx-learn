package com.zhixing.trade.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.trade.service.OrderService;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 超时关单消息处理器单测：解析消息并触发统一关单入口。
 */
@ExtendWith(MockitoExtension.class)
class OrderTimeoutHandlerTest {

    @Mock
    private OrderService orderService;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OrderTimeoutHandler handler;

    @Test
    void handleClosesOrderByIdFromMessage() throws Exception {
        OrderCloseMsg closeMsg = new OrderCloseMsg();
        closeMsg.setOrderId(100L);
        closeMsg.setOrderNo("T100");
        when(objectMapper.readValue(anyString(), eq(OrderCloseMsg.class))).thenReturn(closeMsg);

        MessageExt ext = new MessageExt();
        ext.setTopic("zx_order_timeout");
        ext.setTags("CLOSE");
        ext.setBody("{}".getBytes(StandardCharsets.UTF_8));

        handler.handle(ext);

        verify(orderService).closeExpired(100L);
    }

    @Test
    void supportsOnlyTimeoutTopicWithCloseTag() {
        org.junit.jupiter.api.Assertions.assertTrue(handler.supports("zx_order_timeout", "CLOSE"));
        org.junit.jupiter.api.Assertions.assertFalse(handler.supports("zx_order_timeout", "PAID"));
        org.junit.jupiter.api.Assertions.assertFalse(handler.supports("zx_order_paid", "CLOSE"));
    }
}
