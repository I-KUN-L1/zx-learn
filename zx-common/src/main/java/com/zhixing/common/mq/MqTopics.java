package com.zhixing.common.mq;

/**
 * RocketMQ 主题 / Tag 全局命名规范（zx_ 前缀 + 下划线分隔，Tag 全大写）。
 * <p>
 * 所有业务模块统一引用此类，禁止散落硬编码；新增主题须在此登记。
 */
public interface MqTopics {

    /** 订单支付成功事件（zx-trade 发布 → zx-learning 消费开通课程） */
    String TOPIC_ORDER_PAID = "zx_order_paid";
    /** 优惠券核销 / 退回事件（zx-trade 内部异步落库） */
    String TOPIC_COUPON_USE = "zx_coupon_use";
    /** 订单超时关单（延迟消息，zx-trade 内部消费） */
    String TOPIC_ORDER_TIMEOUT = "zx_order_timeout";
    /** 课程名额锁定 / 确认 / 释放（zx-trade 发布 → zx-course 消费） */
    String TOPIC_COURSE_QUOTA = "zx_course_quota";
    /** 优惠券秒杀领取（zx-promotion 发布 → zx-promotion 消费异步落库） */
    String TOPIC_SECKILL_CLAIM = "zx_seckill_claim";

    interface Tags {
        String COUPON_USE = "USE";
        String COUPON_REFUND = "REFUND";
        String ORDER_CLOSE = "CLOSE";
        String ORDER_PAID = "PAID";
        /** 锁定名额（下单） */
        String QUOTA_LOCK = "LOCK";
        /** 确认名额（支付成功：锁定转销量） */
        String QUOTA_CONFIRM = "CONFIRM";
        /** 释放名额（超时关单 / 取消） */
        String QUOTA_RELEASE = "RELEASE";
        /** 秒杀领取（Redis 预扣成功后异步落库） */
        String SECKILL_CLAIM = "CLAIM";
    }
}
