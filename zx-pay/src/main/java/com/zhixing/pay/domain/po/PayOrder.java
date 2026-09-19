package com.zhixing.pay.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 支付单（持久化）。
 * <p>
 * 原先是进程内 {@code LinkedHashMap}（访问序 LRU）：服务重启即丢、多实例状态不一致、
 * 溢出淘汰导致老单静默消失 —— 资金类数据不可接受。现落库到 {@code zx_pay.pay_order}，
 * 由唯一键 {@code uk_biz_order_no} 保证「一业务单只对应一张支付单」（重复申请复用而非新建）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pay_order")
public class PayOrder extends BasePO {

    /** 业务订单号（zx-trade 订单号），唯一 */
    private String bizOrderNo;

    /** 支付金额（分） */
    private Long amount;

    /** 渠道：1支付宝 2微信 */
    private Integer channel;

    /** 状态：0待支付 1已支付 2已关闭 */
    private Integer status;

    /** 收银台跳转地址 */
    private String payUrl;

    /** 渠道交易流水号 */
    private String payNo;

    /** 回调确认时间 */
    private LocalDateTime notifyTime;
}
