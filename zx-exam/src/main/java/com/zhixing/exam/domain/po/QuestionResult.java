package com.zhixing.exam.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 答题记录（MySQL 持久化）。
 * <p>
 * 新增 {@code userAnswer} 与 {@code courseId}：
 * 前者支撑错题本"我的作答 vs 正确答案"对照，后者支撑按课程维度的正确率统计。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("question_result")
public class QuestionResult extends BasePO {

    /** 用户 id */
    private Long userId;

    /** 题目 id */
    private Long questionId;

    /** 题目名称（冗余快照，便于学情分析展示，无需回表题库） */
    private String questionName;

    /** 学员作答（选项字母，如 A / AB） */
    private String userAnswer;

    /** 课程 id（冗余，便于按课程统计） */
    private Long courseId;

    /** 是否答对 */
    private Boolean correct;

    /** 得分 */
    private Integer score;
}
