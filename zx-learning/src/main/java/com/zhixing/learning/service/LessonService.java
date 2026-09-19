package com.zhixing.learning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixing.api.client.course.CourseClient;
import com.zhixing.api.dto.course.CourseSimpleInfoDTO;
import com.zhixing.common.domain.PageDTO;
import com.zhixing.common.domain.PageQuery;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.learning.domain.po.LearningRecord;
import com.zhixing.learning.domain.po.Lesson;
import com.zhixing.learning.mapper.LearningRecordMapper;
import com.zhixing.learning.mapper.LessonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.HashMap;
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

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LessonMapper lessonMapper;
    private final CourseClient courseClient;
    private final LearningRecordMapper learningRecordMapper;

    /**
     * 我的课表分页（对齐前端 LearningLessonVO 契约）：
     * 聚合课程封面（course 服务）与学习进度/状态（learning_record），课程服务不可用时降级仅返回课表快照。
     */
    public PageDTO<Map<String, Object>> page(Long userId, PageQuery query) {
        Page<Lesson> page = lessonMapper.selectPage(query.toMpPage("create_time", false),
                new LambdaQueryWrapper<Lesson>().eq(Lesson::getUserId, userId));

        List<Lesson> records = page.getRecords();
        if (records.isEmpty()) {
            return PageDTO.empty(page.getTotal(), page.getPages());
        }

        // 课程信息聚合（封面/名称）：降级返回空集合，课表仍可展示
        Map<Long, CourseSimpleInfoDTO> courseInfoMap = loadCourseInfo(records);

        // 学习进度聚合：按课程分组求平均进度、判断是否全部学完
        Map<Long, List<LearningRecord>> recordsByCourse = learningRecordMapper.selectList(
                        new LambdaQueryWrapper<LearningRecord>()
                                .eq(LearningRecord::getUserId, userId)
                                .in(LearningRecord::getCourseId, records.stream()
                                        .map(Lesson::getCourseId).collect(Collectors.toSet())))
                .stream()
                .collect(Collectors.groupingBy(LearningRecord::getCourseId));

        List<Map<String, Object>> list = records.stream()
                .map(l -> toVoMap(l, courseInfoMap, recordsByCourse))
                .collect(Collectors.toList());
        return PageDTO.of(page, list);
    }

    private Map<Long, CourseSimpleInfoDTO> loadCourseInfo(List<Lesson> lessons) {
        try {
            List<Long> courseIds = lessons.stream().map(Lesson::getCourseId).collect(Collectors.toList());
            return courseClient.queryCourseSimpleInfoList(courseIds).stream()
                    .collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c, (a, b) -> a));
        } catch (Exception e) {
            log.warn("课表封面聚合失败（课程服务不可用，降级）: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private static Map<String, Object> toVoMap(Lesson l,
                                               Map<Long, CourseSimpleInfoDTO> courseInfoMap,
                                               Map<Long, List<LearningRecord>> recordsByCourse) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.getId());
        m.put("userId", l.getUserId());
        m.put("courseId", l.getCourseId());
        CourseSimpleInfoDTO course = courseInfoMap.get(l.getCourseId());
        m.put("courseName", course != null && course.getName() != null ? course.getName() : l.getCourseName());
        m.put("coverUrl", course != null ? course.getCoverUrl() : "");
        // 学习进度：取该课程全部记录的平均值
        List<LearningRecord> courseRecords = recordsByCourse.getOrDefault(l.getCourseId(), List.of());
        int learnProgress = courseRecords.isEmpty() ? 0
                : (int) Math.round(courseRecords.stream()
                        .mapToInt(r -> r.getProgress() == null ? 0 : r.getProgress())
                        .average().orElse(0));
        m.put("learnProgress", learnProgress);
        // 状态：0在学 1已完成 2已过期
        boolean allFinished = !courseRecords.isEmpty() && courseRecords.stream()
                .allMatch(r -> Boolean.TRUE.equals(r.getFinished()));
        m.put("status", allFinished ? 1 : 0);
        m.put("weekFreq", parseWeekFreq(l.getPlan()));
        m.put("createTime", l.getCreateTime() == null ? null : TIME_FMT.format(l.getCreateTime()));
        return m;
    }

    /** 从学习计划 JSON 中解析每周学习频次（无计划/解析失败返回 null） */
    private static Integer parseWeekFreq(String plan) {
        if (plan == null || plan.isBlank()) {
            return null;
        }
        try {
            String body = plan.trim();
            int idx = body.indexOf("\"weekFreq\"");
            if (idx < 0) {
                return null;
            }
            int start = body.indexOf(':', idx) + 1;
            int end = start;
            while (end < body.length() && Character.isDigit(body.charAt(end))) {
                end++;
            }
            return start == end ? null : Integer.valueOf(body.substring(start, end));
        } catch (Exception e) {
            return null;
        }
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
     * 支付成功后开通课程（由 MQ 消费端 / 交易服务同步调用）。
     * <p>
     * 唯一索引 {@code uk_user_course} 保证幂等：重复投递不会产生重复课表项。
     * <b>但幂等不能只是"跳过"</b>：唯一索引是物理约束，而删除课表项是逻辑删除
     * （{@code deleted=1}）。学员删过课程后再购买/开课时 insert 会撞唯一索引，
     * 若仅跳过则该行永远是 {@code deleted=1}，课表里始终看不到 —— 必须"复活"它。
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
            // 课表项已存在：可能是重复投递（真幂等），也可能是曾被删除（deleted=1）仍占用唯一索引。
            // 统一走"复活 + 刷新课程名快照"，保证开课后一定能在我的课表中看到。
            int revived = lessonMapper.revive(userId, courseId, courseName);
            log.info("课表项已存在，开课幂等处理：userId={}, courseId={}, 复活行数={}",
                    userId, courseId, revived);
        }
    }

    /**
     * 用户课程中心（课表）中的课程 id 集合（内部 Feign 接口）。
     * <p>
     * 「已拥有」的权威口径：只要课表存在（含学习进度为 0、退款后未清课表的课程），
     * 课程中心/购买链路即视为已拥有，禁止重复购买。
     */
    public List<Long> listCourseIds(Long userId) {
        return lessonMapper.selectList(new LambdaQueryWrapper<Lesson>()
                        .select(Lesson::getCourseId)
                        .eq(Lesson::getUserId, userId))
                .stream()
                .map(Lesson::getCourseId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
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