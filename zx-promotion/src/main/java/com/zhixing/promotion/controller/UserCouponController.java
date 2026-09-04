package com.zhixing.promotion.controller;

import com.zhixing.common.annotation.NoWrapper;
import com.zhixing.common.domain.R;
import com.zhixing.common.utils.UserContext;
import com.zhixing.promotion.domain.po.UserCoupon;
import com.zhixing.promotion.service.UserCouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户优惠券
 */
@RestController
@RequestMapping("/user-coupons")
@RequiredArgsConstructor
public class UserCouponController {

    private final UserCouponService userCouponService;

    @PostMapping("/claim")
    public R<Long> claim(@RequestParam Long couponId) {
        return R.ok(userCouponService.claim(UserContext.getUserId(), couponId));
    }

    @PostMapping("/redeem")
    public R<Long> redeem(@RequestParam Long couponId, @RequestParam String code) {
        return R.ok(userCouponService.redeemByCode(UserContext.getUserId(), couponId, code));
    }

    @GetMapping
    public R<List<UserCoupon>> list(@RequestParam(required = false) Integer status) {
        return R.ok(userCouponService.listByUser(UserContext.getUserId(), status));
    }

    @PostMapping("/{id}/use")
    public R<Void> use(@PathVariable Long id, @RequestParam(required = false) Long orderId) {
        userCouponService.use(id, orderId);
        return R.ok();
    }

    /**
     * 内部 Feign 接口：批量查询优惠券规则（不包装）
     */
    @GetMapping("/rules")
    @NoWrapper
    public Map<Long, List<String>> rules(@RequestParam("ids") List<Long> ids) {
        return userCouponService.queryRules(ids);
    }
}