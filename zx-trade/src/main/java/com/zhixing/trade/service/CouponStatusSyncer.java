package com.zhixing.trade.service;

import com.zhixing.api.client.promotion.PromotionClient;
import com.zhixing.common.utils.TxSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 优惠券状态同步器：把交易侧的券核销 / 退回结果回写到 zx-promotion 的 {@code user_coupon.status}。
 *
 * <p><b>为什么需要它</b>：券是否可用的权威口径在 zx-promotion 的 user_coupon 上
 * （券列表、下单选券都读它），而"用券"这一步实际发生在交易侧（{@code coupon_use_record}）。
 * 过去只写核销流水、不回写券状态，于是出现两端漂移：
 * <ul>
 *   <li>券列表仍显示"未使用"（用户看着以为还能用）；</li>
 *   <li>再次使用时被 Redis 限用计数拦下，提示"该优惠券已达领取上限"（其实是已用过）。</li>
 * </ul>
 *
 * <p><b>可靠性</b>：三条通道叠加，且全部幂等（重复执行无副作用）：
 * <ol>
 *   <li>下单 / 关单本地事务 <b>提交后</b>同步回写（{@link TxSupport#afterCommit}）—— 保证券列表立即更新；</li>
 *   <li>MQ 核销流水消费端兜底（见 {@link CouponUseRecordService}）—— 覆盖同步调用失败；</li>
 *   <li>{@code CouponReconcileJob} 定时对账重放 —— 覆盖前两者同时失败的极端情况。</li>
 * </ol>
 *
 * <p><b>fail-open</b>：回写失败只记日志，绝不抛异常阻断下单 / 关单主流程（券的正确性由后两条通道收敛）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponStatusSyncer {

    private final PromotionClient promotionClient;

    /**
     * 下单核销后标记券「已使用」：本地事务提交后同步回写。
     */
    public void syncUsedAfterCommit(Long userCouponId, Long userId, Long couponId, Long orderId) {
        if (orderId == null || (userCouponId == null && (userId == null || couponId == null))) {
            return;
        }
        TxSupport.afterCommit(() -> markUsed(userCouponId, userId, couponId, orderId));
    }

    /**
     * 关单 / 取消后退回券：本地事务提交后同步回写。
     */
    public void syncRefundedAfterCommit(Long orderId) {
        if (orderId == null) {
            return;
        }
        TxSupport.afterCommit(() -> markRefunded(orderId));
    }

    /** 立即标记「已使用」（MQ 消费端、对账任务等已在事务外的场景直接调用） */
    public void markUsed(Long userCouponId, Long userId, Long couponId, Long orderId) {
        try {
            promotionClient.markCouponUsed(userCouponId, userId, couponId, orderId);
        } catch (Exception e) {
            log.warn("券状态回写失败（已使用）：userCouponId={}, userId={}, couponId={}, orderId={}, err={}",
                    userCouponId, userId, couponId, orderId, e.getMessage());
        }
    }

    /** 立即退回（MQ 消费端、对账任务等已在事务外的场景直接调用） */
    public void markRefunded(Long orderId) {
        try {
            promotionClient.markCouponRefunded(orderId);
        } catch (Exception e) {
            log.warn("券状态回写失败（退回）：orderId={}, err={}", orderId, e.getMessage());
        }
    }
}
