package com.zhixing.learning.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 积分明细。
 * <p>
 * 单一事实来源：用户积分 = 该用户全部明细的 SUM(points)，不额外维护汇总表，
 * 避免"汇总值与明细不一致"的双写风险。
 * <p>
 * 幂等：{@code (user_id, source, ref_id)} 唯一索引 —— 同一业务动作（同一小节完成、
 * 同一题目答对、同一天签到…）重复投递只入账一次，天然防刷且可安全重试。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("points_record")
public class PointsRecord extends BasePO {

    /** 用户 id */
    private Long userId;

    /** 积分变动（正数=获得） */
    private Integer points;

    /** 来源：LESSON/COURSE/QUIZ/SIGN/DISCUSSION/REPLY */
    private String source;

    /** 积分说明 */
    private String description;

    /** 业务幂等引用 */
    private String refId;
}
