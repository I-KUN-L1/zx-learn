package com.zhixing.promotion.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhixing.common.exceptions.BizIllegalException;
import com.zhixing.promotion.domain.po.Coupon;
import com.zhixing.promotion.domain.po.UserCoupon;
import com.zhixing.promotion.mapper.CouponMapper;
import com.zhixing.promotion.mapper.UserCouponMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户优惠券服务单测：领取资格校验（仅进行中可领）、限领数量校验、
 * 兑换码一次性核销（唯一索引兜底）、"未使用->已使用/已过期"状态机、
 * 秒杀落库（券码 + 发放数条件更新 + 唯一索引幂等）。
 */
@ExtendWith(MockitoExtension.class)
class UserCouponServiceTest {

    static {
        // 纯 Mock 环境下 LambdaUpdateWrapper 需要实体列元数据
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Coupon.class);
    }

    @Mock
    private UserCouponMapper userCouponMapper;
    @Mock
    private CouponMapper couponMapper;
    @Mock
    private CouponService couponService;

    @InjectMocks
    private UserCouponService service;

    private Coupon coupon(int status, int issuedNum, int totalNum) {
        Coupon c = new Coupon();
        c.setId(1L);
        c.setName("满减券");
        c.setDiscountAmount(100L);
        c.setThresholdAmount(500L);
        c.setStatus(status);
        c.setIssuedNum(issuedNum);
        c.setTotalNum(totalNum);
        return c;
    }

    @Test
    void claimSucceedsWhenOngoingAndInStock() {
        Coupon c = coupon(CouponService.STATE_ONGOING, 0, 10);
        when(couponService.getById(1L)).thenReturn(c);

        service.claim(100L, 1L);

        verify(userCouponMapper).insert(any(UserCoupon.class));
        verify(couponMapper).updateById(c);
        assertEquals(1, c.getIssuedNum());
    }

    @Test
    void claimRejectedWhenNotOngoing() {
        when(couponService.getById(1L)).thenReturn(coupon(CouponService.STATE_NOT_STARTED, 0, 10));

        assertThrows(BizIllegalException.class, () -> service.claim(100L, 1L));
    }

    @Test
    void claimRejectedWhenSoldOut() {
        when(couponService.getById(1L)).thenReturn(coupon(CouponService.STATE_ONGOING, 10, 10));

        assertThrows(BizIllegalException.class, () -> service.claim(100L, 1L));
    }

    @Test
    void claimRejectedOnDuplicateByUniqueIndex() {
        when(couponService.getById(1L)).thenReturn(coupon(CouponService.STATE_ONGOING, 0, 10));
        doThrow(new DuplicateKeyException("uk_user_coupon"))
                .when(userCouponMapper).insert(any(UserCoupon.class));

        assertThrows(BizIllegalException.class, () -> service.claim(100L, 1L));
    }

    @Test
    void redeemByCodeSucceedsOnce() {
        when(couponService.requireByExchangeCode("CODE123"))
                .thenReturn(coupon(CouponService.STATE_ONGOING, 0, 10));

        service.redeemByCode(100L, 1L, "CODE123");

        verify(userCouponMapper).insert(any(UserCoupon.class));
        verify(couponMapper).updateById(any(Coupon.class));
    }

    @Test
    void redeemByCodeRejectedWhenCodeDoNotMatchCoupon() {
        Coupon c = coupon(CouponService.STATE_ONGOING, 0, 10);
        c.setId(2L);
        when(couponService.requireByExchangeCode("CODE123")).thenReturn(c);

        assertThrows(BizIllegalException.class, () -> service.redeemByCode(100L, 1L, "CODE123"));
    }

    @Test
    void useTransitionsUnusedToUsed() {
        UserCoupon uc = new UserCoupon();
        uc.setId(5L);
        uc.setStatus(0);
        when(userCouponMapper.selectById(5L)).thenReturn(uc);

        service.use(5L, 999L);

        assertEquals(1, uc.getStatus());
        assertEquals(999L, uc.getOrderId());
        assertNotNull(uc.getUseTime());
        verify(userCouponMapper).updateById(uc);
    }

    @Test
    void useRejectedWhenAlreadyUsed() {
        UserCoupon uc = new UserCoupon();
        uc.setId(5L);
        uc.setStatus(1);
        when(userCouponMapper.selectById(5L)).thenReturn(uc);

        assertThrows(BizIllegalException.class, () -> service.use(5L, 999L));
    }

    @Test
    void claimSeckillInsertsWithCouponCodeAndConditionalIssueUpdate() {
        Coupon c = coupon(CouponService.STATE_ONGOING, 3, 10);

        boolean ok = service.claimSeckill(100L, c, "SK123");

        assertTrue(ok);
        verify(userCouponMapper).insert(argThat((UserCoupon uc) ->
                "SK123".equals(uc.getCouponCode())
                        && Long.valueOf(100L).equals(uc.getUserId())
                        && Integer.valueOf(0).equals(uc.getStatus())));
        verify(couponMapper).update(isNull(), argThat(w -> {
            // getSqlSet 返回列名级 SET 片段，断言条件更新的是发放数
            String set = ((com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<?>) w).getSqlSet();
            return set != null && set.contains("issued_num");
        }));
    }

    @Test
    void claimSeckillDuplicateMessageReturnsFalse() {
        doThrow(new DuplicateKeyException("uk_user_coupon"))
                .when(userCouponMapper).insert(any(UserCoupon.class));

        boolean ok = service.claimSeckill(100L, coupon(CouponService.STATE_ONGOING, 3, 10), "SK123");

        assertFalse(ok);
        verify(couponMapper, never()).update(isNull(), any());
    }
}