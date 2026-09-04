package com.zhixing.api.client.search;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 搜索服务客户端
 */
@FeignClient(value = "search-service", contextId = "searchClient")
public interface SearchClient {

    @GetMapping("/courses/name")
    List<Long> queryCourseIdsByName(@RequestParam("keyword") String keyword);
}
