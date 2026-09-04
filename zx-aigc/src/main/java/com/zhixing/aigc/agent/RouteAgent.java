package com.zhixing.aigc.agent;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 路由 Agent：根据用户意图分发到具体 Agent
 */
@Component
public class RouteAgent {

    private final Map<AgentType, AbstractAgent> agents = new HashMap<>();

    public RouteAgent(ConsultAgent consult, RecommendAgent recommend,
                      KnowledgeAgent knowledge, BuyAgent buy) {
        agents.put(AgentType.CONSULT, consult);
        agents.put(AgentType.RECOMMEND, recommend);
        agents.put(AgentType.KNOWLEDGE, knowledge);
        agents.put(AgentType.BUY, buy);
    }

    /**
     * 意图识别：基于关键词匹配
     */
    public AgentType route(String question) {
        if (question == null) {
            return AgentType.CONSULT;
        }
        String q = question.toLowerCase();
        if (containsAny(q, "推荐", "介绍课程", "有什么课", "学什么", "适合", "报什么", "推荐课程", "好课")) {
            return AgentType.RECOMMEND;
        }
        if (containsAny(q, "买", "购买", "下单", "报名", "价格", "多少钱", "优惠", "支付", "券")) {
            return AgentType.BUY;
        }
        if (containsAny(q, "知识点", "概念", "原理", "是什么", "什么是", "什么意思", "为什么", "怎么学", "解释", "定义", "有哪些", "区别")) {
            return AgentType.KNOWLEDGE;
        }
        return AgentType.CONSULT;
    }

    public AbstractAgent getAgent(AgentType type) {
        return agents.getOrDefault(type, agents.get(AgentType.CONSULT));
    }

    public Map<AgentType, AbstractAgent> getAgents() {
        return agents;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) {
                return true;
            }
        }
        return false;
    }
}
