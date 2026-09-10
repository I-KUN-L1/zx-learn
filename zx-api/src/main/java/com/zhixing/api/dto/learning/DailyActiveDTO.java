package com.zhixing.api.dto.learning;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 日活统计 DTO（内部 Feign 接口，供学情看板等消费）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyActiveDTO {

    /** 日期（yyyy-MM-dd） */
    private String date;

    /** 当日活跃用户数（按学习记录去重用户） */
    private Long count;
}
