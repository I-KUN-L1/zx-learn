package com.zhixing.api.client.course;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * 小节题目关系客户端
 */
@FeignClient(value = "course-service", contextId = "subjectClient")
public interface SubjectClient {

    @GetMapping("/courses/subjects/get/{id}")
    List<Long> querySubjectQuestionIds(@PathVariable("id") Long courseId);
}
