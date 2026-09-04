package com.zhixing.aigc.agent;

import com.zhixing.aigc.service.LlmClient;
import org.springframework.stereotype.Component;

/**
 * 咨询 Agent：解答平台使用、课程学习相关问题
 */
@Component
public class ConsultAgent extends AbstractAgent {

    public ConsultAgent(LlmClient llmClient) {
        super(llmClient);
    }

    @Override
    public AgentType type() {
        return AgentType.CONSULT;
    }

    @Override
    protected String systemPrompt() {
        return "你是「知行智学」在线学习平台的智能助教。请用亲切、专业的口吻解答用户关于平台使用、课程学习、账号等方面的疑问。回答要简洁、条理清晰。";
    }
}
