package com.zhixing.learning.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 我的课表（用户看课清单）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lesson")
public class Lesson extends BasePO {

    /** 用户 id */
    private Long userId;

    /** 课程 id */
    private Long courseId;

    /** 课程名称快照 */
    private String courseName;

    /** 学习计划（JSON） */
    private String plan;
}