package com.zhixing.api.client.promotion;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 营销服务客户端
 */
@FeignClient(value = "promotion-service", contextId = "promotionClient")
public interface PromotionClient {

    @GetMapping("/user-coupons/rules")
    Map<Long, List<String>> queryCouponRules(@RequestParam("ids") List<Long> ids);

    /**
     * 标记用户券「已使用」（下单核销优惠券后同步回写，幂等）。
     * <p>
     * 券是否可用的权威口径在 promotion 的 user_coupon.status 上：核销后必须回写，
     * 否则券列表会一直显示"未使用"，而再次使用又会被限用计数拦下。
     *
     * @param userCouponId 用户券行 id
     * @param userId       兜底定位（userCouponId 缺失时按 userId + couponId 定位）
     * @param couponId     兜底定位
     * @param orderId      核销订单 id
     */
    @PostMapping("/user-coupons/internal/mark-used")
    void markCouponUsed(@RequestParam(value = "userCouponId", required = false) Long userCouponId,
                        @RequestParam(value = "userId", required = false) Long userId,
                        @RequestParam(value = "couponId", required = false) Long couponId,
                        @RequestParam(value = "orderId", required = false) Long orderId);

    /**
     * 退回用户券（订单超时关单 / 取消后同步回写，幂等）。
     *
     * @param orderId 被关闭的订单 id
     */
    @PostMapping("/user-coupons/internal/mark-refunded")
    void markCouponRefunded(@RequestParam("orderId") Long orderId);
}
