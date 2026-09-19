package com.zhixing.course.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixing.common.constants.Constant;
import com.zhixing.common.constants.UserRole;
import com.zhixing.common.domain.PageQuery;
import com.zhixing.common.exceptions.ForbiddenException;
import com.zhixing.common.utils.UserContext;
import com.zhixing.course.domain.po.Course;
import com.zhixing.course.domain.po.CourseDraft;
import com.zhixing.course.mapper.CourseCatalogueMapper;
import com.zhixing.course.mapper.CourseDraftMapper;
import com.zhixing.course.mapper.CourseMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 课程归属权限矩阵单测。
 *
 * <p>锁住「员工(1) 不受归属限制 / 教师(3) 仅限本人名下 / 学员与其他角色一律拒绝」这条统一规则，
 * 覆盖：列表归属过滤、下架、草稿（查看 / 发布 / 删除）、门户检索的上下架一致性。
 *
 * <p>每条「拒绝」断言都配了「放行」对照——若归属校验被删掉（修复前的真实状态：
 * 注解只校验角色、不校验课程归属，任意教师可下架/操作他人课程），
 * {@code teacherCannotDownShelfOthersCourse} 会立即失败，不会被「居然成功了」掩盖。
 */
@ExtendWith(MockitoExtension.class)
class CourseOwnershipMatrixTest {

    private static final long COURSE_ID = 2001L;
    private static final long DRAFT_ID = 9001L;
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

