package com.zhixing.api.client.learning;

import com.zhixing.api.dto.learning.PointsAwardDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 学习服务 - 积分能力客户端（服务间内部调用）。
 * <p>
 * 供 zx-exam（测验答对加分）等服务调用。端点标注 {@code @NoWrapper} + 内部调用守卫，
 * 外部用户经网关访问一律 403。加分失败由调用方自行 try/catch 降级，
 * 不阻断主业务（例如答题提交必须成功）。
 */
@FeignClient(value = "learning-service", contextId = "learningPointsClient")
public interface PointsClient {

    /**
     * 授予积分（幂等：同一 user + source + refId 仅入账一次）。
     *
     * @return 本次是否真正入账（重复投递返回 false）
     */
    @PostMapping("/points/award")
    Boolean award(@RequestBody PointsAwardDTO award);

    /**
     * 查询指定用户积分总额（0 表示无记录）
     */
    @GetMapping("/points/users/{userId}/total")
    Long totalOf(@PathVariable("userId") Long userId);
}
