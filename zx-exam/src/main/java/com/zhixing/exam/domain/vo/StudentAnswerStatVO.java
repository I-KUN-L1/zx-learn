package com.zhixing.exam.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 学员维度答题统计 VO：教师端查看"每位学员的答题情况与正确率"，形成教与学闭环。
 */
@Data
public class StudentAnswerStatVO implements Serializable {

    private Long userId;

    private String username;

    private String cellPhone;

    /** 答题总数 */
    private long totalCount;

    /** 答对题数 */
    private long correctCount;

    /** 正确率（百分比，保留 1 位小数） */
    private double accuracy;

    /** 最近答题时间 */
    private LocalDateTime lastAnswerTime;
}
