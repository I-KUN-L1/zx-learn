package com.zhixing.promotion.controller;

import com.zhixing.common.domain.R;
import com.zhixing.common.utils.UserContext;
import com.zhixing.promotion.job.SeckillReconcileJob;
import com.zhixing.promotion.service.SeckillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 优惠券秒杀：Redis Lua 原子预扣 + MQ 异步落库，前端轮询结果。
 */
@RestController
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;
    private final SeckillReconcileJob seckillReconcileJob;

    /**
     * 秒杀领取：立即返回 QUEUING / SOLD_OUT / REPEAT / NOT_READY 状态。
     */
    @PostMapping("/user-coupons/seckill/{couponId}")
    public R<Map<String, Object>> claim(@PathVariable Long couponId) {
        return R.ok(seckillService.claim(UserContext.getUserId(), couponId));
    }

    /**
     * 轮询领取结果：QUEUING / SUCCESS(含券码) / REPEAT / FAILED。
     */
    @GetMapping("/user-coupons/seckill/{couponId}/result")
    public R<Map<String, Object>> result(@PathVariable Long couponId) {
        return R.ok(seckillService.result(UserContext.getUserId(), couponId));
    }

    /**
     * 活动预热：把余量写入 Redis（SETNX，重复调用安全）。
     */
    @PostMapping("/coupons/seckill/warmup/{couponId}")
    public R<Void> warmup(@PathVariable Long couponId) {
        seckillService.warmup(couponId);
        return R.ok();
    }

    /**
     * 手动补偿：对指定活动执行一次对账，返回补发条数。
     */
    @PostMapping("/coupons/seckill/reconcile/{couponId}")
    public R<Map<String, Object>> reconcile(@PathVariable Long couponId) {
        return R.ok(Map.of("compensated", seckillService.reconcile(couponId)));
    }
}
