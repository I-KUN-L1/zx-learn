package com.zhixing.trade.service;

import com.zhixing.common.exceptions.BizIllegalException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    /**
     * 优惠券余量 key 缺失时的兜底余量（懒加载写入）。
     * <p>
     * 余量 key 历史遗漏预热，导致带优惠券下单在 Lua 预扣时被误判"库存不足"。
     * 实际库存上限在 promotion 侧由「领取数 &le; 发行总量」+ 本表 (user_id, coupon_id) 唯一 + 本服务
     * per-user used 限流共同约束，故兜底值取一个 &ge; 任意真实发行总量的上界即可保证正确性：
     * 不会放大超卖风险（每用户每券最多 1 张），又能让冷启动/历史数据下单不再失败。
     * </p>
     */
    @Value("${zx.trade.coupon-stock-default:1000000}")
    private long couponStockDefault;

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
     * <p>
     * 惰性兜底：若 {@code coupon:stock:id} 尚未预热（管理端从未手动调用预热），
     * 则首次核销时以配置兜底余量 SETNX 写入，避免一直返回"库存不足"导致下单失败。
     *
     * @param couponId 优惠券模板 id
     * @param userId   当前用户 id
     * @param limit    该用户对当前优惠券的限用/限领数量
     * @param amount   本次核销抵扣数量（一般=1）
     */
    public void deductStock(Long couponId, Long userId, int limit, int amount) {
        if (couponId == null) {
            return;
        }
        String stockKey = String.format(STOCK_KEY, couponId);
        String usedKey = String.format(USED_KEY, couponId, userId);

        // 惰性兜底：stock key 缺失 → 兜底补一个大余量，保证历史数据下单可成功
        if (Boolean.FALSE.equals(redisTemplate.hasKey(stockKey))) {
            // SETNX：已有则不覆盖，仅当完全缺失才设置
            Boolean set = redisTemplate.opsForValue()
                    .setIfAbsent(stockKey, String.valueOf(couponStockDefault));
            if (Boolean.TRUE.equals(set)) {
                log.info("TradeCouponService 惰性补预热：couponId={}, defaultStock={}",
                        couponId, couponStockDefault);
            }
        }

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