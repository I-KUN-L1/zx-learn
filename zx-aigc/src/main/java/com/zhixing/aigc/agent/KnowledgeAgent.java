package com.zhixing.aigc.agent;

import com.zhixing.aigc.config.RagProperties;
import com.zhixing.aigc.domain.ChunkHit;
import com.zhixing.aigc.service.KnowledgeService;
import com.zhixing.aigc.service.LlmClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 知识 Agent（RAG 增强版）：
 * <ul>
 *   <li>提问时先做向量检索 Top3（minScore 阈值过滤），把命中的课程资料片段注入 System Prompt；</li>
 *   <li>System Prompt 强约束"仅基于课程资料回答，超出范围礼貌拒答"，抑制幻觉；</li>
 *   <li>回答（流式/非流式）末尾附加"参考来源"，让回答可溯源。</li>
 * </ul>
 */
@Slf4j
@Component
public class KnowledgeAgent extends AbstractAgent {

    private final KnowledgeService knowledgeService;
    private final RagProperties ragProperties;

    public KnowledgeAgent(LlmClient llmClient,
                          KnowledgeService knowledgeService,
                          RagProperties ragProperties) {
        super(llmClient);
        this.knowledgeService = knowledgeService;
        this.ragProperties = ragProperties;
    }

    @Override
    public AgentType type() {
        return AgentType.KNOWLEDGE;
    }

    @Override
    protected String systemPrompt() {
        return baseRagPrompt("");
    }

    @Override
    public String answer(ChatContext context) {
        retrieve(context);
        String answer = super.answer(context);
        String footer = sourcesFooter(context.getSources());
        return footer.isEmpty() ? answer : answer + footer;
    }

    @Override
    public Flux<String> stream(ChatContext context) {
        retrieve(context);
        String footer = sourcesFooter(context.getSources());
        Flux<String> body = super.stream(context);
        return footer.isEmpty() ? body : body.concatWith(Flux.just(footer));
    }

    /**
     * 组装 messages：向量检索资料片段并注入系统提示词（检索结果已缓存在 context，不重复检索）。
     */
    @Override
    protected List<Map<String, String>> buildMessages(ChatContext context) {
        List<ChunkHit> hits = retrieve(context);
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPromptWithRag(hits)));
        if (context.getHistory() != null) {
            messages.addAll(context.getHistory());
        }
        messages.add(Map.of("role", "user", "content", context.getQuestion()));
        return messages;
    }

    /**
     * 向量检索（每次对话只检索一次，prompt 注入与参考来源共用结果）。
     */
    private List<ChunkHit> retrieve(ChatContext context) {
        if (context.getSources() != null) {
            return context.getSources();
        }
        try {
            context.setSources(knowledgeService.search(context.getQuestion(), ragProperties.getTopK()));
        } catch (Exception e) {
            // 向量库不可用时降级，不阻断对话
            log.warn("向量检索失败，降级为通用回答：{}", e.getMessage());
            context.setSources(List.of());
        }
        return context.getSources();
    }

    /**
     * 返回带课程资料上下文的 system 提示词。
     */
    protected String systemPromptWithRag(List<ChunkHit> hits) {
        if (hits == null || hits.isEmpty()) {
            // 未命中资料：明确告知无资料可依，强化拒答约束
            return baseRagPrompt("\n\n=== 课程资料 ===\n（本次未检索到相关课程资料。）");
        }
        StringBuilder ctx = new StringBuilder("\n\n=== 课程资料（仅参考以下资料回答） ===\n");
        for (ChunkHit hit : hits) {
            ctx.append("【资料】").append(hit.getTitle() == null ? "讲义" : hit.getTitle())
                    .append("\n").append(hit.getContent()).append("\n");
        }
        return baseRagPrompt(ctx.toString());
    }

    private String baseRagPrompt(String retrievedContext) {
        String constraint = "若课程资料不含相关答案或问题超出课程资料范围（如时政、代码评审、个人主观建议等），"
                + "请礼貌回复：「该问题不在课程资料范围内，我暂无法准确回答，建议查阅课程讲义或咨询讲师。」"
                + "严禁编造或脱离课程资料臆测。";
        return "你是「知行智学」的专业知识助教。仅基于以下课程资料回答学员问题，回答要准确、通俗、条理清晰。\n"
                + constraint
                + retrievedContext;
    }

    /**
     * 参考来源脚注：命中片段的标题与相似度，附在回答末尾实现可溯源。
     * 未命中（触发拒答）时返回空串。
     */
    private String sourcesFooter(List<ChunkHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n\n———\n📚 参考来源：\n");
        for (int i = 0; i < hits.size(); i++) {
            ChunkHit hit = hits.get(i);
            sb.append("[").append(i + 1).append("] ")
                    .append(hit.getTitle() == null || hit.getTitle().isBlank() ? "课程讲义" : hit.getTitle())
                    .append(String.format("（相似度 %.2f）", hit.getScore()))
                    .append("\n");
        }
        return sb.toString();
    }
}
