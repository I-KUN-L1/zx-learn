package com.zhixing.promotion.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.mq.MqTopics;
import com.zhixing.common.mq.RocketMQTemplate;
import com.zhixing.promotion.domain.po.Coupon;
import com.zhixing.promotion.domain.po.UserCoupon;
import com.zhixing.promotion.mapper.UserCouponMapper;
import com.zhixing.promotion.mq.SeckillClaimMsg;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 优惠券秒杀服务（Redis Lua 原子预扣 → MQ 异步落库 → 前端轮询结果）。
 * <p>
 * Redis key 规范（与 zx-trade 核销预扣的 coupon:stock 系列相互独立）：
 * <ul>
 *   <li>{@code coupon:seckill:stock:{couponId}} 秒杀余量（活动预热时 SETNX 写入）；</li>
 *   <li>{@code coupon:seckill:users:{couponId}} 已领取用户 set（限领判重 + 对账依据）；</li>
 *   <li>{@code coupon:seckill:result:{couponId}:{userId}} 落库结果（券码，供轮询查询）。</li>
 * </ul>
 * 可靠性：Lua 扣减成功后实时投递 MQ，投递失败不抛出——由对账任务比对
 * users set 与 DB 领取记录的差集自动补发（详见 SeckillReconcileJob）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillService {

    public static final String STOCK_KEY = "coupon:seckill:stock:%s";
    public static final String USERS_KEY = "coupon:seckill:users:%s";
    public static final String RESULT_KEY = "coupon:seckill:result:%s:%s";

    /** 排队中（已预扣，等待异步落库） */
    public static final String STATUS_QUEUING = "QUEUING";
    /** 已售罄 */
    public static final String STATUS_SOLD_OUT = "SOLD_OUT";
    /** 重复领取 */
    public static final String STATUS_REPEAT = "REPEAT";
    /** 活动未预热/未开始 */
    public static final String STATUS_NOT_READY = "NOT_READY";
    /** 落库成功 */
    public static final String STATUS_SUCCESS = "SUCCESS";
    /** 落库失败（确定性失败，如券被下架） */
    public static final String STATUS_FAILED = "FAILED";

    private static final String STOCK_KEY_PATTERN = "coupon:seckill:stock:*";
    public static final String USERS_KEY_PREFIX = "coupon:seckill:users:";

    private final StringRedisTemplate redisTemplate;
    private final RocketMQTemplate rocketMQTemplate;
    private final CouponService couponService;
    private final UserCouponMapper userCouponMapper;
    private final ObjectMapper objectMapper;

    @Value("${zx.seckill.claim-limit:1}")
    private int claimLimit;

    private DefaultRedisScript<Long> claimScript;

    @PostConstruct
    public void init() {
        claimScript = new DefaultRedisScript<>();
        claimScript.setResultType(Long.class);
        claimScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/seckill_claim.lua")));
    }

    /**
     * 秒杀领取：Lua 原子完成限领判重、余量判断、扣减、记录用户；成功后投递异步落库消息。
     * <p>所有业务结果（含拒绝）均以 200 + 状态字段返回，保证接口零事务、低延迟。</p>
     */
    public Map<String, Object> claim(Long userId, Long couponId) {
        Long r = redisTemplate.execute(claimScript,
                List.of(String.format(STOCK_KEY, couponId), String.format(USERS_KEY, couponId)),
                String.valueOf(userId), String.valueOf(claimLimit));
        if (r != null && r == 1L) {
            boolean ok = sendClaimMsg(couponId, userId);
            if (!ok) {
                // MQ 不可用：Redis 已扣减，不阻塞用户；对账任务稍后补发
                log.warn("秒杀领取消息投递失败，等待对账补偿：couponId={}, userId={}", couponId, userId);
            }
            return Map.of("status", STATUS_QUEUING);
        }
        String status = r == null ? STATUS_NOT_READY
                : switch (r.intValue()) {
                    case -1 -> STATUS_SOLD_OUT;
                    case -2 -> STATUS_REPEAT;
                    default -> STATUS_NOT_READY;
                };
        return Map.of("status", status);
    }

    /**
     * 投递秒杀领取消息。返回 false 表示 MQ 不可用（由对账补偿）。
     */
    public boolean sendClaimMsg(Long couponId, Long userId) {
        SeckillClaimMsg msg = new SeckillClaimMsg();
        msg.setCouponId(couponId);
        msg.setUserId(userId);
        return rocketMQTemplate.send(MqTopics.TOPIC_SECKILL_CLAIM, MqTopics.Tags.SECKILL_CLAIM, msg);
    }

    /**
     * 轮询领取结果：先查 Redis 结果键（消费端落库后写入），未命中再查 DB 兜底。
     */
    public Map<String, Object> result(Long userId, Long couponId) {
        String value = redisTemplate.opsForValue().get(String.format(RESULT_KEY, couponId, userId));
        if (value != null) {
            if (value.startsWith(STATUS_FAILED)) {
                return Map.of("status", STATUS_FAILED, "reason", value.substring(STATUS_FAILED.length() + 1));
            }
            if (STATUS_REPEAT.equals(value)) {
                return Map.of("status", STATUS_REPEAT);
            }
            return Map.of("status", STATUS_SUCCESS, "couponCode", value);
        }
        // DB 兜底：result 键丢失/过期但已落库
        UserCoupon uc = userCouponMapper.selectOne(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getCouponId, couponId));
        if (uc != null) {
            return Map.of("status", STATUS_SUCCESS, "couponCode", uc.getCouponCode());
        }
        return Map.of("status", STATUS_QUEUING);
    }

    /**
     * 活动预热：将余量写入 Redis（SETNX 防止覆盖已抢量，重复调用安全）。
     */
    public void warmup(Long couponId) {
        Coupon coupon = couponService.getById(couponId);
        int remaining = Math.max(
                (coupon.getTotalNum() == null ? 0 : coupon.getTotalNum())
                        - (coupon.getIssuedNum() == null ? 0 : coupon.getIssuedNum()), 0);
        Boolean first = redisTemplate.opsForValue().setIfAbsent(
                String.format(STOCK_KEY, couponId), String.valueOf(remaining));
        log.info("秒杀活动预热：couponId={}, remaining={}, firstWarmup={}", couponId, remaining, first);
    }

    /**
     * 单活动对账：比较 Redis 已领取用户与 DB 落库记录，对差集补发领取消息。
     *
     * @return 补发条数
     */
    public int reconcile(Long couponId) {
        Set<String> members = redisTemplate.opsForSet()
                .members(String.format(USERS_KEY, couponId));
        if (members == null || members.isEmpty()) {
            return 0;
        }
        Set<String> dbUserIds = userCouponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getCouponId, couponId)).stream()
                .map(uc -> String.valueOf(uc.getUserId()))
                .collect(java.util.stream.Collectors.toSet());
        int compensated = 0;
        for (String userId : members) {
            if (dbUserIds.contains(userId)) {
                continue;
            }
            log.warn("对账发现漏单，补发领取消息：couponId={}, userId={}", couponId, userId);
            if (sendClaimMsg(couponId, Long.valueOf(userId))) {
                compensated++;
            }
        }
        return compensated;
    }

    /**
     * 扫描全部进行中的秒杀活动（users set 键），供定时对账任务使用。
     */
    public Set<String> scanActivityCouponIds() {
        return redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Set<String>>) conn -> {
            Set<String> keys = new java.util.HashSet<>();
            try (var cursor = conn.scan(org.springframework.data.redis.core.ScanOptions
                    .scanOptions().match(USERS_KEY_PREFIX + "*").count(200).build())) {
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next()));
                }
            }
            return keys;
        });
    }

    /**
     * 消费端写领取结果（券码），供前端轮询；24h 过期。
     */
    public void writeResult(Long couponId, Long userId, String value) {
        redisTemplate.opsForValue().set(String.format(RESULT_KEY, couponId, userId), value,
                Duration.ofHours(24));
    }

    /**
     * 供 handler 反序列化消息使用
     */
    public SeckillClaimMsg parseClaimMsg(String json) {
        try {
            return objectMapper.readValue(json, SeckillClaimMsg.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("秒杀消息反序列化失败", e);
        }
    }
}
