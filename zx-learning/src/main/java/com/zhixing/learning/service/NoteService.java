package com.zhixing.learning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixing.api.client.course.CourseClient;
import com.zhixing.api.dto.course.CourseSimpleInfoDTO;
import com.zhixing.common.domain.PageDTO;
import com.zhixing.common.domain.PageQuery;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.utils.StringUtils;
import com.zhixing.common.utils.UserContext;
import com.zhixing.learning.domain.po.Note;
import com.zhixing.learning.mapper.NoteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 学习笔记服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoteService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final NoteMapper noteMapper;
    private final CourseClient courseClient;

    /**
     * 新增笔记
     */
    public Long add(Note note) {
        if (note == null || StringUtils.isBlank(note.getContent())) {
            throw new BadRequestException("笔记内容不能为空");
        }
        note.setUserId(UserContext.getUserId());
        noteMapper.insert(note);
        return note.getId();
    }

    /**
     * 更新自己的笔记
     */
    public void update(Note note) {
        Note exist = getOwned(note.getId());
        exist.setContent(note.getContent());
        exist.setPrivacy(note.getPrivacy());
        noteMapper.updateById(exist);
    }

    /**
     * 删除自己的笔记
     */
    public void delete(Long id) {
        getOwned(id);
        noteMapper.deleteById(id);
    }

    /**
     * 分页查询当前用户笔记（对齐前端 NoteVO 契约）：
     * 聚合课程名称（course 服务），随手记（courseId=0）降级展示"随手记"。
     *
     * @param courseId 可选：只看某门课的笔记
     * @param lessonId 可选：只看某个小节（目录小节 id）的笔记 —— 课程内容页「本节笔记」用
     */
    public PageDTO<Map<String, Object>> page(PageQuery query, Long courseId, Long lessonId) {
        Page<Note> page = noteMapper.selectPage(query.toMpPage("create_time", false),
                new LambdaQueryWrapper<Note>()
                        .eq(Note::getUserId, UserContext.getUserId())
                        .eq(courseId != null && courseId > 0, Note::getCourseId, courseId)
                        .eq(lessonId != null && lessonId > 0, Note::getLessonId, lessonId));

        List<Note> records = page.getRecords();
        if (records.isEmpty()) {
            return PageDTO.empty(page.getTotal(), page.getPages());
        }

        Map<Long, String> courseNames = loadCourseNames(records);

        List<Map<String, Object>> list = records.stream().map(n -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("userId", n.getUserId());
            m.put("courseId", n.getCourseId() == null ? 0 : n.getCourseId());
            m.put("lessonId", n.getLessonId() == null ? 0 : n.getLessonId());
            m.put("courseName", courseNames.getOrDefault(n.getCourseId(), "随手记"));
            m.put("content", n.getContent());
            m.put("createTime", n.getCreateTime() == null ? null : TIME_FMT.format(n.getCreateTime()));
            return m;
        }).collect(Collectors.toList());
        return PageDTO.of(page, list);
    }

    /** 聚合课程名称：课程服务不可用时降级返回空集合，笔记仍可展示 */
    private Map<Long, String> loadCourseNames(List<Note> notes) {
        List<Long> courseIds = notes.stream()
                .map(Note::getCourseId)
                .filter(cid -> cid != null && cid > 0)
                .distinct()
                .collect(Collectors.toList());
        if (courseIds.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return courseClient.queryCourseSimpleInfoList(courseIds).stream()
                    .collect(Collectors.toMap(CourseSimpleInfoDTO::getId,
                            c -> c.getName() == null ? "" : c.getName(), (a, b) -> a));
        } catch (Exception e) {
            log.warn("笔记课程名称聚合失败（课程服务不可用，降级）: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * 校验笔记归属当前用户
     */
    private Note getOwned(Long id) {
        Note note = noteMapper.selectById(id);
        if (note == null || !note.getUserId().equals(UserContext.getUserId())) {
            throw new BadRequestException("笔记不存在或无权操作");
        }
        return note;
    }
}
