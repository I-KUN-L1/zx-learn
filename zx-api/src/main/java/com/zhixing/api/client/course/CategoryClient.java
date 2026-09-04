package com.zhixing.api.client.course;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * 课程分类客户端
 */
@FeignClient(value = "course-service", contextId = "categoryClient")
public interface CategoryClient {

    @GetMapping("/categorys/getAllOfOneLevel")
    List<Object> getAllOfOneLevel();
}
