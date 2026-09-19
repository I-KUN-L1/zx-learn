package com.zhixing.insight.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 个性化学习路径推荐 VO（对齐前端 LearningPathVO 契约）。
 */
@Data
public class RecommendVO implements Serializable {

    /** 推荐理由/总结 */
    private String reason;

    /** 推荐学习路径步骤 */
    private List<StepDTO> steps;

    /**
     * 路径步骤项：{order, courseId, courseName, reason}
     */
    @Data
    public static class StepDTO implements Serializable {
        private Integer order;
        private Long courseId;
        private String courseName;
        private String reason;
    }
}
