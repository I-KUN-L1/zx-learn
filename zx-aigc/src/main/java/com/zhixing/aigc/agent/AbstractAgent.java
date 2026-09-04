package com.zhixing.aigc.agent;

import com.zhixing.aigc.service.LlmClient;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent 抽象基类
 */
public abstract class AbstractAgent {

    protected final LlmClient llmClient;

    protected AbstractAgent(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    /**
     * 该 Agent 负责处理的意图类型
     */
    public abstract AgentType type();

    /**
     * 系统提示词
     */
    protected abstract String systemPrompt();

    /**
     * 非流式回答
     */
    public String answer(ChatContext context) {
        return llmClient.chat(buildMessages(context)).block();
    }

    /**
     * 流式回答
     */
    public Flux<String> stream(ChatContext context) {
        return llmClient.chatStream(buildMessages(context));
    }

    protected List<Map<String, String>> buildMessages(ChatContext context) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt()));
        for (Map<String, String> h : context.getHistory()) {
            messages.add(h);
        }
        messages.add(Map.of("role", "user", "content", context.getQuestion()));
        return messages;
    }
}
