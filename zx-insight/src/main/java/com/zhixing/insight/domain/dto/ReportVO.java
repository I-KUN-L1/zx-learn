package com.zhixing.insight.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 学情报告 VO
 */
@Data
public class ReportVO implements Serializable {

    private Long id;

    private Long userId;

    private LocalDate reportDate;

    /** 多维度评分 */
    private Map<String, Integer> dimensions;

    /** 薄弱点 */
    private List<String> weakness;

    /** 学习建议 */
    private List<String> recommendations;

    /** 分析总结 */
    private String summary;

    /** 是否由大模型生成 */
    private Boolean aiGenerated;
}
