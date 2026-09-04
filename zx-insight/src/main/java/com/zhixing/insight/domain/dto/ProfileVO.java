package com.zhixing.insight.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 能力画像 VO（雷达图数据）
 */
@Data
public class ProfileVO implements Serializable {

    private Long userId;

    /** 学习等级 */
    private String level;

    /** 多维度评分：{学习投入度:xx, 学习完成度:xx, 答题能力:xx, 知识广度:xx, 综合理解力:xx} */
    private Map<String, Integer> dimensions;
}
