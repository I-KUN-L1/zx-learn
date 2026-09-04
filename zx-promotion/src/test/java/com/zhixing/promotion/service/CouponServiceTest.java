package com.zhixing.promotion.service;

import com.zhixing.common.exceptions.BizIllegalException;
import com.zhixing.promotion.domain.dto.CouponFormDTO;
import com.zhixing.promotion.domain.po.Coupon;
import com.zhixing.promotion.mapper.CouponMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 优惠券服务单测：覆盖 4 态状态机
 * （0-未开始 / 1-进行中 / 2-已结束 / 3-已下架）的流转校验。
 */
@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock
    private CouponMapper couponMapper;

    @InjectMocks
    private CouponService service;

    private Coupon coupon(int status) {
        Coupon c = new Coupon();
        c.setId(1L);
        c.setStatus(status);
        return c;
    }

    @Test
    void createCouponDefaultsToNotStarted() {
        CouponFormDTO form = new CouponFormDTO();
        form.setName("新人立减券");
        form.setDiscountAmount(100L);
        form.setThresholdAmount(500L);
        form.setTotalNum(100);

        service.create(form);

        ArgumentCaptor<Coupon> captor = ArgumentCaptor.forClass(Coupon.class);
        verify(couponMapper).insert(captor.capture());
        assertEquals(CouponService.STATE_NOT_STARTED, captor.getValue().getStatus());
        assertEquals(0, captor.getValue().getIssuedNum());
    }

    @Test
    void issueFromNotStartedSucceeds() {
        when(couponMapper.selectById(1L)).thenReturn(coupon(CouponService.STATE_NOT_STARTED));

        service.issue(1L);

        verify(couponMapper).updateById(any(Coupon.class));
    }

    @Test
    void issueWhenAlreadyOngoingRejected() {
        when(couponMapper.selectById(1L)).thenReturn(coupon(CouponService.STATE_ONGOING));

        assertThrows(BizIllegalException.class, () -> service.issue(1L));
    }

    @Test
    void issueFromEndedSucceeds() {
        when(couponMapper.selectById(1L)).thenReturn(coupon(CouponService.STATE_ENDED));

        service.issue(1L);

        verify(couponMapper).updateById(any(Coupon.class));
    }

    @Test
    void pauseFromOngoingGoesToOffShelf() {
        Coupon c = coupon(CouponService.STATE_ONGOING);
        when(couponMapper.selectById(1L)).thenReturn(c);

        service.pause(1L);

        assertEquals(CouponService.STATE_OFF_SHELF, c.getStatus());
        verify(couponMapper).updateById(c);
    }

    @Test
    void pauseWhenAlreadyOffShelfRejected() {
        when(couponMapper.selectById(1L)).thenReturn(coupon(CouponService.STATE_OFF_SHELF));

        assertThrows(BizIllegalException.class, () -> service.pause(1L));
    }

    @Test
    void refreshStateAutoEndsAfterValidEndTime() {
        Coupon c = new Coupon();
        c.setId(1L);
        c.setStatus(CouponService.STATE_ONGOING);
        c.setValidEndTime(LocalDateTime.now().minusMinutes(1));
        when(couponMapper.selectById(1L)).thenReturn(c);

        service.getById(1L);

        assertEquals(CouponService.STATE_ENDED, c.getStatus());
        verify(couponMapper).updateById(c);
    }

    @Test
    void refreshStateKeepsOffShelfNotAutoEnded() {
        Coupon c = new Coupon();
        c.setId(1L);
        c.setStatus(CouponService.STATE_OFF_SHELF);
        c.setValidEndTime(LocalDateTime.now().minusMinutes(1));
        when(couponMapper.selectById(1L)).thenReturn(c);

        service.getById(1L);

        assertEquals(CouponService.STATE_OFF_SHELF, c.getStatus());
    }
}