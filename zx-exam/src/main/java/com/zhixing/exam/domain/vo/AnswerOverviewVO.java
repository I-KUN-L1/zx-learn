package com.zhixing.exam.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 教师端答题情况总览 VO：形成"教 → 学 → 评"闭环的教师侧视图。
 */
@Data
public class AnswerOverviewVO implements Serializable {

    /** 答题记录总数 */
    private long totalRecords;

    /** 参与答题的学员数 */
    private long totalStudents;

    /** 被作答过的题目数 */
    private long totalQuestions;

    /** 整体正确率（%） */
    private double accuracy;

    /** 题目维度：每道题的正确率（定位共性薄弱题） */
    private List<QuestionStatVO> questionStats;

    /** 学员维度：每位学员的答题情况与正确率 */
    private List<StudentAnswerStatVO> studentStats;
}
