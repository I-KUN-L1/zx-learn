package com.zhixing.promotion.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MQ 消费流水表（幂等第二层：消费端以 consume_key 唯一去重）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("consume_record")
public class ConsumeRecord extends BasePO {

    /** 消费幂等键（业务前缀 + 订单/领取标识） */
    private String consumeKey;

    /** 主题 */
    private String topic;

    /** tag */
    private String tag;

    /** 1-已消费 */
    private Integer status;
}
