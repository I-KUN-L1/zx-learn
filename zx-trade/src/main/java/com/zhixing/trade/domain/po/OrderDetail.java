package com.zhixing.trade.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订单明细
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("trade_order_detail")
public class OrderDetail extends BasePO {

    /** 订单 id */
    private Long orderId;

    /** 课程 id */
    private Long courseId;

    /** 课程名称快照 */
    private String name;

    /** 价格（分） */
    private Long price;
}