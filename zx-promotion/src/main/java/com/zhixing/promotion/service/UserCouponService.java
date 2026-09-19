package com.zhixing.promotion.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixing.common.domain.PageDTO;
import com.zhixing.common.domain.PageQuery;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.exceptions.BizIllegalException;
import com.zhixing.promotion.domain.po.Coupon;
import com.zhixing.promotion.domain.po.UserCoupon;
import com.zhixing.promotion.domain.vo.UserCouponVO;
import com.zhixing.promotion.mapper.CouponMapper;
import com.zhixing.promotion.mapper.UserCouponMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * <p>
 * <b>状态权威口径</b>：user_coupon.status 是"券是否可用"的唯一依据，券列表、下单选券
 * 都读它。因此交易服务核销/退回优惠券后，必须回写这里的状态（见 {@link #markUsed} /
 * {@link #markRefunded}），否则会出现"券已经用掉了，列表还显示未使用"的漂移。
 * </p>
 */
@Slf4j
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
     * <p>
     * 面向学员端的显式操作（{@code POST /user-coupons/{id}/use}），重复使用按业务错误提示；
     * 交易服务下单核销后的状态回写请走幂等的 {@link #markUsed}。
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
     * <b>幂等</b>标记「已使用」——交易服务下单核销优惠券后回写状态（内部 Feign 调用）。
     * <p>
     * 与 {@link #use(Long, Long)} 的差别在于幂等语义：同步 Feign 重试、MQ 重复消费、
     * 对账任务重放都会重复调用本方法，因此"已使用"必须静默返回而不是抛错，
     * 否则重复投递会污染调用方链路。
     * </p>
     *
     * @param userCouponId 用户券行 id（优先按主键定位）
     * @param userId       兜底定位：userId + couponId
     * @param couponId     兜底定位：userId + couponId
     * @param orderId      核销订单 id
     * @return true=本次发生了状态流转；false=无需变更（已使用 / 券不存在）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean markUsed(Long userCouponId, Long userId, Long couponId, Long orderId) {
        UserCoupon userCoupon = locate(userCouponId, userId, couponId);
        if (userCoupon == null) {
            log.warn("券状态同步：用户券不存在，跳过。userCouponId={}, userId={}, couponId={}, orderId={}",
                    userCouponId, userId, couponId, orderId);
            return false;
        }
        if (userCoupon.getStatus() != null && userCoupon.getStatus() == USED) {
            // 幂等：已使用（重复投递 / 重放）直接返回
            if (orderId != null && userCoupon.getOrderId() != null
                    && !orderId.equals(userCoupon.getOrderId())) {
                log.warn("券状态同步：券 {} 已由订单 {} 核销，本次订单 {} 不覆盖",
                        userCoupon.getId(), userCoupon.getOrderId(), orderId);
            }
            return false;
        }
        userCoupon.setStatus(USED);
        userCoupon.setUseTime(LocalDateTime.now());
        userCoupon.setOrderId(orderId);
        userCouponMapper.updateById(userCoupon);
        log.info("券状态同步为已使用：userCouponId={}, userId={}, couponId={}, orderId={}",
                userCoupon.getId(), userCoupon.getUserId(), userCoupon.getCouponId(), orderId);
        return true;
    }

    /**
     * <b>幂等</b>退回优惠券——订单超时关单 / 取消后回写（内部 Feign 调用）。
     * <p>
     * 按 {@code order_id} 精确定位该单核销掉的券，避免误伤用户后续在新订单里用同一张券的情况。
     * 券已过有效期则置为「已过期」，防止过期券被退回后又能使用。
     * </p>
     *
     * @return 本次还原的券行数（0 表示无需变更，幂等）
     */
    @Transactional(rollbackFor = Exception.class)
    public int markRefunded(Long orderId) {
        if (orderId == null) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        // 仍在有效期内 → 还原为未使用（可再次使用）
        int restored = userCouponMapper.update(null, new LambdaUpdateWrapper<UserCoupon>()
                .eq(UserCoupon::getOrderId, orderId)
                .eq(UserCoupon::getStatus, USED)
                .and(w -> w.isNull(UserCoupon::getValidEndTime)
                        .or().gt(UserCoupon::getValidEndTime, now))
                .set(UserCoupon::getStatus, UNUSED)
                .set(UserCoupon::getUseTime, null)
                .set(UserCoupon::getOrderId, null));
        // 已过有效期 → 置为已过期（不能因为退回而"复活"过期券）
        int expired = userCouponMapper.update(null, new LambdaUpdateWrapper<UserCoupon>()
                .eq(UserCoupon::getOrderId, orderId)
                .eq(UserCoupon::getStatus, USED)
                .isNotNull(UserCoupon::getValidEndTime)
                .le(UserCoupon::getValidEndTime, now)
                .set(UserCoupon::getStatus, EXPIRED)
                .set(UserCoupon::getUseTime, null)
                .set(UserCoupon::getOrderId, null));
        if (restored + expired > 0) {
            log.info("券状态退回：orderId={}, 还原未使用={}, 置为已过期={}", orderId, restored, expired);
        }
        return restored + expired;
    }

    /** 定位用户券：优先主键；主键缺失（前端未回传）时按 (userId, couponId) 兜底 */
    private UserCoupon locate(Long userCouponId, Long userId, Long couponId) {
        if (userCouponId != null) {
            UserCoupon byId = userCouponMapper.selectById(userCouponId);
            if (byId != null) {
                return byId;
            }
        }
        if (userId == null || couponId == null) {
            return null;
        }
        return userCouponMapper.selectOne(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .eq(UserCoupon::getCouponId, couponId)
                .last("LIMIT 1"));
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

    /** 用户优惠券 VO 列表（对齐前端 UserCouponVO 契约：discountValue 字段 + 状态语义 0/1/2 → 1/2/3） */
    public List<UserCouponVO> listVosByUser(Long userId, Integer status) {
        return listByUser(userId, toStoredStatus(status)).stream().map(this::toVO).toList();
    }

    /**
     * 用户优惠券分页（对齐前端 {@code GET /user-coupons/page} 契约）。
     * <p>
     * 历史缺陷：前端 {@code api/promotion.ts} 的 {@code myCouponsPage()} 已声明调用
     * {@code /user-coupons/page}，但后端只有裸 {@code @GetMapping} 的列表接口，
     * 该路径必然 404——属"埋雷"（当前无页面调用，一旦有人用就踩）。
     * </p>
     * <p>
     * status 语义与列表接口<b>完全一致</b>：入参与返回体同为 1未使用 / 2已使用 / 3已过期。
     * 注意必须<b>先转换再判空</b>：若在转换前判空，非法入参会退化成
     * {@code status = null} 这种永不匹配的条件（见 {@link #toStoredStatus(Integer)} 注释）。
     * </p>
     */
    public PageDTO<UserCouponVO> pageVosByUser(PageQuery query, Long userId, Integer frontStatus) {
        Integer stored = toStoredStatus(frontStatus);
        Page<UserCoupon> page = userCouponMapper.selectPage(query.toMpPage("create_time", false),
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .eq(stored != null, UserCoupon::getStatus, stored));
        return PageDTO.of(page, this::toVO);
    }

    /**
     * 前端状态语义（1未使用 / 2已使用 / 3已过期）→ 存储语义（0 / 1 / 2）。
     * <p>
     * 筛选入参必须与<b>返回的 status 字段同语义</b>，否则调用方"按返回什么就筛什么"会筛错：
     * 历史实现直接透传参数，导致 {@code GET /user-coupons?status=1} 返回的是「已使用」的券
     * （存储 1），而返回体里 status=1 却代表「未使用」—— 同一个接口两套语义。
     * 传非法值（null / 越界）时不筛选，避免把筛选条件写成永不匹配。
     */
    Integer toStoredStatus(Integer status) {
        if (status == null) {
            return null;
        }
        int stored = status - 1;
        return (stored < UNUSED || stored > EXPIRED) ? null : stored;
    }

    /** 用户优惠券 PO → VO */
    public UserCouponVO toVO(UserCoupon uc) {
        UserCouponVO vo = new UserCouponVO();
        vo.setId(uc.getId());
        vo.setUserId(uc.getUserId());
        vo.setCouponId(uc.getCouponId());
        vo.setCouponName(uc.getCouponName());
        vo.setDiscountValue(uc.getDiscountAmount());
        vo.setThresholdAmount(uc.getThresholdAmount());
        vo.setCreateTime(uc.getCreateTime());
        // 状态语义对齐：存储 0未使用/1已使用/2已过期 → 前端 1未使用/2已使用/3已过期
        vo.setStatus(uc.getStatus() == null ? null : uc.getStatus() + 1);
        return vo;
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