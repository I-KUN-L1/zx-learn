package com.zhixing.course.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 课程名额（与课程表 1:1，course_id 唯一）。
 * <p>quota 为 NULL 表示不限名额；locked_count 为"已锁定未确认"的在途名额数。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("course_quota")
public class CourseQuota extends BasePO {

    /** 课程 id（唯一） */
    private Long courseId;

    /** 名额上限（NULL = 不限） */
    private Integer quota;

    /** 已锁定名额数（下单锁定，支付转销量 / 关单释放） */
    private Integer lockedCount;
}
