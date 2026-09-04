package com.zhixing.aigc.memory;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 会话消息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RedisMessage implements Serializable {

    private String role;
    private String content;
}
