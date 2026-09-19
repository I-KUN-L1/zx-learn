package com.zhixing.pay.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 支付回调流水（幂等凭证）。
 * <p>
 * 唯一键 {@code uk_channel_pay_no (channel, pay_no)} 保证同一笔渠道回调只受理一次：
 * 渠道重推（超时重试是其标准行为）时第二次直接命中唯一键，不再重复改单。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pay_notify_log")
public class PayNotifyLog extends BasePO {

    /** 渠道：alipay / wxpay */
    private String channel;

    /** 业务订单号 */
    private String bizOrderNo;

    /** 渠道交易流水号 */
    private String payNo;

    /** 验签结果：0失败 1通过 */
    private Integer verifyResult;

    /** 回调原始报文（截断至 1024） */
    private String raw;
}
