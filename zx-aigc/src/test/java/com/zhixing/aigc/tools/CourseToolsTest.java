package com.zhixing.aigc.tools;

import com.zhixing.api.client.course.CourseClient;
import com.zhixing.api.dto.course.CourseSimpleInfoDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 课程工具单测：searchCourses 真实查询课程库 + 分类（lv1/lv2/lv3 任一级）过滤。
 */
@ExtendWith(MockitoExtension.class)
class CourseToolsTest {

    @Mock
    private CourseClient courseClient;

    @InjectMocks
    private CourseTools courseTools;

    private CourseSimpleInfoDTO course(long id, Long lv1, Long lv2, Long lv3) {
        CourseSimpleInfoDTO dto = new CourseSimpleInfoDTO();
        dto.setId(id);
        dto.setName("课程" + id);
        dto.setCategoryIdLv1(lv1);
        dto.setCategoryIdLv2(lv2);
        dto.setCategoryIdLv3(lv3);
        return dto;
    }

    @Test
    void searchCoursesQueriesRealCourseLibrary() {
        when(courseClient.queryCourseIdByName("Java")).thenReturn(List.of(1L, 2L));
        when(courseClient.queryCourseSimpleInfoList(List.of(1L, 2L)))
                .thenReturn(List.of(course(1L, 100L, 100100L, 100100301L),
                        course(2L, 100L, 100100L, 100100301L)));

        List<CourseSimpleInfoDTO> courses = courseTools.searchCourses("Java", null);

        assertEquals(2, courses.size());
    }

    @Test
    void searchCoursesFiltersByCategoryIdOnAnyLevel() {
        when(courseClient.queryCourseIdByName("Java")).thenReturn(List.of(1L, 2L, 3L));
        when(courseClient.queryCourseSimpleInfoList(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(
                        course(1L, 100L, 100100L, 100100301L),   // lv3 命中
                        course(2L, 200L, 200100L, 200100001L),   // 不命中
                        course(3L, 999L, 100100L, 999999999L))); // lv2 命中

        List<CourseSimpleInfoDTO> courses = courseTools.searchCourses("Java", 100100301L);
        assertEquals(1, courses.size());
        assertEquals(1L, courses.get(0).getId());

        // lv2 维度过滤
        List<CourseSimpleInfoDTO> byLv2 = courseTools.searchCourses("Java", 100100L);
        assertEquals(2, byLv2.size());
    }

    @Test
    void searchCoursesReturnsEmptyForBlankKeyword() {
        assertTrue(courseTools.searchCourses("  ", null).isEmpty());
        assertTrue(courseTools.searchCourses(null, null).isEmpty());
    }
}
