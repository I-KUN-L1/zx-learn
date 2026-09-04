package com.zhixing.learning.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 学习进度记录（升级：内存 → MySQL 持久化）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("learning_record")
public class LearningRecord extends BasePO {

    /** 用户 id */
    private Long userId;

    /** 课程 id */
    private Long courseId;

    /** 课时 id */
    private Long lessonId;

    /** 学习进度（0-100） */
    private Integer progress;

    /** 是否学完 */
    private Boolean finished;

    /** 本次学习时长（秒） */
    private Integer learnDuration;

    /** 最近学习时间 */
    private LocalDateTime lastLearnTime;
}
