package com.zhixing.trade.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.trade.service.CouponUseRecordService;
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
 * 优惠券消息处理器分发单测：按 tag 正确路由到核销/退回，未知 tag 安全丢弃。
 */
@ExtendWith(MockitoExtension.class)
class CouponUseHandlerTest {

    @Mock
    private CouponUseRecordService couponUseRecordService;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private CouponUseHandler handler;

    private MessageExt message(String tag) {
        MessageExt ext = new MessageExt();
        ext.setTopic("zx_coupon_use");
        ext.setTags(tag);
        ext.setBody("{}".getBytes(StandardCharsets.UTF_8));
        return ext;
    }

    @Test
    void routesUseTagToSubmitUse() throws Exception {
        when(objectMapper.readValue(anyString(), eq(CouponMsg.class))).thenReturn(new CouponMsg());

        handler.handle(message("USE"));

        verify(couponUseRecordService).submitUse(any(CouponMsg.class));
        verify(couponUseRecordService, never()).submitRefund(any());
    }

    @Test
    void routesRefundTagToSubmitRefund() throws Exception {
        when(objectMapper.readValue(anyString(), eq(CouponMsg.class))).thenReturn(new CouponMsg());

        handler.handle(message("REFUND"));

        verify(couponUseRecordService).submitRefund(any(CouponMsg.class));
        verify(couponUseRecordService, never()).submitUse(any());
    }

    @Test
    void dropsUnknownTagSafely() throws Exception {
        handler.handle(message("UNKNOWN"));

        verify(couponUseRecordService, never()).submitUse(any());
        verify(couponUseRecordService, never()).submitRefund(any());
    }

    @Test
    void supportsCouponTopicOnly() {
        org.junit.jupiter.api.Assertions.assertTrue(handler.supports("zx_coupon_use", "USE"));
        org.junit.jupiter.api.Assertions.assertFalse(handler.supports("zx_order_paid", "USE"));
    }
}
