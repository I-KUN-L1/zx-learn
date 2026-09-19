package com.zhixing.api.client.course;

import com.zhixing.api.dto.course.CourseCatalogueDTO;
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

    /**
     * 门户课程检索（zx-search 的搜索与推荐位使用）。
     * <p>
     * 只返回<b>已上架</b>课程，保证搜索结果与上下架状态一致。
     *
     * @param keyword 课程名模糊关键字，可为空
     * @param sort    排序口径：{@code best} 销量倒序 / {@code free} 仅免费课 / 其它 按最新
     * @param limit   返回条数上限
     */
    @GetMapping("/course/portal")
    List<CourseSimpleInfoDTO> queryPortalCourses(@RequestParam(value = "keyword", required = false) String keyword,
                                                 @RequestParam(value = "sort", required = false) String sort,
                                                 @RequestParam(value = "limit", required = false) Integer limit);

    /**
     * 批量查询课程目录（章节/小节）信息
     */
    @GetMapping("/course/catalogues")
    List<CourseCatalogueDTO> queryCatalogueList(@RequestParam("ids") List<Long> ids);

    /**
     * 查询指定课程的全部目录（章节 + 小节）。
     * 供学习服务判定整门课程是否学完并发放课程完成积分。
     */
    @GetMapping("/course/{id}/catalogues")
    List<CourseCatalogueDTO> queryCataloguesByCourse(@PathVariable("id") Long courseId);
}
