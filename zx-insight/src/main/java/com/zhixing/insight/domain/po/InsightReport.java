package com.zhixing.insight.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 学情报告
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("insight_report")
public class InsightReport extends BasePO {

    /** 用户 id */
    private Long userId;

    /** 报告日期 */
    private LocalDate reportDate;

    /** 学习投入度 0-100 */
    private Integer engagement;

    /** 学习完成度 0-100 */
    private Integer completion;

    /** 答题能力 0-100 */
    private Integer quizAbility;

    /** 知识广度 0-100 */
    private Integer breadth;

    /** 综合理解力 0-100 */
    private Integer comprehension;

    /** 薄弱点 JSON 数组字符串 */
    private String weakness;

    /** 学习建议 JSON 数组字符串 */
    private String recommendations;

    /** AI 总结 */
    private String summary;

    /** 是否由大模型生成（false 为规则引擎生成） */
    private Boolean aiGenerated;
}
