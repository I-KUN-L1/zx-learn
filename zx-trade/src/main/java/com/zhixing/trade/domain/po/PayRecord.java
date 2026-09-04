package com.zhixing.trade.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 交易支付回调流水表：pay_no 唯一，用于支付回调幂等
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("trade_pay_record")
public class PayRecord extends BasePO {

    /** 订单 id */
    private Long orderId;

    /** 渠道交易流水号 */
    private String payNo;

    /** 支付方式 */
    private Integer payType;

    /** 支付金额（分） */
    private Long amount;

    /** 0-处理中 1-成功 */
    private Integer status;

    /** 回调时间 */
    private LocalDateTime callbackTime;

    /** 回调原始报文 */
    private String raw;
}