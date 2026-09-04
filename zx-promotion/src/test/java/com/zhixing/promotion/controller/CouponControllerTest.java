package com.zhixing.promotion.controller;

import com.zhixing.promotion.domain.dto.CouponFormDTO;
import com.zhixing.promotion.domain.po.Coupon;
import com.zhixing.promotion.service.CouponService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 优惠券控制器单测：验证对外接口委托到服务层
 */
@ExtendWith(MockitoExtension.class)
class CouponControllerTest {

    @Mock
    private CouponService couponService;

    @InjectMocks
    private CouponController controller;

    private CouponFormDTO form() {
        CouponFormDTO form = new CouponFormDTO();
        form.setName("新人券");
        form.setDiscountAmount(100L);
        form.setThresholdAmount(500L);
        form.setTotalNum(100);
        return form;
    }

    @Test
    void createDelegatesToService() {
        when(couponService.create(form())).thenReturn(88L);

        Long id = controller.create(form()).getData();

        assertEquals(88L, id);
        verify(couponService).create(form());
    }

    @Test
    void getByIdReturnsCoupon() {
        Coupon coupon = new Coupon();
        coupon.setId(1L);
        coupon.setName("新人券");
        when(couponService.getById(1L)).thenReturn(coupon);

        Coupon got = controller.getById(1L).getData();

        assertEquals(1L, got.getId());
        assertEquals("新人券", got.getName());
    }

    @Test
    void issueAndPauseDelegateToService() {
        assertTrue(controller.issue(1L).success());
        assertTrue(controller.pause(1L).success());
        verify(couponService).issue(1L);
        verify(couponService).pause(1L);
    }

    @Test
    void deleteDelegatesToService() {
        assertTrue(controller.delete(1L).success());
        verify(couponService).delete(1L);
    }
}