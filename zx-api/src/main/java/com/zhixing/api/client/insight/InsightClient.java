package com.zhixing.api.client.insight;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 智能学情分析服务客户端
 */
@FeignClient(value = "insight-service", contextId = "insightClient")
public interface InsightClient {

    /**
     * 生成并返回用户最新学情报告
     */
    @PostMapping("/insight/reports/generate/{userId}")
    Long generateReport(@PathVariable("userId") Long userId);

    /**
     * 查询用户能力画像（JSON 字符串）
     */
    @GetMapping("/insight/profiles/{userId}")
    Object getProfile(@PathVariable("userId") Long userId);
}
