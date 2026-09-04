package com.zhixing.promotion.job;

import com.zhixing.promotion.service.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 秒杀对账任务：定时比对 Redis 已领取用户与 DB 落库记录的差集并补发领取消息。
 * <p>
 * 覆盖故障场景：MQ 停机期间 Redis 预扣成功的请求（实时投递失败），
 * MQ 恢复后由本任务自动补投落库，无需人工干预；也可通过
 * {@code POST /coupons/seckill/reconcile/{couponId}} 手动触发。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillReconcileJob {

    private final SeckillService seckillService;

    @Value("${zx.seckill.reconcile-enabled:true}")
    private boolean reconcileEnabled;

    @Scheduled(fixedDelayString = "${zx.seckill.reconcile-delay:60000}")
    public void reconcileAll() {
        if (!reconcileEnabled) {
            return;
        }
        Set<String> keys = seckillService.scanActivityCouponIds();
        if (keys == null || keys.isEmpty()) {
            return;
        }
        for (String key : keys) {
            try {
                long couponId = Long.parseLong(
                        key.substring(SeckillService.USERS_KEY_PREFIX.length()));
                int compensated = seckillService.reconcile(couponId);
                if (compensated > 0) {
                    log.info("秒杀对账补发完成：couponId={}, compensated={}", couponId, compensated);
                }
            } catch (NumberFormatException e) {
                log.warn("跳过无法解析的秒杀活动键：{}", key);
            } catch (Exception e) {
                log.error("秒杀对账异常：key={}, err={}", key, e.getMessage());
            }
        }
    }
}
