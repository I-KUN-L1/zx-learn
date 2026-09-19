package com.zhixing.trade.job;

import com.zhixing.trade.service.CouponUseRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 优惠券状态对账任务：修复「券已核销，但 zx-promotion 侧 user_coupon 仍是未使用」的漂移。
 *
 * <p>为什么需要它：正常情况下用券后券状态有两条回写通道 ——
 * 下单时的同步回写（Feign afterCommit）与 MQ 核销流水的消费端兜底。
 * 但两者都可能失败（营销服务不可用 / MQ 不可用并重试耗尽转死信），
 * 此时券已经抵扣过，状态却还是"未使用"：用户看到的列表与实际不符，
 * 再次使用又会被限用计数拦成"已达使用上限"。
 *
 * <p>本任务按 {@code coupon_use_record} 的最终状态重放回写（接口幂等），
 * 语义精确、不会误伤正常业务。可通过 {@code tx.coupon.reconcile-enabled=false} 关闭。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponReconcileJob {

    private final CouponUseRecordService couponUseRecordService;

    /** 是否启用（默认启用） */
    @Value("${tx.coupon.reconcile-enabled:true}")
    private boolean enabled;

    /** 单轮最多重放的流水条数 */
    @Value("${tx.coupon.reconcile-batch:200}")
    private int batch;

    /** 只重放最近 N 小时内变动过的流水（历史存量由迁移脚本一次性修复） */
    @Value("${tx.coupon.reconcile-lookback-hours:24}")
    private int lookbackHours;

    @Scheduled(fixedDelayString = "${tx.coupon.reconcile-interval:300000}")
    public void reconcileCouponStatus() {
        if (!enabled) {
            return;
        }
        try {
            int replayed = couponUseRecordService.reconcile(batch, lookbackHours);
            if (replayed > 0) {
                log.info("优惠券状态对账完成：本轮重放 {} 条核销流水", replayed);
            }
        } catch (Exception e) {
            log.error("优惠券状态对账任务异常：{}", e.getMessage());
        }
    }
}
