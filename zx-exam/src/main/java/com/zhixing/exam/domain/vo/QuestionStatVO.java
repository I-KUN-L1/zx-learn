package com.zhixing.exam.domain.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 题目维度答题统计 VO：教师端查看"每道题的正确率"，定位学员共性薄弱题目。
 */
@Data
public class QuestionStatVO implements Serializable {

    private Long questionId;

    private String questionName;

    private Long courseId;

    private String courseName;

    /** 作答人次 */
    private long totalCount;

    /** 答对人次 */
    private long correctCount;

    /** 正确率（百分比，保留 1 位小数） */
    private double accuracy;
}
