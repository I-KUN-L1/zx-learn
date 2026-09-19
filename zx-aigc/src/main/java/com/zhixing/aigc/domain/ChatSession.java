package com.zhixing.aigc.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话（JSON 契约对齐前端 ChatSession：主键字段为 id）
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatSession {

    /** 会话 ID（前端以 id 读取） */
    private String id;
    private String title;
    private Long userId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
