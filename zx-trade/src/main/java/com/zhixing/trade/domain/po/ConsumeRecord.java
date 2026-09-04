package com.zhixing.trade.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * MQ 消费流水表：消费端以 consume_key 唯一去重，实现幂等（第二层）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("consume_record")
public class ConsumeRecord extends BasePO {

    /** 消费幂等键（消息 key） */
    private String consumeKey;

    /** 主题 */
    private String topic;

    /** tag */
    private String tag;

    /** 1-已消费 */
    private Integer status;
}