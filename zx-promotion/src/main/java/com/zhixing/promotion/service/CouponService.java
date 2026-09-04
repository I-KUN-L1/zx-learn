package com.zhixing.promotion.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixing.common.domain.PageDTO;
import com.zhixing.common.domain.PageQuery;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.exceptions.BizIllegalException;
import com.zhixing.common.utils.BeanUtils;
import com.zhixing.common.utils.StringUtils;
import com.zhixing.promotion.domain.dto.CouponFormDTO;
import com.zhixing.promotion.domain.po.Coupon;
import com.zhixing.promotion.mapper.CouponMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 优惠券服务。
 * <p>
 * 状态机：0-未开始 -> 1-进行中 -> 2-已结束（按生效时间自动流转）
 *                  └------> 3-已下架（手动下架）
 * 未开始/已结束/已下架均不可领取；已结束状态在读取/操作时依据失效时间自动修正并落库。
 */
@Service
@RequiredArgsConstructor
public class CouponService {

    /** 未开始 */
    public static final int STATE_NOT_STARTED = 0;
    /** 进行中 */
    public static final int STATE_ONGOING = 1;
    /** 已结束 */
    public static final int STATE_ENDED = 2;
    /** 已下架 */
    public static final int STATE_OFF_SHELF = 3;

    private final CouponMapper couponMapper;

    public Long create(CouponFormDTO form) {
        if (StringUtils.isBlank(form.getName())) {
            throw new BadRequestException("优惠券名称不能为空");
        }
        if (form.getDiscountAmount() == null || form.getDiscountAmount() <= 0) {
            throw new BadRequestException("优惠券面值需大于 0");
        }
        if (form.getTotalNum() == null || form.getTotalNum() <= 0) {
            throw new BadRequestException("发行总量需大于 0");
        }
        Coupon coupon = BeanUtils.copyBean(form, Coupon.class);
        coupon.setIssuedNum(0);
        coupon.setStatus(STATE_NOT_STARTED);
        couponMapper.insert(coupon);
        return coupon.getId();
    }

    /**
     * 发放：进入"进行中"。仅允许从未开始 / 已下架 / 已结束流转到进行中，重复操作被拦截。
     */
    public void issue(Long id) {
        Coupon coupon = refreshState(id);
        if (Integer.valueOf(STATE_ONGOING).equals(coupon.getStatus())) {
            throw new BizIllegalException("优惠券已在进行中");
        }
        coupon.setStatus(STATE_ONGOING);
        couponMapper.updateById(coupon);
    }

    /**
     * 下架：由"进行中"/"未开始"进入"已下架"，重复下架被拦截。
     */
    public void pause(Long id) {
        Coupon coupon = refreshState(id);
        if (Integer.valueOf(STATE_OFF_SHELF).equals(coupon.getStatus())) {
            throw new BizIllegalException("优惠券已下架");
        }
        coupon.setStatus(STATE_OFF_SHELF);
        couponMapper.updateById(coupon);
    }

    public Coupon getById(Long id) {
        return refreshState(id);
    }

    public PageDTO<Coupon> page(PageQuery query, String name, Integer status) {
        Page<Coupon> page = couponMapper.selectPage(query.toMpPage("create_time", false),
                new LambdaQueryWrapper<Coupon>()
                        .like(StringUtils.isNotBlank(name), Coupon::getName, name)
                        .eq(status != null, Coupon::getStatus, status));
        return PageDTO.of(page);
    }

    public void delete(Long id) {
        require(id);
        couponMapper.deleteById(id);
    }

    /**
     * 按兑换码查询进行中的券（供兑换码一次性核销使用）
     */
    public Coupon requireByExchangeCode(String exchangeCode) {
        if (StringUtils.isBlank(exchangeCode)) {
            throw new BadRequestException("兑换码不能为空");
        }
        Coupon coupon = couponMapper.selectOne(new LambdaQueryWrapper<Coupon>()
                .eq(Coupon::getExchangeCode, exchangeCode));
        if (coupon == null) {
            throw new BadRequestException("兑换码无效");
        }
        return refreshState(coupon);
    }

    /**
     * 状态自动修正：当前时间超过失效时间且非"已下架"时，自动流转为"已结束"并落库。
     */
    public Coupon refreshState(Long id) {
        return refreshState(require(id));
    }

    private Coupon refreshState(Coupon coupon) {
        if (coupon.getValidEndTime() != null
                && LocalDateTime.now().isAfter(coupon.getValidEndTime())
                && !Integer.valueOf(STATE_OFF_SHELF).equals(coupon.getStatus())) {
            coupon.setStatus(STATE_ENDED);
            couponMapper.updateById(coupon);
        }
        return coupon;
    }

    private Coupon require(Long id) {
        Coupon coupon = couponMapper.selectById(id);
        if (coupon == null) {
            throw new BadRequestException("优惠券不存在");
        }
        return coupon;
    }
}