package com.zhixing.insight.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 能力画像 VO（对齐前端 InsightProfileVO 契约）。
 * 雷达图能力维度 + 近 7 日学习时长趋势 + 总览指标。
 */
@Data
public class ProfileVO implements Serializable {

    private Long userId;

    /** 累计学习时长（分钟） */
    private long totalDuration;

    /** 课程完成率（百分比） */
    private int completedRate;

    /** 连续打卡天数 */
    private int continuousDays;

    /** 能力维度雷达图数据 */
    private List<AbilityDTO> abilities;

    /** 近 7 日学习时长趋势（分钟） */
    private List<TrendDTO> trends;

    /**
     * 能力维度项：{name, value(0-100)}
     */
    @Data
    public static class AbilityDTO implements Serializable {
        private String name;
        private Integer value;

        public AbilityDTO() {
        }

        public AbilityDTO(String name, Integer value) {
            this.name = name;
            this.value = value;
        }
    }

    /**
     * 趋势项：{date(M/d), duration(分钟)}
     */
    @Data
    public static class TrendDTO implements Serializable {
        private String date;
        private Long duration;

        public TrendDTO() {
        }

        public TrendDTO(String date, Long duration) {
            this.date = date;
            this.duration = duration;
        }
    }
}
