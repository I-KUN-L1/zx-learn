package com.zhixing.aigc.agent;

import com.zhixing.aigc.config.RagProperties;
import com.zhixing.aigc.domain.ChunkHit;
import com.zhixing.aigc.service.KnowledgeService;
import com.zhixing.aigc.service.LlmClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 知识 Agent 单测：RAG prompt 注入（含未命中强拒答）、回答末尾参考来源（流式/非流式）。
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeAgentTest {

    @Mock
    private LlmClient llmClient;
    @Mock
    private KnowledgeService knowledgeService;

    private KnowledgeAgent newAgent() {
        return new KnowledgeAgent(llmClient, knowledgeService, new RagProperties());
    }

    private ChunkHit hit(String title, double score) {
        ChunkHit h = new ChunkHit();
        h.setTitle(title);
        h.setContent("事务传播行为包括 REQUIRED、REQUIRES_NEW...");
        h.setScore(score);
        return h;
    }

    @Test
    void answerAppendsSourcesFooter() {
        when(knowledgeService.search(anyString(), anyInt()))
                .thenReturn(List.of(hit("第2章-事务管理讲义", 0.87)));
        when(llmClient.chat(anyList())).thenReturn(
                reactor.core.publisher.Mono.just("事务传播行为有 REQUIRED 等。"));

        String answer = newAgent().answer(context("事务传播行为有哪些？"));

        assertTrue(answer.startsWith("事务传播行为有 REQUIRED 等。"));
        assertTrue(answer.contains("参考来源"));
        assertTrue(answer.contains("第2章-事务管理讲义"));
        assertTrue(answer.contains("0.87"));
    }

    @Test
    void streamAppendsSourcesFooterAsLastEvent() {
        when(knowledgeService.search(anyString(), anyInt()))
                .thenReturn(List.of(hit("第2章-事务管理讲义", 0.87)));
        when(llmClient.chatStream(anyList())).thenReturn(Flux.just("回答", "内容"));

        Flux<String> flux = newAgent().stream(context("事务传播行为有哪些？"));

        StepVerifier.create(flux)
                .expectNext("回答", "内容")
                .assertNext(last -> {
                    assertTrue(last.contains("参考来源"));
                    assertTrue(last.contains("[1] 第2章-事务管理讲义"));
                })
                .verifyComplete();
    }

    @Test
    void noHitUsesStrongRefusalPromptWithoutFooter() {
        when(knowledgeService.search(anyString(), anyInt())).thenReturn(List.of());
        when(llmClient.chat(anyList())).thenReturn(reactor.core.publisher.Mono.just("拒答"));

        String answer = newAgent().answer(context("明天天气怎么样"));

        assertEquals("拒答", answer); // 无参考来源脚注
        // system prompt 注入"未检索到相关课程资料"，强化拒答约束
        org.mockito.Mockito.verify(llmClient).chat(org.mockito.ArgumentMatchers.argThat(msgs ->
                msgs.get(0).get("content").contains("未检索到相关课程资料")));
    }

    @Test
    void vectorStoreDownStillAnswers() {
        when(knowledgeService.search(anyString(), anyInt()))
                .thenThrow(new RuntimeException("pgvector 不可用"));
        when(llmClient.chat(anyList())).thenReturn(reactor.core.publisher.Mono.just("降级回答"));

        String answer = newAgent().answer(context("事务"));

        assertEquals("降级回答", answer);
        assertFalse(answer.contains("参考来源"));
    }

    private ChatContext context(String question) {
        ChatContext ctx = new ChatContext();
        ctx.setQuestion(question);
        return ctx;
    }
}
