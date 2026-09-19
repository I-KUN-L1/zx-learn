package com.zhixing.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixing.api.client.course.CourseClient;
import com.zhixing.api.dto.course.CourseSimpleInfoDTO;
import com.zhixing.common.domain.PageDTO;
import com.zhixing.common.domain.PageQuery;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.utils.UserContext;
import com.zhixing.exam.domain.po.Question;
import com.zhixing.exam.mapper.QuestionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 题库服务（教师端发布 → 学员端接收）。
 * <p>
 * 原实现把题目存在 Controller 的内存 ConcurrentHashMap 里：进程重启即丢、
 * 多实例不共享、学员端无法稳定接收。此处统一落库 MySQL。
 * <p>
 * 课程名称通过 Feign 批量补全，课程服务不可用时降级为"课程 #id"，不阻断题库查询。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionService {

    /** 已发布（学员可见） */
    public static final int STATUS_PUBLISHED = 1;
    /** 草稿（仅教师可见） */
    public static final int STATUS_DRAFT = 0;

    private final QuestionMapper questionMapper;
    private final CourseClient courseClient;

    /**
     * 题库分页查询（教师端题库管理 / 学员端题库练习共用）。
     *
     * @param onlyPublished 学员端传 true（只返回已发布），教师端传 false（草稿也可见）
     */
    public PageDTO<Question> page(PageQuery query, String keyword, Integer type,
                                  Long courseId, Long teacherId, boolean onlyPublished) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<Question>()
                .like(StringUtils.hasText(keyword), Question::getName, keyword)
                .eq(type != null, Question::getType, type)
                .eq(courseId != null, Question::getCourseId, courseId)
                .eq(teacherId != null, Question::getTeacherId, teacherId)
                .eq(onlyPublished, Question::getStatus, STATUS_PUBLISHED)
                .orderByDesc(Question::getId);
        Page<Question> page = questionMapper.selectPage(query.toMpPage(), wrapper);
        fillCourseName(page.getRecords());
        return PageDTO.of(page);
    }

    /**
     * 按 id 列表查询题目；ids 为空时返回题库（学员端只返回已发布）。
     * <p>
     * 修复要点：原 {@code @RequestParam("ids") List<Long> ids} 为强制参数，
     * 学员端不带 ids 请求时直接 400，导致"题库练习"永远加载失败。
     */
    public List<Question> listByIdsOrAll(List<Long> ids, boolean onlyPublished) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<Question>()
                .in(ids != null && !ids.isEmpty(), Question::getId, ids)
                .eq(onlyPublished, Question::getStatus, STATUS_PUBLISHED)
                .orderByAsc(Question::getId);
        List<Question> list = questionMapper.selectList(wrapper);
        fillCourseName(list);
        return list;
    }

    public Question getRequired(Long id) {
        Question question = questionMapper.selectById(id);
        if (question == null) {
            throw new BadRequestException("题目不存在");
        }
        fillCourseName(Collections.singletonList(question));
        return question;
    }

    /** 教师新增题目（默认发布，可显式置草稿） */
    public Long create(Question question) {
        validate(question);
        question.setId(null);
        question.setTeacherId(UserContext.getUserId());
        if (question.getStatus() == null) {
            question.setStatus(STATUS_PUBLISHED);
        }
        normalize(question);
        questionMapper.insert(question);
        log.info("教师 {} 新增题目 id={}, courseId={}, status={}",
                question.getTeacherId(), question.getId(), question.getCourseId(), question.getStatus());
        return question.getId();
    }

    /** 教师更新题目（保留原归属教师，避免被横向篡改） */
    public void update(Long id, Question question) {
        Question exist = questionMapper.selectById(id);
        if (exist == null) {
            throw new BadRequestException("题目不存在");
        }
        validate(question);
        question.setId(id);
        question.setTeacherId(exist.getTeacherId());
        if (question.getStatus() == null) {
            question.setStatus(exist.getStatus());
        }
        normalize(question);
        questionMapper.updateById(question);
        log.info("更新题目 id={}", id);
    }

    public void delete(Long id) {
        if (questionMapper.deleteById(id) == 0) {
            throw new BadRequestException("题目不存在");
        }
        log.info("删除题目 id={}", id);
    }

    /** 发布 / 撤回题目：控制学员端可见性，是师生联动的开关 */
    public void publish(Long id, boolean published) {
        Question question = questionMapper.selectById(id);
        if (question == null) {
            throw new BadRequestException("题目不存在");
        }
        Question patch = new Question();
        patch.setId(id);
        patch.setStatus(published ? STATUS_PUBLISHED : STATUS_DRAFT);
        questionMapper.updateById(patch);
        log.info("题目 {} 发布状态更新为 {}", id, published ? "已发布" : "草稿");
    }

    /** 题目分值映射（供其他服务/前端批量取分） */
    public Map<Long, Integer> scores(List<Long> ids) {
        return listByIdsOrAll(ids, false).stream()
                .filter(q -> q.getId() != null && q.getScore() != null)
                .collect(Collectors.toMap(Question::getId, Question::getScore, (a, b) -> a));
    }

    /** 某教师的题目数量 */
    public long countByTeacher(Long teacherId) {
        return questionMapper.selectCount(new LambdaQueryWrapper<Question>()
                .eq(teacherId != null, Question::getTeacherId, teacherId));
    }

    // ============ 私有方法 ============

    private void validate(Question question) {
        if (question == null || !StringUtils.hasText(question.getName())) {
            throw new BadRequestException("题干不能为空");
        }
        if (question.getScore() == null) {
            throw new BadRequestException("分值不能为空");
        }
    }

    /** 归一化：判断题固定两选项、选项转大写、答案去空格并大写 */
    private void normalize(Question question) {
        if (Objects.equals(question.getType(), 3)) {
            question.setOptions(new ArrayList<>(List.of("正确", "错误")));
        }
        if (question.getAnswer() != null) {
            question.setAnswer(question.getAnswer().replaceAll("\\s", "").toUpperCase());
        }
        if (question.getDifficulty() == null) {
            question.setDifficulty(1);
        }
    }

    /** 批量补全课程名称（Feign 失败降级，不阻断主流程） */
    private void fillCourseName(List<Question> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        List<Long> courseIds = list.stream()
                .map(Question::getCourseId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (courseIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = new LinkedHashMap<>();
        try {
            List<CourseSimpleInfoDTO> courses = courseClient.queryCourseSimpleInfoList(courseIds);
            if (courses != null) {
                courses.stream()
                        .filter(c -> c.getId() != null && c.getName() != null)
                        .forEach(c -> nameMap.put(c.getId(), c.getName()));
            }
        } catch (Exception e) {
            log.warn("补全课程名称失败，降级为占位名称：{}", e.getMessage());
        }
        list.forEach(q -> q.setCourseName(
                q.getCourseId() == null ? null : nameMap.getOrDefault(q.getCourseId(), "课程 #" + q.getCourseId())));
    }
}
