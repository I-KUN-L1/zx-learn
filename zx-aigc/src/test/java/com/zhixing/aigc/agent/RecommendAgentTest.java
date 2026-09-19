package com.zhixing.aigc.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.api.dto.course.CourseSimpleInfoDTO;
import com.zhixing.aigc.service.LlmClient;
import com.zhixing.aigc.service.ToolRunner;
import com.zhixing.aigc.tools.CourseTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * 推荐 Agent 单元测试：
 * <ul>
 *   <li>响应式 stream（修复点：曾在 Netty 事件循环线程上 block() 触发
 *       IllegalStateException，改造为 flatMapMany 纯响应式切片推送）；</li>
 *   <li>Function Calling 工具链路：searchCourses 的参数解析、类型转换、
 *       未知工具防御与坏参容错。</li>
 * </ul>
 */
class RecommendAgentTest {

    private static final String REPLY = "推荐 Java 21 核心技术：从入门到精通，理由：体系完整";

    private LlmClient llmClient;
    private CourseTools courseTools;
    private RecommendAgent agent;

    /** 捕获 chatWithTools 入参，用于直测 runTool 链路 */
    private List<Map<String, Object>> capturedTools;
    private ToolRunner capturedRunner;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        llmClient = mock(LlmClient.class);
        courseTools = mock(CourseTools.class);
        agent = new RecommendAgent(llmClient, courseTools, new ObjectMapper());
        when(llmClient.chatWithTools(anyList(), anyList(), any()))
                .thenAnswer(inv -> {
                    capturedTools = (List<Map<String, Object>>) (List<?>) inv.getArgument(1);
                    capturedRunner = (ToolRunner) inv.getArgument(2);
                    return Mono.just(REPLY);
                });
    }

    private ChatContext context(String question) {
        ChatContext ctx = new ChatContext();
        ctx.setUserId(1L);
        ctx.setSessionId("s-1");
        ctx.setQuestion(question);
        return ctx;
    }

    @Test
    void streamEmitsCompleteReplyWithoutBlocking() {
        // 修复回归：stream 为纯响应式（不 block），切片推送拼接后应完整还原回复
        StepVerifier.create(agent.stream(context("推荐一门 Java 课程")))
                .recordWith(ArrayList::new)
                .thenConsumeWhile(s -> true)
                .expectRecordedMatches(chunks -> REPLY.equals(String.join("", chunks)))
                .verifyComplete();
    }

    @Test
    void streamCompletesWhenLlmReturnsEmpty() {
        when(llmClient.chatWithTools(anyList(), anyList(), any())).thenReturn(Mono.empty());
        StepVerifier.create(agent.stream(context("推荐课程")))
                .expectNextCount(0)
                .verifyComplete();
    }

    @Test
    void answerReturnsLlmReply() {
        assertEquals(REPLY, agent.answer(context("推荐一门 Java 课程")));
    }

    @Test
    void toolDefinitionDeclaresSearchCourses() throws Exception {
        agent.answer(context("推荐课程"));

        String json = new ObjectMapper().writeValueAsString(capturedTools);
        assertTrue(json.contains("searchCourses"), "应向 LLM 声明 searchCourses 工具");
        assertTrue(json.contains("keyword"), "工具参数 schema 应包含 keyword");
    }

    @Test
    void runToolSearchesCoursesWithKeywordAndCategory() {
        CourseSimpleInfoDTO course = new CourseSimpleInfoDTO();
        course.setId(3001L);
        course.setName("Java 21 核心技术：从入门到精通");
        when(courseTools.searchCourses("Java", 11L)).thenReturn(List.of(course));

        agent.answer(context("推荐课程"));
        String result = capturedRunner.run("searchCourses",
                "{\"keyword\":\"Java\",\"category\":11}");

        assertTrue(result.contains("Java 21 核心技术"));
        verify(courseTools).searchCourses("Java", 11L);
    }

    @Test
    void runToolConvertsNumericStringCategory() {
        // LLM 可能把分类 id 序列化为字符串，应容错转换为 Long
        when(courseTools.searchCourses("Go", 13L)).thenReturn(List.of());

        agent.answer(context("推荐课程"));
        capturedRunner.run("searchCourses", "{\"keyword\":\"Go\",\"category\":\"13\"}");

        verify(courseTools).searchCourses("Go", 13L);
    }

    @Test
    void runToolReturnsHintWhenNoCourseMatched() {
        when(courseTools.searchCourses("Rust", null)).thenReturn(List.of());

        agent.answer(context("推荐课程"));
        String result = capturedRunner.run("searchCourses", "{\"keyword\":\"Rust\"}");

        assertTrue(result.contains("未找到"), "空结果应返回未找到提示而非空串");
    }

    @Test
    void runToolRejectsUnknownToolName() {
        agent.answer(context("推荐课程"));

        assertEquals("{\"error\":\"未知工具\"}", capturedRunner.run("deleteAll", "{}"));
        verifyNoInteractions(courseTools);
    }

    @Test
    void runToolToleratesInvalidArgumentsJson() {
        agent.answer(context("推荐课程"));

        assertEquals("{\"error\":\"工具执行失败\"}", capturedRunner.run("searchCourses", "not-json"));
    }
}
