package com.zhixing.aigc.agent;

import com.zhixing.aigc.domain.ChunkHit;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 对话上下文
 */
@Data
public class ChatContext {

    private Long userId;
    private String sessionId;
    private String question;
    /** 历史消息 [{role: user/assistant, content: ...}] */
    private List<Map<String, String>> history = new ArrayList<>();
    /** RAG 检索命中的课程资料片段（KnowledgeAgent 检索一次后复用：注入 prompt + 回答末尾附参考来源） */
    private List<ChunkHit> sources;
}
