package com.zhixing.aigc.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.aigc.config.LlmProperties;
import com.zhixing.aigc.config.RagProperties;
import com.zhixing.aigc.service.KnowledgeService;
import com.zhixing.aigc.service.LlmClient;
import com.zhixing.aigc.tools.CourseTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * 意图路由 Agent 单元测试：验证关键词路由逻辑
 */
class RouteAgentTest {

    private RouteAgent routeAgent;

    @BeforeEach
    void setUp() {
        LlmClient llmClient = new LlmClient(new LlmProperties());
        routeAgent = new RouteAgent(
                new ConsultAgent(llmClient),
                new RecommendAgent(llmClient, mock(CourseTools.class), new ObjectMapper()),
                new KnowledgeAgent(llmClient, mock(KnowledgeService.class), new RagProperties()),
                new BuyAgent(llmClient));
    }

    @Test
    void routeRecommendForCourseRecommendation() {
        assertEquals(AgentType.RECOMMEND, routeAgent.route("帮我推荐一门 Java 课程"));
        assertEquals(AgentType.RECOMMEND, routeAgent.route("有什么好课推荐"));
    }

    @Test
    void routeBuyForPurchaseIntent() {
        assertEquals(AgentType.BUY, routeAgent.route("我想购买这门课程"));
        assertEquals(AgentType.BUY, routeAgent.route("这门课多少钱"));
    }

    @Test
    void routeKnowledgeForConceptQuestions() {
        assertEquals(AgentType.KNOWLEDGE, routeAgent.route("什么是微服务架构"));
        assertEquals(AgentType.KNOWLEDGE, routeAgent.route("解释一下 JWT 的原理"));
    }

    @Test
    void routeConsultAsDefault() {
        assertEquals(AgentType.CONSULT, routeAgent.route("你好呀"));
        assertEquals(AgentType.CONSULT, routeAgent.route(null));
    }

    @Test
    void getAgentReturnsCorrespondingAgent() {
        assertNotNull(routeAgent.getAgent(AgentType.CONSULT));
        assertNotNull(routeAgent.getAgent(AgentType.RECOMMEND));
        assertEquals(AgentType.CONSULT, routeAgent.getAgent(AgentType.CONSULT).type());
    }
}
