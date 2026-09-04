package com.zhixing.course.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 课程名额变更流水（按订单维度记录 LOCK → CONFIRM / RELEASE 生命周期，order_id 唯一幂等）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("course_quota_record")
public class CourseQuotaRecord extends BasePO {

    /** 订单 id（唯一，防重复锁定/确认/释放） */
    private Long orderId;

    /** 课程 id */
    private Long courseId;

    /** 用户 id */
    private Long userId;

    /** 状态：1-已锁定 2-已确认(转销量) 0-已释放 */
    private Integer status;
}
