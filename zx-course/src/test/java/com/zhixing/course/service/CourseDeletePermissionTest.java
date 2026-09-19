package com.zhixing.course.service;

import com.zhixing.common.constants.UserRole;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.exceptions.ForbiddenException;
import com.zhixing.common.utils.UserContext;
import com.zhixing.course.domain.po.Course;
import com.zhixing.course.mapper.CourseCatalogueMapper;
import com.zhixing.course.mapper.CourseDraftMapper;
import com.zhixing.course.mapper.CourseMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 课程删除权限单测：锁住「员工(1) 可删任意课程、教师(3) 仅可删本人名下课程」这条规则。
 *
 * <p>每条"拒绝"断言都配了"放行"对照：若归属校验被删掉（= 修复前的真实状态：
 * 只改注解不做归属判定，任意教师可删任意课程），
 * {@link #teacherCannotDeleteOthersCourse()} 会立即失败，不会被"能删成功"掩盖。
 */
@ExtendWith(MockitoExtension.class)
class CourseDeletePermissionTest {

    private static final long COURSE_ID = 1001L;
    private static final long TEACHER_ID = 3001L;
    private static final long OTHER_TEACHER_ID = 3002L;
    private static final long STAFF_ID = 1L;

    @Mock
    private CourseMapper courseMapper;
    @Mock
    private CourseDraftMapper courseDraftMapper;
    @Mock
    private CourseCatalogueMapper catalogueMapper;

    @InjectMocks
    private CourseService courseService;

    @AfterEach
    void clearContext() {
        // ThreadLocal 必须清，否则污染同一 JVM 内的后续用例
        UserContext.remove();
    }

    private Course courseOf(Long teacherId) {
        Course course = new Course();
        course.setId(COURSE_ID);
        course.setName("虚拟线程实战");
        course.setTeacherId(teacherId);
        course.setStatus(1);
        return course;
    }

    private void login(Long userId, UserRole role) {
        UserContext.setUser(userId);
        UserContext.setRole(role.getCode());
    }

    @Test
    @DisplayName("员工(1) 可删除他人名下的课程")
    void staffCanDeleteAnyCourse() {
        login(STAFF_ID, UserRole.STAFF);
        when(courseMapper.selectById(COURSE_ID)).thenReturn(courseOf(OTHER_TEACHER_ID));

        courseService.delete(COURSE_ID);

        verify(courseMapper).deleteById(COURSE_ID);
    }

    @Test
    @DisplayName("教师(3) 可删除本人名下课程")
    void teacherCanDeleteOwnCourse() {
        login(TEACHER_ID, UserRole.TEACHER);
        when(courseMapper.selectById(COURSE_ID)).thenReturn(courseOf(TEACHER_ID));

        courseService.delete(COURSE_ID);

        verify(courseMapper).deleteById(COURSE_ID);
    }

    @Test
    @DisplayName("教师(3) 删除他人课程被拒绝，且不会落库删除")
    void teacherCannotDeleteOthersCourse() {
        login(TEACHER_ID, UserRole.TEACHER);
        when(courseMapper.selectById(COURSE_ID)).thenReturn(courseOf(OTHER_TEACHER_ID));

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> courseService.delete(COURSE_ID));

        assertEquals("只能删除本人名下的课程", ex.getMessage());
        verify(courseMapper, never()).deleteById(COURSE_ID);
    }

    @Test
    @DisplayName("授课老师为空的课程：教师删不了，只有员工可删（历史数据兜底）")
    void teacherCannotDeleteCourseWithoutTeacher() {
        login(TEACHER_ID, UserRole.TEACHER);
        when(courseMapper.selectById(COURSE_ID)).thenReturn(courseOf(null));

        assertThrows(ForbiddenException.class, () -> courseService.delete(COURSE_ID));
        verify(courseMapper, never()).deleteById(COURSE_ID);
    }

    @Test
    @DisplayName("学员(2) 走到业务层也一律 403（防内部调用绕过注解）")
    void studentIsRejectedAtServiceLayer() {
        login(2L, UserRole.STUDENT);
        when(courseMapper.selectById(COURSE_ID)).thenReturn(courseOf(TEACHER_ID));

        assertThrows(ForbiddenException.class, () -> courseService.delete(COURSE_ID));
        verify(courseMapper, never()).deleteById(COURSE_ID);
    }

    @Test
    @DisplayName("没有角色头（内部/未登录调用）一律 403")
    void anonymousIsRejectedAtServiceLayer() {
        // 只设 userId、不设 role：模拟"内部调用直接进 Service、绕过了注解层"的场景。
        // 课程必须存在，否则会先命中「课程不存在」而测不到角色兜底分支。
        UserContext.setUser(0L);
        when(courseMapper.selectById(COURSE_ID)).thenReturn(courseOf(TEACHER_ID));

        assertThrows(ForbiddenException.class, () -> courseService.delete(COURSE_ID));
        verify(courseMapper, never()).deleteById(COURSE_ID);
    }

    @Test
    @DisplayName("课程不存在时给出 400，且不落库删除")
    void missingCourseIsRejected() {
        login(STAFF_ID, UserRole.STAFF);
        when(courseMapper.selectById(COURSE_ID)).thenReturn(null);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> courseService.delete(COURSE_ID));

        assertEquals("课程不存在", ex.getMessage());
        verify(courseMapper, never()).deleteById(COURSE_ID);
    }
}
