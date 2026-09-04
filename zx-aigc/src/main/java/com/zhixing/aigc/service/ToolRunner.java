package com.zhixing.aigc.service;

/**
 * 工具调用执行器：LLM 通过 Function Calling 请求调用工具时，由该接口回调执行并返回可被 LLM 消费的结果。
 */
@FunctionalInterface
public interface ToolRunner {

    /**
     * 执行工具，返回 JSON 字符串结果（供 LLM 二次阅读）。
     *
     * @param toolName   工具名，如 searchCourses
     * @param argumentsJson 工具参数（JSON）
     */
    String run(String toolName, String argumentsJson);
}