package com.zhixing.promotion.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.mq.RocketMQTemplate;
import com.zhixing.promotion.domain.po.Coupon;
import com.zhixing.promotion.domain.po.UserCoupon;
import com.zhixing.promotion.mapper.UserCouponMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 秒杀服务单测：Lua 预扣结果分发（成功/售罄/重复/未预热）、MQ 投递失败降级、
 * 轮询结果解析（Redis 结果键优先 + DB 兜底）、对账差集补发。
 */
@ExtendWith(MockitoExtension.class)
class SeckillServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private RocketMQTemplate rocketMQTemplate;
    @Mock
    private CouponService couponService;
    @Mock
    private UserCouponMapper userCouponMapper;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private SetOperations<String, String> setOps;

    @InjectMocks
    private SeckillService service;

    @BeforeAll
    static void initTableInfo() {
        // 纯 Mock 环境下 LambdaQueryWrapper 需要实体列元数据
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserCoupon.class);
    }

    @BeforeEach
    void setUp() {
        service.init();
        ReflectionTestUtils.setField(service, "claimLimit", 1);
    }

    private void luaReturns(long code) {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(code);
    }

    @Test
    void claimSuccessReturnsQueuingAndSendsMsg() {
        luaReturns(1L);
        when(rocketMQTemplate.send(any(), any(), any())).thenReturn(true);

        Map<String, Object> result = service.claim(100L, 1L);

        assertEquals(SeckillService.STATUS_QUEUING, result.get("status"));
        verify(rocketMQTemplate).send(any(), any(), any());
    }

    @Test
    void claimStillQueuingWhenMqDown() {
        // MQ 不可用：不阻塞用户，返回排队中，由对账任务补偿
        luaReturns(1L);
        when(rocketMQTemplate.send(any(), any(), any())).thenReturn(false);

        Map<String, Object> result = service.claim(100L, 1L);

        assertEquals(SeckillService.STATUS_QUEUING, result.get("status"));
    }

    @Test
    void claimMapsLuaRejectCodes() {
        luaReturns(-1L);
        assertEquals(SeckillService.STATUS_SOLD_OUT, service.claim(100L, 1L).get("status"));

        luaReturns(-2L);
        assertEquals(SeckillService.STATUS_REPEAT, service.claim(100L, 1L).get("status"));

        luaReturns(-3L);
        assertEquals(SeckillService.STATUS_NOT_READY, service.claim(100L, 1L).get("status"));

        // redis 异常返回 null → 未预热
        when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(null);
        assertEquals(SeckillService.STATUS_NOT_READY, service.claim(100L, 1L).get("status"));
        verifyNoInteractions(rocketMQTemplate);
    }

    @Test
    void resultReadsRedisFirst() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("coupon:seckill:result:1:100")).thenReturn("SK123");

        Map<String, Object> result = service.result(100L, 1L);

        assertEquals(SeckillService.STATUS_SUCCESS, result.get("status"));
        assertEquals("SK123", result.get("couponCode"));
    }

    @Test
    void resultParsesFailedAndRepeat() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("coupon:seckill:result:1:100")).thenReturn("FAILED:优惠券不存在");
        Map<String, Object> failed = service.result(100L, 1L);
        assertEquals(SeckillService.STATUS_FAILED, failed.get("status"));
        assertEquals("优惠券不存在", failed.get("reason"));

        when(valueOps.get("coupon:seckill:result:1:100")).thenReturn(SeckillService.STATUS_REPEAT);
        assertEquals(SeckillService.STATUS_REPEAT, service.result(100L, 1L).get("status"));
    }

    @Test
    void resultFallsBackToDb() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("coupon:seckill:result:1:100")).thenReturn(null);

        UserCoupon uc = new UserCoupon();
        uc.setUserId(100L);
        uc.setCouponId(1L);
        uc.setCouponCode("SK999");
        when(userCouponMapper.selectOne(any())).thenReturn(uc);
        Map<String, Object> hit = service.result(100L, 1L);
        assertEquals(SeckillService.STATUS_SUCCESS, hit.get("status"));
        assertEquals("SK999", hit.get("couponCode"));

        when(userCouponMapper.selectOne(any())).thenReturn(null);
        assertEquals(SeckillService.STATUS_QUEUING, service.result(100L, 1L).get("status"));
    }

    @Test
    void reconcileResendsMissingDbRecords() {
        when(redisTemplate.opsForSet()).thenReturn(setOps);
        when(setOps.members("coupon:seckill:users:1")).thenReturn(Set.of("100", "200"));
        UserCoupon db = new UserCoupon();
        db.setUserId(100L);
        when(userCouponMapper.selectList(any())).thenReturn(List.of(db));
        when(rocketMQTemplate.send(any(), any(), any())).thenReturn(true);

        int compensated = service.reconcile(1L);

        assertEquals(1, compensated);
    }

    @Test
    void writeResultSetsTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service.writeResult(1L, 100L, "SK123");
        verify(valueOps).set("coupon:seckill:result:1:100", "SK123", Duration.ofHours(24));
    }

    @Test
    void warmupWritesRemainingStock() {
        Coupon coupon = new Coupon();
        coupon.setId(1L);
        coupon.setTotalNum(10);
        coupon.setIssuedNum(3);
        when(couponService.getById(1L)).thenReturn(coupon);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(valueOps.setIfAbsent("coupon:seckill:stock:1", "7")).thenReturn(true);

        service.warmup(1L);

        verify(valueOps).setIfAbsent("coupon:seckill:stock:1", "7");
    }
}
