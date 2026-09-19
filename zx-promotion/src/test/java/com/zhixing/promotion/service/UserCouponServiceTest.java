package com.zhixing.promotion.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixing.common.domain.PageDTO;
import com.zhixing.common.domain.PageQuery;
import com.zhixing.common.exceptions.BizIllegalException;
import com.zhixing.promotion.domain.po.Coupon;
import com.zhixing.promotion.domain.po.UserCoupon;
import com.zhixing.promotion.domain.vo.UserCouponVO;
import com.zhixing.promotion.mapper.CouponMapper;
import com.zhixing.promotion.mapper.UserCouponMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserCoupon.class);
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

    // ============ 券状态同步（交易服务核销 / 退回后回写，幂等） ============

    @Test
    void markUsedTransitionsUnusedToUsed() {
        UserCoupon uc = new UserCoupon();
        uc.setId(5L);
        uc.setUserId(100L);
        uc.setCouponId(9L);
        uc.setStatus(0);
        when(userCouponMapper.selectById(5L)).thenReturn(uc);

        assertTrue(service.markUsed(5L, 100L, 9L, 999L));

        assertEquals(1, uc.getStatus());
        assertEquals(999L, uc.getOrderId());
        assertNotNull(uc.getUseTime());
        verify(userCouponMapper).updateById(uc);
    }

    @Test
    void markUsedIsIdempotentWhenAlreadyUsed() {
        // 同步 Feign 重试 / MQ 重复消费 / 对账重放：必须静默返回，不得抛错
        UserCoupon uc = new UserCoupon();
        uc.setId(5L);
        uc.setStatus(1);
        uc.setOrderId(999L);
        when(userCouponMapper.selectById(5L)).thenReturn(uc);

        assertFalse(service.markUsed(5L, 100L, 9L, 999L));

        verify(userCouponMapper, never()).updateById(any(UserCoupon.class));
    }

    @Test
    void markUsedFallsBackToUserAndCouponWhenRowIdMissing() {
        // 前端未回传 userCouponId 时按 (userId, couponId) 兜底定位
        UserCoupon uc = new UserCoupon();
        uc.setId(5L);
        uc.setStatus(0);
        when(userCouponMapper.selectOne(any())).thenReturn(uc);

        assertTrue(service.markUsed(null, 100L, 9L, 999L));

        assertEquals(1, uc.getStatus());
        verify(userCouponMapper).updateById(uc);
    }

    @Test
    void markUsedToleratesMissingCoupon() {
        when(userCouponMapper.selectById(5L)).thenReturn(null);

        assertFalse(service.markUsed(5L, null, null, 999L));

        verify(userCouponMapper, never()).updateById(any(UserCoupon.class));
    }

    @Test
    void markRefundedRestoresCouponByOrderId() {
        // 关单退回：按订单号定位（未过期 → 未使用；已过期 → 已过期），两条更新各命中 1 行
        when(userCouponMapper.update(isNull(), any())).thenReturn(1);

        assertEquals(2, service.markRefunded(999L));

        verify(userCouponMapper, times(2)).update(isNull(), any());
    }

    @Test
    void markRefundedIsIdempotentWhenNothingMatches() {
        // 重复退回 / 券已还原：命中 0 行即幂等返回
        when(userCouponMapper.update(isNull(), any())).thenReturn(0);

        assertEquals(0, service.markRefunded(999L));
    }

    @Test
    void listStatusParamUsesSameSemanticsAsReturnedField() {
        // 列表筛选入参必须与返回体 status 字段同语义：1未使用/2已使用/3已过期 → 存储 0/1/2。
        // 历史实现直接透传参数（?status=1 查出的是"已使用"），两套语义会筛错。
        assertEquals(0, service.toStoredStatus(1));
        assertEquals(1, service.toStoredStatus(2));
        assertEquals(2, service.toStoredStatus(3));
        // null / 越界值 → 不筛选（而不是写成永不匹配的条件）
        assertNull(service.toStoredStatus(null));
        assertNull(service.toStoredStatus(0));
        assertNull(service.toStoredStatus(99));
    }

    @Test
    void pageVosByUserConvertsStatusAndMapsPage() {
        // 分页接口必须复用列表接口的状态语义（前端 1未使用/2已使用/3已过期 → 存储 0/1/2）。
        // 断言分两层：
        //   1) 查询条件里带的是**转换后**的存储状态（前端传 2 → 应查 status=1），
        //      若实现把前端值直接当存储值用，条件会变成 2 而断言失败；
        //   2) 返回体 status 用前端语义（存储 1 → 返回 2）。
        Page<UserCoupon> mpPage = new Page<>(1, 10);
        UserCoupon uc = new UserCoupon();
        uc.setId(9L);
        uc.setUserId(100L);
        uc.setCouponId(1L);
        uc.setCouponName("满减券");
        uc.setDiscountAmount(100L);
        uc.setThresholdAmount(500L);
        uc.setStatus(1); // 存储语义：1 = 已使用
        mpPage.setRecords(List.of(uc));
        mpPage.setTotal(1L);

        doReturn(mpPage).when(userCouponMapper).selectPage(any(), any());

        PageDTO<UserCouponVO> dto = service.pageVosByUser(new PageQuery(), 100L, 2); // 前端 2 = 已使用

        assertEquals(1L, dto.getTotal());
        assertEquals(1, dto.getList().size());
        assertEquals(2, dto.getList().get(0).getStatus(), "返回体 status 应为前端语义（存储 1 → 2）");

        // 抓查询条件里的实际参数值，验证确实是「userId=100 且 status=1（存储值）」
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<UserCoupon>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(userCouponMapper).selectPage(any(), captor.capture());
        Set<String> params = paramValues(captor.getValue());
        assertEquals(Set.of("100", "1"), params,
                "分页查询条件应为 userId=100 且状态已转换为存储值 1（直接把前端 2 当存储值即失败）");
    }

    @Test
    void pageVosByUserIgnoresInvalidStatusInsteadOfNeverMatching() {
        // 非法 status（越界/null）必须"不筛选"，而不是退化成 status = null 这种永不匹配的条件。
        Page<UserCoupon> mpPage = new Page<>(1, 10);
        mpPage.setRecords(List.of());
        mpPage.setTotal(0L);
        doReturn(mpPage).when(userCouponMapper).selectPage(any(), any());

        service.pageVosByUser(new PageQuery(), 100L, 99);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<UserCoupon>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(userCouponMapper).selectPage(any(), captor.capture());
        Set<String> params = paramValues(captor.getValue());
        assertEquals(Set.of("100"), params, "非法 status 不应进入查询条件");
    }

    /**
     * 取出 Wrapper 里实际绑定的参数值。
     * <p>
     * 关键：MyBatis-Plus 的 {@code paramNameValuePairs} 是<b>懒填充</b>的——
     * 必须先调用 {@code getSqlSegment()}（它会触发列名解析与参数格式化），
     * 否则读到的一律是空 Map（曾因此误判为"测试写错"，实为读取时机不对）。
     */
    private static Set<String> paramValues(Wrapper<UserCoupon> wrapper) {
        AbstractWrapper<?, ?, ?> w = (AbstractWrapper<?, ?, ?>) wrapper;
        w.getSqlSegment();
        return w.getParamNameValuePairs().values().stream()
                .map(String::valueOf)
                .collect(Collectors.toSet());
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