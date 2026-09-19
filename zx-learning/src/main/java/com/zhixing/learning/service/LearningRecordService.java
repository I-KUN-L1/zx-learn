package com.zhixing.learning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixing.api.client.course.CourseClient;
import com.zhixing.api.dto.course.CourseCatalogueDTO;
import com.zhixing.api.dto.course.CourseSimpleInfoDTO;
import com.zhixing.api.dto.learning.DailyActiveDTO;
import com.zhixing.api.dto.learning.LearningRecordDTO;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.exceptions.BizIllegalException;
import com.zhixing.common.utils.BeanUtils;
import com.zhixing.common.utils.UserContext;
import com.zhixing.learning.domain.dto.LearningProgressDTO;
import com.zhixing.learning.domain.po.LearningRecord;
import com.zhixing.learning.mapper.LearningRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 学习记录服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningRecordService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LearningRecordMapper learningRecordMapper;
    private final CourseClient courseClient;
    private final PointsService pointsService;

    /**
     * 提交学习进度。
     * 核心校验：学习进度不允许倒退（同一课程课时下只允许单调递增）。
     */
    public Long submitProgress(LearningProgressDTO form) {
        if (form.getCourseId() == null) {
            throw new BadRequestException("课程 id 不能为空");
        }
        if (form.getLessonId() == null) {
            throw new BadRequestException("课时 id 不能为空");
        }
        if (form.getProgress() == null || form.getProgress() < 0 || form.getProgress() > 100) {
            throw new BadRequestException("学习进度需在 0-100 之间");
        }
        Long userId = UserContext.getUserId();
        LambdaQueryWrapper<LearningRecord> query = new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId, userId)
                .eq(LearningRecord::getCourseId, form.getCourseId())
                .eq(LearningRecord::getLessonId, form.getLessonId());
        LearningRecord record = learningRecordMapper.selectOne(query);
        if (record == null) {
            record = new LearningRecord();
            record.setUserId(userId);
            record.setCourseId(form.getCourseId());
            record.setLessonId(form.getLessonId());
            record.setProgress(form.getProgress());
            record.setFinished(record.getProgress() >= 100);
            record.setLearnDuration(form.getLearnDuration() == null ? 0 : form.getLearnDuration());
            record.setLastLearnTime(LocalDateTime.now());
            learningRecordMapper.insert(record);
            // 新建即 100%（如直接标记完成）同样视为首次完成
            if (Boolean.TRUE.equals(record.getFinished())) {
                awardLessonAndCourse(userId, form.getCourseId(), form.getLessonId());
            }
            return record.getId();
        }
        // 进度不允许倒退
        if (form.getProgress() < record.getProgress()) {
            throw new BizIllegalException("学习进度不能倒退：当前 " + record.getProgress() + "，提交 " + form.getProgress());
        }
        boolean wasFinished = Boolean.TRUE.equals(record.getFinished());
        record.setProgress(form.getProgress());
        record.setFinished(form.getProgress() >= 100);
        int existingDuration = record.getLearnDuration() == null ? 0 : record.getLearnDuration();
        record.setLearnDuration(existingDuration + (form.getLearnDuration() == null ? 0 : form.getLearnDuration()));
        record.setLastLearnTime(LocalDateTime.now());
        learningRecordMapper.updateById(record);
        // 仅在"首次达到 100%"这一刻加分，反复提交进度不会重复计分（幂等键亦兜底）
        if (!wasFinished && Boolean.TRUE.equals(record.getFinished())) {
            awardLessonAndCourse(userId, form.getCourseId(), form.getLessonId());
        }
        return record.getId();
    }

    /**
     * 完成学习后的积分结算：小节完成奖 + 整门课程完成奖。
     * <p>
     * 全部为"尽力而为"语义：课程服务不可用时静默跳过课程级奖励，
     * 绝不因为加分失败而让看课进度上报报错。
     */
    private void awardLessonAndCourse(Long userId, Long courseId, Long lessonId) {
        try {
            String courseName = safeCourseName(courseId);
            pointsService.award(userId, PointsService.SOURCE_LESSON, PointsService.POINTS_LESSON_FINISH,
                    "完成《" + courseName + "》一小节学习", courseId + ":" + lessonId);
            if (isCourseFinished(userId, courseId)) {
                pointsService.award(userId, PointsService.SOURCE_COURSE, PointsService.POINTS_COURSE_FINISH,
                        "完成课程《" + courseName + "》全部内容", String.valueOf(courseId));
            }
        } catch (Exception e) {
            log.warn("学习积分结算失败（已降级，不影响进度上报）：userId={}, courseId={}, err={}",
                    userId, courseId, e.getMessage());
        }
    }

    /** 课程名称（课程服务不可用时降级为 "课程 #id"，保证积分说明始终可读） */
    private String safeCourseName(Long courseId) {
        try {
            CourseSimpleInfoDTO info = courseClient.queryCourseInfoById(courseId);
            if (info != null && info.getName() != null && !info.getName().isBlank()) {
                return info.getName();
            }
        } catch (Exception e) {
            log.warn("积分说明课程名回查失败（降级占位）: {}", e.getMessage());
        }
        return "课程 #" + courseId;
    }

    /**
     * 整门课程是否已学完：已完成小节数 ≥ 课程目录中的小节总数。
     * <p>
     * 目录拉取失败（课程服务不可用）或课程暂无小节时返回 false —— 宁可不发奖，也不误发奖。
     */
    private boolean isCourseFinished(Long userId, Long courseId) {
        List<CourseCatalogueDTO> catalogues = courseClient.queryCataloguesByCourse(courseId);
        if (catalogues == null || catalogues.isEmpty()) {
            return false;
        }
        List<Long> sectionIds = catalogues.stream()
                // chapterType: 1-章 2-小节；历史数据未标注类型的按小节处理
                .filter(c -> c.getChapterType() == null || c.getChapterType() == 2)
                .map(CourseCatalogueDTO::getId)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (sectionIds.isEmpty()) {
            return false;
        }
        Long finished = learningRecordMapper.selectCount(new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId, userId)
                .eq(LearningRecord::getCourseId, courseId)
                .eq(LearningRecord::getFinished, true)
                .in(LearningRecord::getLessonId, sectionIds));
        return finished != null && finished >= sectionIds.size();
    }

    /**
     * 查询指定用户全部学习记录（内部 Feign 接口）
     */
    public List<LearningRecordDTO> listRecords(Long userId) {
        List<LearningRecord> records = learningRecordMapper.selectList(
                new LambdaQueryWrapper<LearningRecord>()
                        .eq(LearningRecord::getUserId, userId)
                        .orderByDesc(LearningRecord::getLastLearnTime));
        return BeanUtils.copyList(records, LearningRecordDTO.class);
    }

    /**
     * 我的学习记录时间线（学员端，对齐前端 LearningRecordVO 契约）：
     * 聚合课程名称与课时小节名称，课程服务不可用时降级展示记录本身。
     */
    public List<Map<String, Object>> myRecords(Long userId) {
        List<LearningRecord> records = learningRecordMapper.selectList(
                new LambdaQueryWrapper<LearningRecord>()
                        .eq(LearningRecord::getUserId, userId)
                        .orderByDesc(LearningRecord::getLastLearnTime));
        if (records.isEmpty()) {
            return List.of();
        }

        Map<Long, String> courseNames = loadCourseNames(records);
        Map<Long, String> sectionNames = loadSectionNames(records);

        return records.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("userId", r.getUserId());
            m.put("courseId", r.getCourseId());
            m.put("lessonId", r.getLessonId());
            m.put("sectionId", r.getLessonId());
            m.put("sectionName", sectionNames.getOrDefault(r.getLessonId(), "第 " + r.getLessonId() + " 小节"));
            m.put("courseName", courseNames.getOrDefault(r.getCourseId(), "课程 #" + r.getCourseId()));
            m.put("moment", r.getLearnDuration() == null ? 0 : r.getLearnDuration());
            m.put("finished", Boolean.TRUE.equals(r.getFinished()));
            m.put("updateTime", r.getLastLearnTime() == null ? null
                    : TIME_FMT.format(r.getLastLearnTime()));
            return m;
        }).collect(Collectors.toList());
    }

    private Map<Long, String> loadCourseNames(List<LearningRecord> records) {
        try {
            List<Long> courseIds = records.stream()
                    .map(LearningRecord::getCourseId).distinct().collect(Collectors.toList());
            return courseClient.queryCourseSimpleInfoList(courseIds).stream()
                    .collect(Collectors.toMap(CourseSimpleInfoDTO::getId,
                            c -> c.getName() == null ? "" : c.getName(), (a, b) -> a));
        } catch (Exception e) {
            log.warn("学习记录课程名称聚合失败（课程服务不可用，降级）: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private Map<Long, String> loadSectionNames(List<LearningRecord> records) {
        try {
            List<Long> sectionIds = records.stream()
                    .map(LearningRecord::getLessonId).distinct().collect(Collectors.toList());
            return courseClient.queryCatalogueList(sectionIds).stream()
                    .collect(Collectors.toMap(CourseCatalogueDTO::getId,
                            c -> c.getName() == null ? "" : c.getName(), (a, b) -> a));
        } catch (Exception e) {
            log.warn("学习记录小节名称聚合失败（课程服务不可用，降级）: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * 查询指定用户学习总时长（秒）（内部 Feign 接口）
     */
    public Long sumDuration(Long userId) {
        return learningRecordMapper.selectList(
                        new LambdaQueryWrapper<LearningRecord>().eq(LearningRecord::getUserId, userId))
                .stream()
                .mapToLong(r -> r.getLearnDuration() == null ? 0L : r.getLearnDuration())
                .sum();
    }

    /**
     * 近 7 日日活统计（内部 Feign 接口）：按最后学习时间落在当日去重用户计数。
     */
    public List<DailyActiveDTO> dailyActive() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.minusDays(6).atStartOfDay();
        List<LearningRecord> records = learningRecordMapper.selectList(
                new LambdaQueryWrapper<LearningRecord>()
                        .select(LearningRecord::getUserId, LearningRecord::getLastLearnTime)
                        .ge(LearningRecord::getLastLearnTime, start));
        Map<String, Set<Long>> byDate = new HashMap<>();
        for (LearningRecord r : records) {
            LocalDateTime t = r.getLastLearnTime();
            if (t == null) {
                continue;
            }
            byDate.computeIfAbsent(t.toLocalDate().toString(), k -> new HashSet<>()).add(r.getUserId());
        }
        List<DailyActiveDTO> result = new ArrayList<>(7);
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            String key = d.toString();
            result.add(new DailyActiveDTO(key, (long) byDate.getOrDefault(key, Set.of()).size()));
        }
        return result;
    }
}