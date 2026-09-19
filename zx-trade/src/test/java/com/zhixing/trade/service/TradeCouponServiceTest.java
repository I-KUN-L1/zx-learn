package com.zhixing.trade.service;

import com.zhixing.common.exceptions.BizIllegalException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TradeCouponService 单测：聚焦「优惠券余量 key 惰性预热」修复——
 * 历史上 coupon:stock:int 从未被预热，带优惠券下单在 Lua 预扣时被误判"库存不足"。
 * 本测试验证：缺失时兜底预热、已有时不重复预热、以及 -1/-2 的错误映射与库存恢复。
 */
@ExtendWith(MockitoExtension.class)
class TradeCouponServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    private TradeCouponService service;

    @BeforeEach
    void setUp() {
        service = new TradeCouponService(redisTemplate);
        // 手工触发初始化，加载 coupon_deduct / coupon_restore Lua 脚本
        service.init();
        // @Value 不注入，测试固定取默认兜底余量
        ReflectionTestUtils.setField(service, "couponStockDefault", 1000000L);
    }

    @Test
    void deductStockWarmsMissingStockKeyBeforeDeduct() {
        String stockKey = "coupon:stock:1";
        String usedKey = "coupon:used:1:1";
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(redisTemplate.hasKey(stockKey)).thenReturn(false);
        when(valueOps.setIfAbsent(stockKey, "1000000")).thenReturn(true);
        // Lua 预扣成功
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L);

        assertDoesNotThrow(() -> service.deductStock(1L, 1L, 1, 1));

        verify(valueOps).setIfAbsent(stockKey, "1000000");
        verify(redisTemplate, never()).hasKey(usedKey);
        // 确认扣减脚本被真正执行（keys = [stockKey, usedKey]）
        verify(redisTemplate).execute(any(RedisScript.class),
                eq(java.util.List.of(stockKey, usedKey)), any(Object[].class));
    }

    @Test
    void deductStockDoesNotRewarmWhenKeyExists() {
        String stockKey = "coupon:stock:2";
        when(redisTemplate.hasKey(stockKey)).thenReturn(true);
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L);

        assertDoesNotThrow(() -> service.deductStock(2L, 5L, 1, 1));

        verify(valueOps, never()).setIfAbsent(any(), any());
    }

    @Test
    void deductStockMapsOverLimitToBizError() {
        String stockKey = "coupon:stock:3";
        when(redisTemplate.hasKey(stockKey)).thenReturn(true);
        // -2 = 超过该用户限用数量
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(-2L);

        BizIllegalException ex = assertThrows(BizIllegalException.class,
                () -> service.deductStock(3L, 1L, 1, 1));

        // 提示须说明真实原因（这张券用过），不能误导成"领不了"
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("已使用过"));
    }

    @Test
    void deductStockMapsOutOfStockToBizError() {
        String stockKey = "coupon:stock:4";
        when(redisTemplate.hasKey(stockKey)).thenReturn(true);
        // -1 = 库存不足：即便预热过，真实余量见底仍应报错
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(-1L);

        BizIllegalException ex = assertThrows(BizIllegalException.class,
                () -> service.deductStock(4L, 1L, 1, 1));

        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("库存不足"));
    }

    @Test
    void restoreStockDoesNotThrow() {
        // 关单退回库存：即便 Redis 执行失败也不抛异常、不影响主流程
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RuntimeException("redis down"));
        assertDoesNotThrow(() -> service.restoreStock(5L, 1L, 1));
    }

    @Test
    void deductStockSkipsWhenCouponIdNull() {
        assertDoesNotThrow(() -> service.deductStock(null, 1L, 1, 1));
        verify(redisTemplate, never()).hasKey(any());
        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }
}