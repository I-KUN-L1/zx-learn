package com.zhixing.aigc.agent;

/**
 * Agent 类型
 */
public enum AgentType {

    CONSULT("咨询", "解答平台使用、课程学习、账号等相关问题"),
    RECOMMEND("推荐", "根据用户兴趣推荐合适的课程"),
    KNOWLEDGE("知识", "基于课程知识库回答专业知识问题"),
    BUY("购买", "协助用户下单购买、报名课程");

    private final String name;
    private final String desc;

    AgentType(String name, String desc) {
        this.name = name;
        this.desc = desc;
    }

    public String getName() {
        return name;
    }

    public String getDesc() {
        return desc;
    }
}
