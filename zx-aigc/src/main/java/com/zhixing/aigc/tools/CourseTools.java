package com.zhixing.aigc.tools;

import com.zhixing.api.client.course.CourseClient;
import com.zhixing.api.dto.course.CourseSimpleInfoDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 课程工具：供 Agent 调用查询课程信息
 */
@Component
@RequiredArgsConstructor
public class CourseTools {

    private final CourseClient courseClient;

    public CourseSimpleInfoDTO getCourseInfo(Long courseId) {
        return courseClient.queryCourseInfoById(courseId);
    }

    public List<Long> searchCourseIds(String name) {
        return courseClient.queryCourseIdByName(name);
    }

    public List<CourseSimpleInfoDTO> listCourses(List<Long> ids) {
        return courseClient.queryCourseSimpleInfoList(ids);
    }

    /**
     * 按关键字真实查询课程库（供 LLM 的 searchCourses 工具调用，避免幻觉推荐）。
     *
     * @param keyword   课程名关键字（必填）
     * @param categoryId 三级分类 id（可选）：命中课程的 lv1/lv2/lv3 任一级即保留
     */
    public List<CourseSimpleInfoDTO> searchCourses(String keyword, Long categoryId) {
        if (keyword == null || keyword.isBlank()) {
            return java.util.List.of();
        }
        List<Long> ids = courseClient.queryCourseIdByName(keyword);
        if (ids == null || ids.isEmpty()) {
            return java.util.List.of();
        }
        List<CourseSimpleInfoDTO> courses = courseClient.queryCourseSimpleInfoList(ids);
        if (categoryId == null) {
            return courses;
        }
        // 分类过滤：任一级分类命中即视为属于该分类
        return courses == null ? java.util.List.of() : courses.stream()
                .filter(c -> categoryId.equals(c.getCategoryIdLv1())
                        || categoryId.equals(c.getCategoryIdLv2())
                        || categoryId.equals(c.getCategoryIdLv3()))
                .toList();
    }
}
