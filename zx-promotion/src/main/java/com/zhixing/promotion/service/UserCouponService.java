package com.zhixing.promotion.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.exceptions.BizIllegalException;
import com.zhixing.promotion.domain.po.Coupon;
import com.zhixing.promotion.domain.po.UserCoupon;
import com.zhixing.promotion.mapper.CouponMapper;
import com.zhixing.promotion.mapper.UserCouponMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户优惠券服务。
 * <p>
 * 券领取/兑换状态机：未使用(0) -> 已使用(1) / 已过期(2)。
 * 领取与兑换码核销均以 user_coupon 上的 (user_id, coupon_id) 唯一索引做一次性兜底，
 * 并发重复领取/兑换由 DB 唯一约束拦截（DuplicateKeyException）转为幂等提示。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class UserCouponService {

    /** 未使用 */
    private static final int UNUSED = 0;
    /** 已使用 */
    private static final int USED = 1;
    /** 已过期 */
    private static final int EXPIRED = 2;

    private final UserCouponMapper userCouponMapper;
    private final CouponMapper couponMapper;
    private final CouponService couponService;

    /**
     * 领取优惠券：仅"进行中"且未发完、且当前处于生效时间内的券可领取。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long claim(Long userId, Long couponId) {
        Coupon coupon = couponService.getById(couponId);
        return doClaim(userId, coupon);
    }

    /**
     * 兑换码一次性核销：校验兑换码归属与券的进行中状态后为用户发放，返回值同领取 id。
     * 同一用户对同一券仅能核销一次，由唯一索引兜底。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long redeemByCode(Long userId, Long couponId, String exchangeCode) {
        Coupon coupon = couponService.requireByExchangeCode(exchangeCode);
        if (!java.util.Objects.equals(coupon.getId(), couponId)) {
            throw new BizIllegalException("兑换码与优惠券不匹配");
        }
        return doClaim(userId, coupon);
    }

    private Long doClaim(Long userId, Coupon coupon) {
        if (coupon.getStatus() == null
                || coupon.getStatus() != CouponService.STATE_ONGOING) {
            throw new BizIllegalException("优惠券未在进行中，无法领取/兑换");
        }
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getValidBeginTime() != null && now.isBefore(coupon.getValidBeginTime())) {
            throw new BizIllegalException("优惠券尚未到生效时间");
        }
        if (coupon.getIssuedNum() == null) {
            coupon.setIssuedNum(0);
        }
        if (coupon.getIssuedNum() >= coupon.getTotalNum()) {
            throw new BizIllegalException("优惠券已领完");
        }
        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(userId);
        userCoupon.setCouponId(coupon.getId());
        userCoupon.setCouponName(coupon.getName());
        userCoupon.setDiscountAmount(coupon.getDiscountAmount());
        userCoupon.setThresholdAmount(coupon.getThresholdAmount());
        userCoupon.setStatus(UNUSED);
        userCoupon.setValidBeginTime(coupon.getValidBeginTime());
        userCoupon.setValidEndTime(coupon.getValidEndTime());
        try {
            userCouponMapper.insert(userCoupon);
        } catch (DuplicateKeyException e) {
            // uk_user_coupon(user_id, coupon_id) 兜底：同一用户同一券只能领取/兑换一次
            throw new BizIllegalException("已领取该优惠券，请勿重复领取");
        }
        coupon.setIssuedNum(coupon.getIssuedNum() + 1);
        couponMapper.updateById(coupon);
        return userCoupon.getId();
    }

    /**
     * 使用优惠券：仅未使用状态可流转至已使用。
     */
    public void use(Long userCouponId, Long orderId) {
        UserCoupon userCoupon = userCouponMapper.selectById(userCouponId);
        if (userCoupon == null) {
            throw new BadRequestException("用户优惠券不存在");
        }
        if (userCoupon.getStatus() != null && userCoupon.getStatus() == USED) {
            throw new BizIllegalException("优惠券已使用");
        }
        if (userCoupon.getStatus() != null && userCoupon.getStatus() == EXPIRED) {
            throw new BizIllegalException("优惠券已过期");
        }
        userCoupon.setStatus(USED);
        userCoupon.setUseTime(LocalDateTime.now());
        userCoupon.setOrderId(orderId);
        userCouponMapper.updateById(userCoupon);
    }

    /**
     * 秒杀领取落库（MQ 消费端调用）：生成券码快照 + 发放数条件递增。
     * <p>
     * uk_user_coupon 唯一索引兜底幂等：重复消息返回 false（已领取），
     * 发放数用条件更新 {@code issued_num < total_num} 防并发丢更新。
     * </p>
     *
     * @return true=本次落库成功；false=重复领取（幂等跳过）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean claimSeckill(Long userId, Coupon coupon, String couponCode) {
        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(userId);
        userCoupon.setCouponId(coupon.getId());
        userCoupon.setCouponName(coupon.getName());
        userCoupon.setDiscountAmount(coupon.getDiscountAmount());
        userCoupon.setThresholdAmount(coupon.getThresholdAmount());
        userCoupon.setStatus(UNUSED);
        userCoupon.setValidBeginTime(coupon.getValidBeginTime());
        userCoupon.setValidEndTime(coupon.getValidEndTime());
        userCoupon.setCouponCode(couponCode);
        try {
            userCouponMapper.insert(userCoupon);
        } catch (DuplicateKeyException e) {
            return false;
        }
        // 发放数 +1（条件更新防并发覆盖；失败仅影响统计，由对账校正）
        couponMapper.update(null, new LambdaUpdateWrapper<Coupon>()
                .eq(Coupon::getId, coupon.getId())
                .apply("IFNULL(issued_num, 0) < total_num")
                .setSql("issued_num = IFNULL(issued_num, 0) + 1"));
        return true;
    }

    public List<UserCoupon> listByUser(Long userId, Integer status) {
        return userCouponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .eq(status != null, UserCoupon::getStatus, status)
                .orderByDesc(UserCoupon::getCreateTime));
    }

    /**
     * 内部 Feign 接口：根据优惠券 id 批量返回优惠规则描述
     */
    public Map<Long, List<String>> queryRules(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return couponMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Coupon::getId, this::rulesOf));
    }

    private List<String> rulesOf(Coupon coupon) {
        return List.of(
                "满" + coupon.getThresholdAmount() + "减" + coupon.getDiscountAmount(),
                "面值" + coupon.getDiscountAmount() + "分（门槛" + coupon.getThresholdAmount() + "分）");
    }
}