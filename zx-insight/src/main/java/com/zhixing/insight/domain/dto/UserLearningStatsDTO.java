package com.zhixing.insight.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 用户学习统计数据（跨服务聚合结果）
 */
@Data
public class UserLearningStatsDTO implements Serializable {

    private Long userId;

    /** 学习总时长（秒） */
    private long totalDuration;

    /** 学习课程数 */
    private int courseCount;

    /** 完成课程数 */
    private int finishedCourseCount;

    /** 平均学习进度 0-100 */
    private int avgProgress;

    /** 答题总数 */
    private long quizCount;

    /** 答对数 */
    private long quizCorrect;

    /** 答题正确率 % */
    private double quizAccuracy;

    /** 活跃天数（基于学习记录数估算） */
    private int activeDays;

    /** 学习过的课程 id 列表 */
    private List<Long> courseIds;

    /** 近 7 日每日学习时长（秒），key=yyyy-MM-dd（按最后学习时间归属日期） */
    private Map<String, Long> dailyDurations;

    /** 连续签到天数 */
    private int signStreak;
}
