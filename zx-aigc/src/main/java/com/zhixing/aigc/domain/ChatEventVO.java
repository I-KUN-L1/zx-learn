package com.zhixing.aigc.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天事件（SSE 推送）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatEventVO {

    /** 事件类型：START / DELTA / END */
    private String type;
    /** 增量文本 */
    private String content;
    /** 命中的 Agent */
    private String agent;

    public static ChatEventVO start(String agent) {
        return new ChatEventVO("START", "", agent);
    }

    public static ChatEventVO delta(String content) {
        return new ChatEventVO("DELTA", content, null);
    }

    public static ChatEventVO end() {
        return new ChatEventVO("END", "", null);
    }
}
