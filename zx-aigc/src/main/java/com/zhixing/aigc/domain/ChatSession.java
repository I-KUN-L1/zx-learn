package com.zhixing.aigc.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话
 */
@Data
public class ChatSession {

    private String sessionId;
    private String title;
    private Long userId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