    @BeforeEach
    void initMybatisMeta() {
        // 纯 Mock 单测需手动初始化元数据，LambdaWrapper 才能解析出列名（否则 getSqlSegment 抛异常）
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Course.class);
        TableInfoHelper.initTableInfo(assistant, CourseDraft.class);
    }

    @AfterEach
    void clearContext() {
        UserContext.remove();
    }

    private void login(long userId, UserRole role) {
        UserContext.setUser(userId);
        UserContext.setRole(role.getCode());
    }

    private Course courseOf(Long teacherId) {
        Course course = new Course();
        course.setId(COURSE_ID);
        course.setName("虚拟线程实战");
        course.setTeacherId(teacherId);
        course.setStatus(Constant.COURSE_STATUS_ON_SHELF);
        return course;
    }

    private CourseDraft draftOf(Long teacherId) {
        CourseDraft draft = new CourseDraft();
        draft.setId(DRAFT_ID);
        draft.setName("虚拟线程实战（草稿）");
        draft.setTeacherId(teacherId);
        draft.setSubmitted(0);
        draft.setStep(1);
        return draft;
    }

    /** 捕获传给 courseMapper.selectPage 的查询条件 SQL，用于断言过滤列 */
    private String captureCourseSql() {
        ArgumentCaptor<Wrapper<Course>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(courseMapper).selectPage(any(), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        return sql == null ? "" : sql.toLowerCase();
    }

    // ==================== 下架：归属校验 ====================

    @Test
    @DisplayName("员工(1) 可下架他人名下课程")
    void staffCanDownShelfAnyCourse() {
        login(STAFF_ID, UserRole.STAFF);
        when(courseMapper.selectById(COURSE_ID)).thenReturn(courseOf(OTHER_TEACHER_ID));
        when(courseMapper.updateById(any(Course.class))).thenReturn(1);

        courseService.downShelf(COURSE_ID);

        ArgumentCaptor<Course> captor = ArgumentCaptor.forClass(Course.class);
        verify(courseMapper).updateById(captor.capture());
        assertEquals(Constant.COURSE_STATUS_OFF_SHELF, captor.getValue().getStatus());
    }

    @Test
    @DisplayName("教师(3) 可下架本人名下课程")
    void teacherCanDownShelfOwnCourse() {
        login(TEACHER_ID, UserRole.TEACHER);
        when(courseMapper.selectById(COURSE_ID)).thenReturn(courseOf(TEACHER_ID));
        when(courseMapper.updateById(any(Course.class))).thenReturn(1);

        courseService.downShelf(COURSE_ID);

        verify(courseMapper).updateById(any(Course.class));
    }

    @Test
    @DisplayName("教师(3) 下架他人课程被拒绝，且不落库（修复前这里会下架成功）")
    void teacherCannotDownShelfOthersCourse() {
        login(TEACHER_ID, UserRole.TEACHER);
        when(courseMapper.selectById(COURSE_ID)).thenReturn(courseOf(OTHER_TEACHER_ID));

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> courseService.downShelf(COURSE_ID));

        assertEquals("只能下架本人名下的课程", ex.getMessage());
        verify(courseMapper, never()).updateById(any(Course.class));
    }

    @Test
    @DisplayName("学员(2) 走到业务层也一律 403（防内部调用绕过注解）")
    void studentCannotDownShelf() {
        login(2L, UserRole.STUDENT);
        when(courseMapper.selectById(COURSE_ID)).thenReturn(courseOf(TEACHER_ID));

        assertThrows(ForbiddenException.class, () -> courseService.downShelf(COURSE_ID));
        verify(courseMapper, never()).updateById(any(Course.class));
    }

    // ==================== 列表：归属过滤 ====================

    @Test
    @DisplayName("教师(3) 课程列表按本人 teacher_id 过滤（看不到其他教师的课）")
    void teacherListIsScopedToOwnCourses() {
        when(courseMapper.selectPage(any(), any())).thenReturn(new Page<Course>());

        courseService.pageQuery(new PageQuery(), null, null, TEACHER_ID);

        String sql = captureCourseSql();
        assertTrue(sql.contains("teacher_id"), "教师列表必须带 teacher_id 归属条件，实际 SQL：" + sql);
    }

    @Test
    @DisplayName("员工(1) 课程列表不限归属（ownerScope 为空，不带 teacher_id 条件）")
    void staffListIsNotScoped() {
        when(courseMapper.selectPage(any(), any())).thenReturn(new Page<Course>());

        courseService.pageQuery(new PageQuery(), null, null, null);

        String sql = captureCourseSql();
        assertFalse(sql.contains("teacher_id"), "员工列表不应带归属条件，实际 SQL：" + sql);
    }

    @Test
    @DisplayName("学员/访客范围：只查已上架，且不带归属条件")
    void studentListSeesOnlyOnShelfCourses() {
        when(courseMapper.selectPage(any(), any())).thenReturn(new Page<Course>());

        courseService.pageQuery(new PageQuery(), null, Constant.COURSE_STATUS_ON_SHELF, null);

        String sql = captureCourseSql();
        assertTrue(sql.contains("status"), "学员列表必须按 status 过滤，实际 SQL：" + sql);
        assertFalse(sql.contains("teacher_id"), "学员列表是「看全站已上架」，不应带归属条件，实际 SQL：" + sql);
    }

    // ==================== 草稿：归属校验 ====================

    @Test
    @DisplayName("教师(3) 只能查看本人草稿，看他人草稿 403")
    void teacherCannotReadOthersDraft() {
        login(TEACHER_ID, UserRole.TEACHER);
        when(courseDraftMapper.selectById(DRAFT_ID)).thenReturn(draftOf(OTHER_TEACHER_ID));

        assertThrows(ForbiddenException.class, () -> courseService.getBaseInfo(DRAFT_ID));
    }

    @Test
    @DisplayName("教师(3) 可查看本人草稿")
    void teacherCanReadOwnDraft() {
        login(TEACHER_ID, UserRole.TEACHER);
        when(courseDraftMapper.selectById(DRAFT_ID)).thenReturn(draftOf(TEACHER_ID));

        assertNotNull(courseService.getBaseInfo(DRAFT_ID));
    }

    @Test
    @DisplayName("教师(3) 不能发布他人草稿，且不会写库")
    void teacherCannotPublishOthersDraft() {
        login(TEACHER_ID, UserRole.TEACHER);
        when(courseDraftMapper.selectById(DRAFT_ID)).thenReturn(draftOf(OTHER_TEACHER_ID));

        assertThrows(ForbiddenException.class, () -> courseService.upShelf(DRAFT_ID));

        verify(courseDraftMapper, never()).updateById(any(CourseDraft.class));
        verify(courseMapper, never()).insert(any(Course.class));
    }

    @Test
    @DisplayName("教师(3) 不能删除他人草稿，且不会落库")
    void teacherCannotDeleteOthersDraft() {
        login(TEACHER_ID, UserRole.TEACHER);
        when(courseDraftMapper.selectById(DRAFT_ID)).thenReturn(draftOf(OTHER_TEACHER_ID));

        assertThrows(ForbiddenException.class, () -> courseService.deleteDraft(DRAFT_ID));

        verify(courseDraftMapper, never()).deleteById(DRAFT_ID);
    }

    @Test
    @DisplayName("教师(3) 草稿箱只列本人草稿（SQL 带 teacher_id 条件）")
    void teacherDraftPageIsScoped() {
        login(TEACHER_ID, UserRole.TEACHER);
        when(courseDraftMapper.selectPage(any(), any())).thenReturn(new Page<CourseDraft>());

        courseService.draftPage(new PageQuery(), null);

        ArgumentCaptor<Wrapper<CourseDraft>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(courseDraftMapper).selectPage(any(), captor.capture());
        String sql = captor.getValue().getSqlSegment().toLowerCase();
        assertTrue(sql.contains("teacher_id"), "教师草稿箱必须带归属条件，实际 SQL：" + sql);
    }

    // ==================== 门户检索：与上下架状态一致 ====================

    @Test
    @DisplayName("门户检索只召回已上架课程（下架课程不会出现在搜索结果里）")
    void portalQueryOnlyReturnsOnShelfCourses() {
        when(courseMapper.selectPage(any(), any())).thenReturn(new Page<Course>());

        courseService.portalQuery("Java", "new", 8);

        String sql = captureCourseSql();
        assertTrue(sql.contains("status"), "门户检索必须按 status 过滤，实际 SQL：" + sql);
        assertTrue(sql.contains("name"), "带关键字时应按课程名过滤，实际 SQL：" + sql);
    }

    @Test
    @DisplayName("精品好课按销量倒序（sold desc）")
    void portalBestSortsBySold() {
        when(courseMapper.selectPage(any(), any())).thenReturn(new Page<Course>());

        courseService.portalQuery(null, "best", 8);

        String sql = captureCourseSql();
        assertTrue(sql.contains("sold"), "精品好课必须按销量排序，实际 SQL：" + sql);
    }

    @Test
    @DisplayName("limit 越界自动收敛到 [1, 50]，避免调用方传入极大值")
    void portalLimitIsClamped() {
        when(courseMapper.selectPage(any(), any())).thenReturn(new Page<Course>());

        ArgumentCaptor<Page<Course>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        courseService.portalQuery(null, "new", 999);
        verify(courseMapper).selectPage(pageCaptor.capture(), any());
        assertEquals(50L, pageCaptor.getValue().getSize());

        courseService.portalQuery(null, "new", -5);
        verify(courseMapper, org.mockito.Mockito.times(2)).selectPage(pageCaptor.capture(), any());
        assertEquals(1L, pageCaptor.getValue().getSize());
    }
}
