package com.zhixing.api.client.course;

import com.zhixing.api.dto.course.CourseSimpleInfoDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 课程服务客户端
 */
@FeignClient(value = "course-service", contextId = "courseClient",
        fallbackFactory = CourseClientFallbackFactory.class)
public interface CourseClient {

    @GetMapping("/course/simpleInfo")
    List<CourseSimpleInfoDTO> queryCourseSimpleInfoList(@RequestParam("ids") List<Long> ids);

    @GetMapping("/course/{id}/searchInfo")
    CourseSimpleInfoDTO queryCourseInfoById(@PathVariable("id") Long id);

    @GetMapping("/course/name")
    List<Long> queryCourseIdByName(@RequestParam("name") String name);

    /**
     * 查询全部已上架课程
     */
    @GetMapping("/course/all")
    List<CourseSimpleInfoDTO> queryAllSimpleInfo();
}
