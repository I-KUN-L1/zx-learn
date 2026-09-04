package com.zhixing.api.client.remark;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 点赞服务客户端
 */
@FeignClient(value = "remark-service", contextId = "remarkClient")
public interface RemarkClient {

    @GetMapping("/likes/list")
    Map<Long, Boolean> queryLikeStatus(@RequestParam("bizIds") java.util.List<Long> bizIds);
}
