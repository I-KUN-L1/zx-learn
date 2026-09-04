package com.zhixing.promotion.controller;

import com.zhixing.common.domain.PageDTO;
import com.zhixing.common.domain.PageQuery;
import com.zhixing.common.domain.R;
import com.zhixing.promotion.domain.dto.CouponFormDTO;
import com.zhixing.promotion.domain.po.Coupon;
import com.zhixing.promotion.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 优惠券管理
 */
@RestController
@RequestMapping("/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @PostMapping
    public R<Long> create(@RequestBody CouponFormDTO form) {
        return R.ok(couponService.create(form));
    }

    @GetMapping("/page")
    public R<PageDTO<Coupon>> page(PageQuery query,
                                   @RequestParam(required = false) String name,
                                   @RequestParam(required = false) Integer status) {
        return R.ok(couponService.page(query, name, status));
    }

    @GetMapping("/{id}")
    public R<Coupon> getById(@PathVariable Long id) {
        return R.ok(couponService.getById(id));
    }

    @PutMapping("/{id}/issue")
    public R<Void> issue(@PathVariable Long id) {
        couponService.issue(id);
        return R.ok();
    }

    @PutMapping("/{id}/pause")
    public R<Void> pause(@PathVariable Long id) {
        couponService.pause(id);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        couponService.delete(id);
        return R.ok();
    }
}