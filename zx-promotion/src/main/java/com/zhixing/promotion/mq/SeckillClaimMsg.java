package com.zhixing.promotion.mq;

import lombok.Data;

import java.io.Serializable;

/**
 * 秒杀领取消息报文（topic: zx_seckill_claim，tag: CLAIM）
 */
@Data
public class SeckillClaimMsg implements Serializable {

    private Long couponId;
    private Long userId;
}
