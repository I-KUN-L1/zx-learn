package com.zhixing.trade.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 购物车项
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cart")
public class Cart extends BasePO {

    /** 用户 id */
    private Long userId;

    /** 课程 id */
    private Long courseId;

    /** 课程名称快照 */
    private String courseName;

    /** 课程价格（分）快照 */
    private Long coursePrice;
}