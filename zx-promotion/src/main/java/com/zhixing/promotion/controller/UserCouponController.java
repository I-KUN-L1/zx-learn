package com.zhixing.promotion.controller;

import com.zhixing.common.annotation.NoWrapper;
import com.zhixing.common.annotation.RequireRole;
import com.zhixing.common.constants.UserRole;
import com.zhixing.common.domain.PageDTO;
import com.zhixing.common.domain.PageQuery;
import com.zhixing.common.domain.R;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.utils.InternalOnlyGuard;
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
 * 优惠券规则批量查询（/rules）与券状态同步（/internal/**）为内部 Feign 接口，不做角色限制，
 * 但经 {@link InternalOnlyGuard} 拒绝一切来自网关的外部请求（含登录用户）。
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

    /**
     * 我的优惠券列表。
     * <p>
     * {@code status} 采用与返回体字段<b>一致</b>的前端语义：1 未使用 / 2 已使用 / 3 已过期；
     * 不传或传非法值则返回全部（服务层负责语义转换，见 {@code UserCouponService#toStoredStatus}）。
     */
    @GetMapping
    @RequireRole(UserRole.STUDENT)
    public R<List<UserCouponVO>> list(@RequestParam(required = false) Integer status) {
        return R.ok(userCouponService.listVosByUser(UserContext.getUserId(), status));
    }

    /**
     * 我的优惠券（分页）。
     * <p>
     * status：1 未使用 / 2 已使用 / 3 已过期 —— 与列表接口及返回体 status 字段<b>同语义</b>。
     * <p>
     * 该端点此前缺失（前端 api/promotion.ts 的 {@code myCouponsPage()} 已按
     * {@code /user-coupons/page} 调用，后端却无实现），属埋雷式契约缺口，现补齐。
     */
    @GetMapping("/page")
    @RequireRole(UserRole.STUDENT)
    public R<PageDTO<UserCouponVO>> page(PageQuery query,
                                         @RequestParam(required = false) Integer status) {
        return R.ok(userCouponService.pageVosByUser(query, UserContext.getUserId(), status));
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

    // ============ 券状态同步内部接口（Feign 调用，不包装） ============

    /**
     * 标记用户券「已使用」——交易服务下单核销优惠券后回写（幂等）。
     * <p>
     * 与 MQ 核销流水双通道：同步回写保证券列表<b>立即</b>显示"已使用"，
     * MQ 消费端与对账任务作为失败兜底，三条通道都幂等，重复执行无副作用。
     * <p>
     * 仅限服务间调用，外部用户经网关访问一律 403（见 {@link InternalOnlyGuard}）。
     */
    @PostMapping("/internal/mark-used")
    @NoWrapper
    public void markUsed(@RequestParam(required = false) Long userCouponId,
                         @RequestParam(required = false) Long userId,
                         @RequestParam(required = false) Long couponId,
                         @RequestParam(required = false) Long orderId) {
        InternalOnlyGuard.checkInternal();
        userCouponService.markUsed(userCouponId, userId, couponId, orderId);
    }

    /**
     * 退回用户券——订单超时关单 / 取消后由交易服务回写（幂等）。
     * <p>
     * 仅限服务间调用，外部用户经网关访问一律 403。
     */
    @PostMapping("/internal/mark-refunded")
    @NoWrapper
    public void markRefunded(@RequestParam Long orderId) {
        InternalOnlyGuard.checkInternal();
        userCouponService.markRefunded(orderId);
    }
}