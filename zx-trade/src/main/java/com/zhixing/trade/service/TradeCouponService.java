package com.zhixing.trade.service;

import com.zhixing.common.exceptions.BizIllegalException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.util.Arrays;

/**
 * 优惠券库存服务（Redis Lua 原子操作，防超卖与超量领取）。
 * <p>
 * 余量与用户已用数量缓存在 Redis，真正的落库由 MQ 消费端异步完成，
 * 预扣只为并发控制，最终以消费流水完成对账。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeCouponService {

    private static final String STOCK_KEY = "coupon:stock:%s";
    private static final String USED_KEY = "coupon:used:%s:%s";

    private final StringRedisTemplate redisTemplate;

    private DefaultRedisScript<Long> deductScript;
    private DefaultRedisScript<Long> restoreScript;

    @PostConstruct
    public void init() {
        deductScript = new DefaultRedisScript<>();
        deductScript.setResultType(Long.class);
        deductScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/coupon_deduct.lua")));

        restoreScript = new DefaultRedisScript<>();
        restoreScript.setResultType(Long.class);
        restoreScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/coupon_restore.lua")));
    }

    /**
     * 原子预扣优惠券库存。
     *
     * @param limit  该用户对当前优惠券的限用/限领数量
     * @param amount 本次核销抵扣数量（一般=1）
     */
    public void deductStock(Long couponId, Long userId, int limit, int amount) {
        if (couponId == null) {
            return;
        }
        String stockKey = String.format(STOCK_KEY, couponId);
        String usedKey = String.format(USED_KEY, couponId, userId);
        Long result = redisTemplate.execute(deductScript, Arrays.asList(stockKey, usedKey),
                String.valueOf(limit), String.valueOf(amount));
        if (result == null || result != 1L) {
            throw new BizIllegalException(result != null && result == -2L
                    ? "该优惠券已达领取上限"
                    : "优惠券库存不足，请刷新后再试");
        }
    }

    /**
     * 恢复优惠券库存（订单超时关单 / 取消时调用）。尽力而为，失败不影响主流程。
     */
    public void restoreStock(Long couponId, Long userId, int amount) {
        if (couponId == null) {
            return;
        }
        try {
            String stockKey = String.format(STOCK_KEY, couponId);
            String usedKey = String.format(USED_KEY, couponId, userId);
            redisTemplate.execute(restoreScript, Arrays.asList(stockKey, usedKey),
                    String.valueOf(amount));
        } catch (Exception e) {
            log.warn("优惠券库存恢复失败（由对账任务兜底）：couponId={}, err={}", couponId, e.getMessage());
        }
    }
}