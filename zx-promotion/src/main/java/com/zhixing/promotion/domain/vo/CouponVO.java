package com.zhixing.promotion.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 优惠券视图对象（对齐前端 CouponVO 契约）。
 * <p>
 * 字段命名与 frontend zx-web/src/types/api.ts 的 CouponVO 一一对应：
 * discountValue=面值(分) 、remainNum=剩余可领数 、issueBeginTime/issueEndTime=发放起止时间。
 */
@Data
public class CouponVO implements Serializable {

    private Long id;

    /** 名称 */
    private String name;

    /** 折扣力度/面值（分）：满减券为面值，折扣券为折扣价位(如 8500=85 折) */
    private Long discountValue;

    /** 使用门槛（分），0 表示无门槛 */
    private Long thresholdAmount;

    /** 类型：1-通用券 2-秒杀券 */
    private Integer type;

    /** 状态：1-发放中 2-暂停发放/已结束 3-未开始 */
    private Integer status;

    /** 发放总量 */
    private Integer totalNum;

    /** 剩余可领取数量（totalNum - issuedNum） */
    private Integer remainNum;

    /** 发放开始时间 */
    private LocalDateTime issueBeginTime;

    /** 发放结束时间 */
    private LocalDateTime issueEndTime;
}