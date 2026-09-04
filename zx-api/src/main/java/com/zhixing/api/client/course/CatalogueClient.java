package com.zhixing.api.client.course;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 课程目录客户端
 */
@FeignClient(value = "course-service", contextId = "catalogueClient")
public interface CatalogueClient {

    @GetMapping("/catalogues/batchQuery")
    Map<Long, Object> batchQueryCatalogue(@RequestParam("ids") List<Long> ids);
}
