package com.zhixing.api.dto.learning;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 学习记录 DTO（跨服务）
 */
@Data
public class LearningRecordDTO implements Serializable {

    private Long id;
    private Long userId;
    private Long courseId;
    private Long lessonId;
    private Integer progress;
    private Boolean finished;
    private Integer learnDuration;
    private LocalDateTime lastLearnTime;
}
