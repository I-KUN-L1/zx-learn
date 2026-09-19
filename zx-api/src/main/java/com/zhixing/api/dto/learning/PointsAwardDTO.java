package com.zhixing.api.dto.learning;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 积分授予请求（服务间内部调用）。
 * <p>
 * 幂等契约：{@code source + refId} 在同一用户下唯一，重复投递只会入账一次，
 * 调用方无需先查询再写入，失败可安全重试。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PointsAwardDTO implements Serializable {

    /** 目标用户 id */
    private Long userId;

    /** 来源标识（LESSON/COURSE/QUIZ/SIGN/DISCUSSION/REPLY） */
    private String source;

    /** 积分值（正数=获得） */
    private Integer points;

    /** 积分说明（展示在积分明细） */
    private String description;

    /** 业务幂等引用（如课程:小节、题目 id、帖子 id） */
    private String refId;
}
