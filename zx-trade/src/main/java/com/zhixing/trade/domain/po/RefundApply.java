package com.zhixing.trade.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 退款申请表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("refund_apply")
public class RefundApply extends BasePO {

    /** 订单 id */
    private Long orderId;

    /** 用户 id */
    private Long userId;

    /** 课程 id */
    private Long courseId;

    /** 退款金额（分） */
    private Long amount;

    /** 退款原因 */
    private String reason;

    /** 状态：0-待审核 1-已通过 2-已拒绝 */
    private Integer status;

    /** 审核说明 */
    private String remark;
}