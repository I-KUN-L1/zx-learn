package com.zhixing.learning.domain.dto;

import lombok.Data;

/**
 * 学习进度提交表单
 */
@Data
public class LearningProgressDTO {

    /** 课程 id */
    private Long courseId;

    /** 课时 id */
    private Long lessonId;

    /** 学习进度（0-100） */
    private Integer progress;

    /** 本次学习时长（秒） */
    private Integer learnDuration;
}