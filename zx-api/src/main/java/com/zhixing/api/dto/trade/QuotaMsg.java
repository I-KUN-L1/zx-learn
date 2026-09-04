package com.zhixing.api.dto.trade;

import lombok.Data;

import java.io.Serializable;

/**
 * 课程名额事件报文（topic: zx_course_quota，tag: LOCK / CONFIRM / RELEASE）。
 * <p>zx-trade 发布，zx-course 消费并维护 course_quota 锁定计数。</p>
 */
@Data
public class QuotaMsg implements Serializable {

    private Long orderId;
    private Long courseId;
    private Long userId;
}
