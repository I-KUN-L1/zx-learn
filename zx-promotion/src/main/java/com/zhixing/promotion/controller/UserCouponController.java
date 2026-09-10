package com.zhixing.promotion.controller;

import com.zhixing.common.annotation.NoWrapper;
import com.zhixing.common.annotation.RequireRole;
import com.zhixing.common.constants.UserRole;
import com.zhixing.common.domain.R;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.utils.UserContext;
import com.zhixing.promotion.domain.vo.UserCouponVO;
import com.zhixing.promotion.service.UserCouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户优惠券。
 * 权限：领券/兑券/用券/查本人券均为学员端行为，仅学员(2)可操作，管理员/教师一律 403；
 * 优惠券规则批量查询（/rules）为内部 Feign 接口，不做角色限制。
 */
@RestController
@RequestMapping("/user-coupons")
@RequiredArgsConstructor
public class UserCouponController {

    private final UserCouponService userCouponService;

    /**
     * 普通领券。前端以 JSON body {@code {"couponId":1}} 提交，同时兼容 query 参数形式。
     */
    @PostMapping("/claim")
    @RequireRole(UserRole.STUDENT)
    public R<Long> claim(@RequestBody(required = false) Map<String, Object> body,
                         @RequestParam(required = false) Long couponId) {
        Long cid = couponId != null ? couponId
                : (body == null ? null : parseLong(body.get("couponId")));
        if (cid == null) {
            throw new BadRequestException("优惠券ID不能为空");
        }
        return R.ok(userCouponService.claim(UserContext.getUserId(), cid));
    }

    private Long parseLong(Object v) {
        if (v == null) {
            return null;
        }
        return v instanceof Number ? ((Number) v).longValue() : Long.valueOf(String.valueOf(v));
    }

    @PostMapping("/redeem")
    @RequireRole(UserRole.STUDENT)
    public R<Long> redeem(@RequestParam Long couponId, @RequestParam String code) {
        return R.ok(userCouponService.redeemByCode(UserContext.getUserId(), couponId, code));
    }

    @GetMapping
    @RequireRole(UserRole.STUDENT)
    public R<List<UserCouponVO>> list(@RequestParam(required = false) Integer status) {
        return R.ok(userCouponService.listVosByUser(UserContext.getUserId(), status));
    }

    @PostMapping("/{id}/use")
    @RequireRole(UserRole.STUDENT)
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