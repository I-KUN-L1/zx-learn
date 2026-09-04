package com.zhixing.insight.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 个性化学习路径推荐 VO
 */
@Data
public class RecommendVO implements Serializable {

    private Long userId;

    /** 分析总结 */
    private String summary;

    /** 薄弱点 */
    private List<String> weakness;

    /** 推荐课程列表 */
    private List<CourseRecommendDTO> courses;

    /** 学习建议 */
    private List<String> suggestions;

    @Data
    public static class CourseRecommendDTO implements Serializable {
        private Long id;
        private String name;
        private Long price;
        private String reason;
    }
}
