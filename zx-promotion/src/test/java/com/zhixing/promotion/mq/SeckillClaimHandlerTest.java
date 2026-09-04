package com.zhixing.promotion.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.promotion.domain.po.Coupon;
import com.zhixing.promotion.mapper.CouponMapper;
import com.zhixing.promotion.service.IdempotencyGuard;
import com.zhixing.promotion.service.SeckillService;
import com.zhixing.promotion.service.UserCouponService;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 秒杀领取消费端单测：确定性失败短路、消费流水幂等跳过、
 * 券码生成落库、重复消息写 REPEAT 结果。
 */
@ExtendWith(MockitoExtension.class)
class SeckillClaimHandlerTest {

    @Mock
    private CouponMapper couponMapper;
    @Mock
    private UserCouponService userCouponService;
    @Mock
    private IdempotencyGuard idempotencyGuard;
    @Mock
    private SeckillService seckillService;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private SeckillClaimHandler handler;

    private MessageExt message() {
        MessageExt ext = new MessageExt();
        ext.setBody("{\"couponId\":1,\"userId\":100}".getBytes(StandardCharsets.UTF_8));
        return ext;
    }

    private void stubParse() {
        when(seckillService.parseClaimMsg(anyString())).thenAnswer((Answer<SeckillClaimMsg>) inv -> {
            SeckillClaimMsg m = new SeckillClaimMsg();
            m.setCouponId(1L);
            m.setUserId(100L);
            return m;
        });
    }

    @Test
    void routesSeckillTopicAndTag() {
        assertEquals(Set.of("zx_seckill_claim"), handler.subscribeTopics());
        assertTrue(handler.supports("zx_seckill_claim", "CLAIM"));
        assertFalse(handler.supports("zx_seckill_claim", "OTHER"));
    }

    @Test
    void missingCouponWritesFailedWithoutIdempotency() throws Exception {
        stubParse();
        when(couponMapper.selectById(1L)).thenReturn(null);

        handler.handle(message());

        verify(seckillService).writeResult(1L, 100L,
                SeckillService.STATUS_FAILED + ":优惠券不存在");
        verifyNoInteractions(idempotencyGuard, userCouponService);
    }

    @Test
    void firstConsumeInsertsWithCouponCode() throws Exception {
        stubParse();
        Coupon coupon = new Coupon();
        coupon.setId(1L);
        when(couponMapper.selectById(1L)).thenReturn(coupon);
        when(idempotencyGuard.tryConsume(eq("seckill:claim:1:100"), anyString(), anyString()))
                .thenReturn(true);
        when(userCouponService.claimSeckill(eq(100L), any(Coupon.class), contains("SK")))
                .thenReturn(true);

        handler.handle(message());

        verify(userCouponService).claimSeckill(eq(100L), any(Coupon.class), contains("SK"));
        verify(seckillService).writeResult(eq(1L), eq(100L), contains("SK"));
    }

    @Test
    void duplicateClaimWritesRepeat() throws Exception {
        stubParse();
        when(couponMapper.selectById(1L)).thenReturn(new Coupon());
        when(idempotencyGuard.tryConsume(anyString(), anyString(), anyString())).thenReturn(true);
        // 幂等第一层：uk_user_coupon 唯一索引冲突 → claimSeckill 返回 false
        when(userCouponService.claimSeckill(anyLong(), any(), anyString())).thenReturn(false);

        handler.handle(message());

        verify(seckillService).writeResult(1L, 100L, SeckillService.STATUS_REPEAT);
    }

    @Test
    void consumedRecordSkipsBusiness() throws Exception {
        stubParse();
        when(couponMapper.selectById(1L)).thenReturn(new Coupon());
        when(idempotencyGuard.tryConsume(anyString(), anyString(), anyString())).thenReturn(false);

        handler.handle(message());

        verifyNoInteractions(userCouponService);
        verify(seckillService, org.mockito.Mockito.never())
                .writeResult(anyLong(), anyLong(), anyString());
    }
}
