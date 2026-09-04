package com.zhixing.exam.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 答题记录（升级：内存 → MySQL 持久化）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("question_result")
public class QuestionResult extends BasePO {

    /** 用户 id */
    private Long userId;

    /** 题目 id */
    private Long questionId;

    /** 题目名称（冗余快照，便于学情分析展示） */
    private String questionName;

    /** 是否答对 */
    private Boolean correct;

    /** 得分 */
    private Integer score;
}
