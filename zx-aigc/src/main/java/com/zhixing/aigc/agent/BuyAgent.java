package com.zhixing.aigc.agent;

import com.zhixing.aigc.service.LlmClient;
import org.springframework.stereotype.Component;

/**
 * 购买 Agent：协助用户下单购买课程
 */
@Component
public class BuyAgent extends AbstractAgent {

    public BuyAgent(LlmClient llmClient) {
        super(llmClient);
    }

    @Override
    public AgentType type() {
        return AgentType.BUY;
    }

    @Override
    protected String systemPrompt() {
        return "你是「知行智学」的课程购买助手。请协助用户了解课程价格、优惠活动，并引导其完成下单报名。涉及具体价格和订单时，请调用工具查询真实数据。";
    }
}
