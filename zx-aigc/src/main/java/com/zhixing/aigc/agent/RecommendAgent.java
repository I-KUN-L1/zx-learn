package com.zhixing.aigc.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.api.dto.course.CourseSimpleInfoDTO;
import com.zhixing.aigc.service.LlmClient;
import com.zhixing.aigc.tools.CourseTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 推荐 Agent（函数调用增强版）：
 * 通过 Function Calling 让 LLM 调用真实工具 searchCourses(keyword) 查询课程库后再推荐，
 * 避免模型"幻觉编造"课程。
 */
@Slf4j
@Component
public class RecommendAgent extends AbstractAgent {

    private static final String TOOL_SEARCH_COURSES = "searchCourses";

    private final CourseTools courseTools;
    private final ObjectMapper objectMapper;

    public RecommendAgent(LlmClient llmClient, CourseTools courseTools, ObjectMapper objectMapper) {
        super(llmClient);
        this.courseTools = courseTools;
        this.objectMapper = objectMapper;
    }

    @Override
    public AgentType type() {
        return AgentType.RECOMMEND;
    }

    @Override
    protected String systemPrompt() {
        return "你是「知行智学」的课程推荐顾问。推荐前务必调用 searchCourses 工具查询课程库中的真实课程，"
                + "不要凭空编造课程。请根据用户学习目标与兴趣推荐，并说明推荐理由；信息不足可主动询问。";
    }

    @Override
    public String answer(ChatContext context) {
        return llmClient.chatWithTools(buildMessages(context), toolDefinitions(), this::runTool).block();
    }

    @Override
    public Flux<String> stream(ChatContext context) {
        String reply = llmClient.chatWithTools(buildMessages(context), toolDefinitions(), this::runTool).block();
        if (reply == null) {
            reply = "";
        }
        // 工具执行完成后得到最终答复，按小块切片流式推送
        return Flux.fromArray(reply.split("(?<=\\G.{8})"));
    }

    /**
     * 工具 JSON Schema 定义（OpenAI 兼容）
     */
    private List<Map<String, Object>> toolDefinitions() {
        Map<String, Object> parameters = Map.of(
                "type", "object",
                "properties", Map.of(
                        "keyword", Map.of(
                                "type", "string",
                                "description", "课程搜索关键字，如 Java、Python、微服务 等"),
                        "category", Map.of(
                                "type", "string",
                                "description", "课程分类 id（可选数字），用于精确过滤，如 100100301")),
                "required", List.of("keyword"));
        Map<String, Object> function = new HashMap<>();
        function.put("name", TOOL_SEARCH_COURSES);
        function.put("description", "根据关键字查询课程库中的真实课程列表，用于课程推荐");
        function.put("parameters", parameters);
        return List.of(Map.of("type", "function", "function", function));
    }

    /**
     * 工具执行：name=searchCourses，args={"keyword": "Java", "category": "100100301"}
     */
    private String runTool(String name, String argumentsJson) {
        try {
            if (!TOOL_SEARCH_COURSES.equals(name)) {
                return "{\"error\":\"未知工具\"}";
            }
            Map<String, Object> args = objectMapper.readValue(argumentsJson,
                    new TypeReference<Map<String, Object>>() {
                    });
            String keyword = args == null ? "" : String.valueOf(args.getOrDefault("keyword", ""));
            Long categoryId = toLong(args == null ? null : args.get("category"));
            List<CourseSimpleInfoDTO> courses = courseTools.searchCourses(keyword, categoryId);
            if (courses == null || courses.isEmpty()) {
                return "{\"courses\": \"未找到与「" + keyword + "」相关的课程\"}";
            }
            return objectMapper.writeValueAsString(courses);
        } catch (Exception e) {
            log.warn("工具执行失败：{}", e.getMessage());
            return "{\"error\":\"工具执行失败\"}";
        }
    }

    private Long toLong(Object o) {
        if (o == null) {
            return null;
        }
        try {
            String s = String.valueOf(o).trim();
            return s.isEmpty() ? null : Long.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}