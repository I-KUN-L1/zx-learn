package com.zhixing.learning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixing.api.client.course.CourseClient;
import com.zhixing.api.dto.course.CourseSimpleInfoDTO;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.learning.domain.po.Lesson;
import com.zhixing.learning.mapper.LessonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 我的课表服务（持久化）。
 * <p>与课程服务数据一致性：新增课表项时经 {@link CourseClient} 校验课程必须存在，
 * 课程服务不可用时降级返回 null 并拒绝落库，避免课表出现无效课程。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LessonService {

    private final LessonMapper lessonMapper;
    private final CourseClient courseClient;

    public List<Map<String, Object>> page(Long userId) {
        return lessonMapper.selectList(new LambdaQueryWrapper<Lesson>()
                        .eq(Lesson::getUserId, userId)
                        .orderByDesc(Lesson::getCreateTime)).stream()
                .map(LessonService::toMap)
                .collect(Collectors.toList());
    }

    public Map<String, Object> now(Long userId) {
        List<Lesson> list = lessonMapper.selectList(new LambdaQueryWrapper<Lesson>()
                .eq(Lesson::getUserId, userId)
                .orderByDesc(Lesson::getCreateTime));
        return list.isEmpty() ? null : toMap(list.get(0));
    }

    public Map<String, Object> getByCourse(Long userId, Long courseId) {
        Lesson lesson = lessonMapper.selectOne(new LambdaQueryWrapper<Lesson>()
                .eq(Lesson::getUserId, userId)
                .eq(Lesson::getCourseId, courseId));
        return lesson == null ? null : toMap(lesson);
    }

    public Integer countByCourse(Long courseId) {
        return Math.toIntExact(lessonMapper.selectCount(new LambdaQueryWrapper<Lesson>()
                .eq(Lesson::getCourseId, courseId)));
    }

    public Boolean valid(Long userId, Long courseId) {
        Long count = lessonMapper.selectCount(new LambdaQueryWrapper<Lesson>()
                .eq(Lesson::getUserId, userId)
                .eq(Lesson::getCourseId, courseId));
        return count > 0;
    }

    /**
     * 新增/更新学习计划：校验课程存在后落库，同一用户同一课程唯一（幂等 upsert）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void createPlan(Long userId, Map<String, Object> plan) {
        Long courseId = longOf(plan.get("courseId"));
        if (courseId == null) {
            throw new BadRequestException("课程 id 不能为空");
        }
        // 课程一致性校验：课程服务不可用时降级返回 null
        CourseSimpleInfoDTO course = courseClient.queryCourseInfoById(courseId);
        if (course == null) {
            throw new BadRequestException("课程不存在，无法加入课表");
        }
        Lesson existing = lessonMapper.selectOne(new LambdaQueryWrapper<Lesson>()
                .eq(Lesson::getUserId, userId)
                .eq(Lesson::getCourseId, courseId));
        if (existing != null) {
            existing.setCourseName(course.getName());
            existing.setPlan(plan.isEmpty() ? null : plan.toString());
            lessonMapper.updateById(existing);
            return;
        }
        Lesson lesson = new Lesson();
        lesson.setUserId(userId);
        lesson.setCourseId(courseId);
        lesson.setCourseName(course.getName());
        lesson.setPlan(plan.isEmpty() ? null : plan.toString());
        try {
            lessonMapper.insert(lesson);
        } catch (DuplicateKeyException e) {
            // uk_user_course 兜底，忽略并发重复添加
        }
    }

    public void delete(Long userId, Long courseId) {
        lessonMapper.delete(new LambdaQueryWrapper<Lesson>()
                .eq(Lesson::getUserId, userId)
                .eq(Lesson::getCourseId, courseId));
    }

    /**
     * 支付成功后开通课程（由 MQ 消费端调用）：
     * uk_user_course 唯一索引幂等，重复投递不会产生重复课表项。
     */
    @Transactional(rollbackFor = Exception.class)
    public void enroll(Long userId, Long courseId, String courseName) {
        Lesson lesson = new Lesson();
        lesson.setUserId(userId);
        lesson.setCourseId(courseId);
        lesson.setCourseName(courseName);
        try {
            lessonMapper.insert(lesson);
            log.info("支付成功开课：userId={}, courseId={}", userId, courseId);
        } catch (DuplicateKeyException e) {
            // uk_user_course 兜底：重复支付事件幂等跳过
            log.info("课表项已存在，开课幂等跳过：userId={}, courseId={}", userId, courseId);
        }
    }

    private static Map<String, Object> toMap(Lesson l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("courseId", l.getCourseId());
        m.put("courseName", l.getCourseName());
        m.put("plan", l.getPlan());
        return m;
    }

    private static Long longOf(Object v) {
        return v instanceof Number n ? n.longValue() : null;
    }
}