package com.zhixing.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.api.dto.course.CourseCatalogueDTO;
import com.zhixing.api.dto.course.CourseSimpleInfoDTO;
import com.zhixing.common.constants.Constant;
import com.zhixing.common.constants.UserRole;
import com.zhixing.common.domain.PageDTO;
import com.zhixing.common.domain.PageQuery;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.exceptions.BizIllegalException;
import com.zhixing.common.exceptions.ForbiddenException;
import com.zhixing.common.utils.BeanUtils;
import com.zhixing.common.utils.StringUtils;
import com.zhixing.common.utils.UserContext;
import com.zhixing.course.domain.dto.CatalogueNodeDTO;
import com.zhixing.course.domain.dto.CourseFormDTO;
import com.zhixing.course.domain.po.Course;
import com.zhixing.course.domain.po.CourseCatalogue;
import com.zhixing.course.domain.po.CourseDraft;
import com.zhixing.course.domain.vo.CourseDraftVO;
import com.zhixing.course.domain.vo.CourseVO;
import com.zhixing.course.mapper.CourseCatalogueMapper;
import com.zhixing.course.mapper.CourseDraftMapper;
import com.zhixing.course.mapper.CourseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 课程服务
 */
@Service
@RequiredArgsConstructor
public class CourseService {

    /** 编辑步骤：基础信息 */
    private static final int STEP_BASE_INFO = 1;
    /** 编辑步骤：章节目录 */
    private static final int STEP_CATALOGUE = 2;
    /** 草稿状态：仍在草稿箱（未发布） */
    private static final int SUBMITTED_NO = 0;
    /** 草稿状态：已提交上架 */
    private static final int SUBMITTED_YES = 1;

    /** 课程/章节名称列宽上限（course.name = VARCHAR(128)，course_catalogue.name = VARCHAR(128)） */
    private static final int MAX_NAME_LENGTH = 128;
    /** 课程简介列宽上限（course.description / course_draft.description = VARCHAR(2000)） */
    private static final int MAX_DESCRIPTION_LENGTH = 2000;

    /** 草稿目录 JSON 序列化器（只用于本服务内的简单 POJO，无需注入 Spring 容器里的 ObjectMapper） */
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final TypeReference<List<CatalogueNodeDTO>> CATALOGUE_TYPE =
            new TypeReference<>() {
            };

    private final CourseMapper courseMapper;
    private final CourseDraftMapper courseDraftMapper;
    private final CourseCatalogueMapper catalogueMapper;

    // ============ 课程归属权限矩阵（唯一事实来源） ============
    //
    // 角色：员工 STAFF(1) / 学员 STUDENT(2) / 教师 TEACHER(3)。
    //
    // ┌──────────┬──────────────────────┬──────────────────────────────┐
    // │ 操作     │ 员工(1)              │ 教师(3)                      │
    // ├──────────┼──────────────────────┼──────────────────────────────┤
    // │ 列表     │ 全部课程             │ 仅本人名下（teacher_id=本人）│
    // │ 查看草稿 │ 任意草稿             │ 仅本人草稿                   │
    // │ 编辑草稿 │ 任意草稿             │ 仅本人草稿                   │
    // │ 发布     │ 任意草稿             │ 仅本人草稿                   │
    // │ 上架     │ 任意草稿             │ 仅本人草稿                   │
    // │ 下架     │ 任意课程             │ 仅本人名下课程               │
    // │ 删除     │ 任意课程             │ 仅本人名下课程               │
    // └──────────┴──────────────────────┴──────────────────────────────┘
    //
    // 学员与其他角色：不具备任何课程管理能力（由控制器 @RequireRole fail-closed 拒绝）；
    // 无身份头的服务间内部调用同样按 fail-closed 处理，避免将来新增的内部调用绕过归属校验。
    //
    // 「本人」的判定口径是 course.teacher_id / course_draft.teacher_id（授课老师），
    // 与前端「我的课程」语义一致：管理员代建、但把授课老师指定给某教师的课程，该教师同样可管理。

    /** 当前用户是否员工（不受课程归属限制） */
    private boolean isStaff() {
        return UserContext.hasRole(UserRole.STAFF.getCode());
    }

    /** 当前用户是否教师 */
    private boolean isTeacher() {
        return UserContext.hasRole(UserRole.TEACHER.getCode());
    }

    /**
     * 课程列表的归属范围（配合 {@link #pageQuery}）：员工不过滤返回 {@code null}，
     * 教师返回本人 userId（只看本人名下课程）。
     * <p>
     * 学员/访客的可见性不在这里处理——他们由控制器强制 {@code status = 已上架}（看全站已上架课程）。
     */
    private Long ownerScopeForList() {
        return isStaff() ? null : UserContext.getUser();
    }

    /**
     * 断言当前用户可管理指定课程（下架 / 删除），否则 403。
     * <p>
     * 员工放行；教师要求 {@code course.teacher_id == 当前用户}；其他角色（含无身份头的内部调用）一律拒绝。
     * 归属判定集中在 {@link #isOwnedByCurrentTeacher(Long)}，避免各操作各写一份判断而出现口径漂移。
     *
     * @param action 动作文案（用于错误提示，如「下架」「删除」）
     */
    private void assertCourseManageable(Course course, String action) {
        if (isStaff()) {
            return;
        }
        if (isTeacher() && isOwnedByCurrentTeacher(course.getTeacherId())) {
            return;
        }
        if (isTeacher()) {
            throw new ForbiddenException("只能" + action + "本人名下的课程");
        }
        throw new ForbiddenException("无权限访问该资源");
    }

    /**
     * 断言当前用户可管理指定草稿（查看 / 编辑 / 发布 / 删除），否则 403。
     * 规则与 {@link #assertCourseManageable} 一致，只是归属字段落在草稿表上。
     */
    private void assertDraftManageable(CourseDraft draft, String action) {
        if (isStaff()) {
            return;
        }
        if (isTeacher() && isOwnedByCurrentTeacher(draft.getTeacherId())) {
            return;
        }
        if (isTeacher()) {
            throw new ForbiddenException("只能" + action + "本人名下的课程");
        }
        throw new ForbiddenException("无权限访问该资源");
    }

    /** 归属判定：授课老师字段与当前登录用户一致（任一侧为空都视为「不属于」） */
    private boolean isOwnedByCurrentTeacher(Long teacherId) {
        Long current = UserContext.getUser();
        return current != null && teacherId != null && current.equals(teacherId);
    }

    /**
     * 保存课程基本信息（写入草稿表，即「存草稿箱」）。
     * <p>
     * 逐字段显式赋值而不用 {@code BeanUtils.copyProperties}，原因是两条业务约束：
     * <ol>
     *   <li>表单没有 teacherId，批量拷贝会把草稿里已绑定的授课老师抹成 null；</li>
     *   <li>step / submitted 是服务端状态，不能由前端随意改写（否则草稿能被"伪造成已发布"）。</li>
     * </ol>
     */
    public Long saveBaseInfo(CourseFormDTO form) {
        if (StringUtils.isBlank(form.getName())) {
            throw new BadRequestException("课程名称不能为空");
        }
        String name = form.getName().trim();
        if (name.length() > MAX_NAME_LENGTH) {
            throw new BadRequestException("课程名称过长，最多 " + MAX_NAME_LENGTH + " 个字符");
        }
        if (form.getDescription() != null && form.getDescription().length() > MAX_DESCRIPTION_LENGTH) {
            throw new BadRequestException("课程介绍过长，最多 " + MAX_DESCRIPTION_LENGTH + " 个字符");
        }
        if (form.getPrice() != null && form.getPrice() < 0) {
            throw new BadRequestException("课程价格不能为负数");
        }
        // 免费课价格强制归零，避免「标记免费却挂着价格」被下单链路读到
        if (Integer.valueOf(1).equals(form.getFree()) && form.getPrice() != null && form.getPrice() != 0) {
            throw new BadRequestException("免费课程的价格必须为 0");
        }

        CourseDraft draft;
        boolean create = form.getId() == null;
        if (create) {
            draft = new CourseDraft();
            draft.setSubmitted(SUBMITTED_NO);
        } else {
            draft = courseDraftMapper.selectById(form.getId());
            if (draft == null) {
                throw new BadRequestException("课程草稿不存在");
            }
        }

        draft.setName(name);
        draft.setCoverUrl(form.getCoverUrl());
        draft.setPrice(form.getPrice() == null ? 0L : form.getPrice());
        draft.setCategoryIdLv1(form.getCategoryIdLv1());
        draft.setCategoryIdLv2(form.getCategoryIdLv2());
        draft.setCategoryIdLv3(form.getCategoryIdLv3());
        draft.setFree(form.getFree() == null ? 0 : form.getFree());
        draft.setDescription(form.getDescription());
        if (form.getTeacherId() != null) {
            draft.setTeacherId(form.getTeacherId());
        }
        if (draft.getTeacherId() == null) {
            // 教师端表单里没有「授课老师」选择项。若不兜底，草稿的 teacher_id 恒为 null，
            // checkBeforeUpShelf 会一直拦「请选择授课老师」——「保存成功但永远发不出去」。
            draft.setTeacherId(UserContext.getUser());
        }

        // 目录：只有前端真的提交了目录才覆盖，避免只改基础信息时把已存目录清空
        boolean hasCatalogue = false;
        if (form.getCatalogueList() != null) {
            List<CatalogueNodeDTO> chapters = cleanChapters(form.getCatalogueList());
            hasCatalogue = !chapters.isEmpty();
            draft.setCatalogueJson(writeCatalogueJson(chapters));
            draft.setCatalogueList(chapters);
        } else if (draft.getCatalogueJson() != null) {
            draft.setCatalogueList(readCatalogueJson(draft.getCatalogueJson()));
        }

        // 步骤只前进不回退：填过目录的草稿不会因为再保存一次基础信息而退回第 1 步
        int incomingStep = form.getStep() == null ? STEP_BASE_INFO : form.getStep();
        if (hasCatalogue) {
            incomingStep = Math.max(incomingStep, STEP_CATALOGUE);
        }
        Integer currentStep = draft.getStep();
        draft.setStep(currentStep == null ? incomingStep : Math.max(currentStep, incomingStep));

        if (create) {
            courseDraftMapper.insert(draft);
        } else {
            courseDraftMapper.updateById(draft);
        }
        return draft.getId();
    }

    /**
     * 读取草稿编辑态信息（含目录，供前端表单回填）。
     */
    public CourseDraft getBaseInfo(Long id) {
        CourseDraft draft = courseDraftMapper.selectById(id);
        if (draft == null) {
            return null;
        }
        assertDraftManageable(draft, "查看");
        draft.setCatalogueList(readCatalogueJson(draft.getCatalogueJson()));
        return draft;
    }

    /**
     * 草稿箱分页（只列未发布的草稿）。
     * <p>
     * ⚠ 草稿与正式课程在**两张表**里：本方法查 course_draft，
     * 「已发布（发布态）」列的是 course 表。曾经草稿箱 Tab 误用
     * {@code /courses/page?status=2} 去查 course 表，而该表 status 只有 0/1，
     * 于是草稿箱永远是空的——新建课程"保存成功却看不见"。
     */
    public PageDTO<CourseDraftVO> draftPage(PageQuery query, String name) {
        // 归属收敛：员工看全部草稿，教师只看本人草稿（与下架/删除的归属口径一致）
        Long ownerScope = ownerScopeForList();
        LambdaQueryWrapper<CourseDraft> wrapper = new LambdaQueryWrapper<CourseDraft>()
                .like(StringUtils.isNotBlank(name), CourseDraft::getName, name)
                .eq(ownerScope != null, CourseDraft::getTeacherId, ownerScope)
                .eq(CourseDraft::getSubmitted, SUBMITTED_NO);
        // 排序白名单：只允许按更新时间倒序（草稿箱按"最近编辑"排最符合直觉，也免去注入面）
        query.setSortBy(null);
        Page<CourseDraft> page = courseDraftMapper.selectPage(
                query.toMpPage("update_time", false), wrapper);
        return PageDTO.of(page, CourseDraftVO::of);
    }

    /**
     * 删除草稿箱里的草稿（逻辑删除）。
     * <p>
     * 已发布过的草稿不允许从这里删：它关联着线上正式课程，
     * 要走正式课程的「下架 / 删除」流程，否则会出现"课程还在、草稿没了"的错位。
     */
    public void deleteDraft(Long draftId) {
        CourseDraft draft = courseDraftMapper.selectById(draftId);
        if (draft == null) {
            throw new BadRequestException("课程草稿不存在");
        }
        assertDraftManageable(draft, "删除");
        if (Integer.valueOf(SUBMITTED_YES).equals(draft.getSubmitted())) {
            // 用 400（明确的请求不合法）而不是 BizIllegalException 的默认业务码 1001，
            // 前端能据此把「草稿已发布」和「系统繁忙」区分开。
            throw new BadRequestException("该草稿已发布，请在「已发布」列表中下架或删除");
        }
        courseDraftMapper.deleteById(draftId);
    }

    public CourseVO getCourseById(Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new BadRequestException("课程不存在");
        }
        CourseVO vo = CourseVO.of(course);
        vo.setCatalogues(buildCatalogueTree(catalogueMapper.selectList(
                new LambdaQueryWrapper<CourseCatalogue>()
                        .eq(CourseCatalogue::getCourseId, id)
                        .orderByAsc(CourseCatalogue::getChapterType)
                        .orderByAsc(CourseCatalogue::getIndex))));
        return vo;
    }

    /**
     * 把库中扁平的课程目录（chapter_type 1-章 / 2-小节，parent_id 关联）
     * 组装成前端直接可用的两级结构。
     * <p>
     * 容错：
     * <ul>
     *   <li>孤儿小节（parent_id 指向不存在的章节）提升为顶层节点，避免内容"凭空消失"；</li>
     *   <li>没有任何章节、只有小节的课程，同样按顶层小节展示。</li>
     * </ul>
     */
    private List<CourseCatalogue> buildCatalogueTree(List<CourseCatalogue> flat) {
        if (flat == null || flat.isEmpty()) {
            return List.of();
        }
        Map<Long, CourseCatalogue> chapterById = new LinkedHashMap<>();
        for (CourseCatalogue c : flat) {
            boolean isChapter = c.getChapterType() == null
                    || c.getChapterType() == 1
                    || c.getParentId() == null
                    || c.getParentId() == 0;
            if (isChapter) {
                c.setSections(new ArrayList<>());
                chapterById.put(c.getId(), c);
            }
        }
        List<CourseCatalogue> result = new ArrayList<>(chapterById.values());
        for (CourseCatalogue c : flat) {
            if (chapterById.containsKey(c.getId())) {
                continue;
            }
            CourseCatalogue parent = c.getParentId() == null ? null : chapterById.get(c.getParentId());
            if (parent != null) {
                parent.getSections().add(c);
            } else {
                // 孤儿小节：顶层展示，避免不可见
                c.setSections(new ArrayList<>());
                result.add(c);
            }
        }
        return result;
    }

    /**
     * 课程上架：草稿同步到正式表，并把草稿目录合并进 course_catalogue。
     * <p>
     * 成功后草稿标记 {@code submitted = 1}，即离开草稿箱、出现在「已发布」列表。
     */
    public void upShelf(Long draftId) {
        CourseDraft draft = courseDraftMapper.selectById(draftId);
        if (draft == null) {
            throw new BadRequestException("课程草稿不存在");
        }
        // 归属校验放在字段校验之前：教师探查他人草稿 id 时立即 403，
        // 不会因为 checkBeforeUpShelf 的字段提示而变相泄露「该草稿存在且缺哪些字段」
        assertDraftManageable(draft, "发布");
        checkBeforeUpShelf(draftId);
        Course course;
        if (draft.getCourseId() != null) {
            course = courseMapper.selectById(draft.getCourseId());
            if (course == null) {
                course = new Course();
            }
        } else {
            course = new Course();
        }
        course.setName(draft.getName());
        course.setCoverUrl(draft.getCoverUrl());
        course.setPrice(draft.getPrice());
        course.setCategoryIdLv1(draft.getCategoryIdLv1());
        course.setCategoryIdLv2(draft.getCategoryIdLv2());
        course.setCategoryIdLv3(draft.getCategoryIdLv3());
        course.setTeacherId(draft.getTeacherId());
        course.setFree(draft.getFree());
        course.setDescription(draft.getDescription());
        course.setStatus(1);
        course.setPublishTimes((course.getPublishTimes() == null ? 0 : course.getPublishTimes()) + 1);
        if (course.getId() == null) {
            courseMapper.insert(course);
            draft.setCourseId(course.getId());
        } else {
            courseMapper.updateById(course);
        }

        // 草稿目录 → 正式目录表，并回填章数/小节数（课程卡片与详情页依赖这两个数）
        int[] counts = syncCatalogue(course.getId(), readCatalogueJson(draft.getCatalogueJson()));
        if (counts != null) {
            course.setChapterCount(counts[0]);
            course.setSubjectCount(counts[1]);
            courseMapper.updateById(course);
        }

        draft.setSubmitted(SUBMITTED_YES);
        courseDraftMapper.updateById(draft);
    }

    /**
     * 草稿目录 → 正式目录表 {@code course_catalogue}。
     * <p>
     * 用「按名称匹配的增量合并」，而不是「先清空再重建」：正式表里的章节很可能已经被
     * 2026-09-14 的内容迁移写入了讲义正文 / 要点 / 学习资料，清空重建会把讲义全部丢掉
     * （课程内容页立刻变空）。规则：
     * <ul>
     *   <li>草稿里有、正式表里同名 → 复用原行，只更新排序与父子关系（讲义原样保留）；</li>
     *   <li>草稿里有、正式表里没有 → 新增；</li>
     *   <li>正式表里有、草稿里没有 → 逻辑删除（用户确实删掉了这一节）。</li>
     * </ul>
     * 同名重复按出现先后顺序配对，因此可重复执行（幂等）。
     *
     * @return {章数, 小节数}；草稿没有目录时返回 null，表示「不动正式目录」
     */
    private int[] syncCatalogue(Long courseId, List<CatalogueNodeDTO> chapters) {
        if (courseId == null || chapters == null || chapters.isEmpty()) {
            return null;
        }
        List<CourseCatalogue> existing = catalogueMapper.selectList(
                new LambdaQueryWrapper<CourseCatalogue>()
                        .eq(CourseCatalogue::getCourseId, courseId)
                        .orderByAsc(CourseCatalogue::getIndex));

        Map<String, Deque<CourseCatalogue>> pool = new LinkedHashMap<>();
        for (CourseCatalogue c : existing) {
            pool.computeIfAbsent(catalogueKey(c.getChapterType(), c.getName()), k -> new ArrayDeque<>()).add(c);
        }

        Set<Long> kept = new HashSet<>();
        int chapterIndex = 0;
        int subjectCount = 0;
        for (CatalogueNodeDTO chapter : chapters) {
            chapterIndex++;
            CourseCatalogue chapterRow = poll(pool, 1, chapter.getName());
            if (chapterRow == null) {
                chapterRow = new CourseCatalogue();
                chapterRow.setCourseId(courseId);
                chapterRow.setName(chapter.getName());
                chapterRow.setChapterType(1);
                chapterRow.setParentId(0L);
                chapterRow.setIndex(chapterIndex);
                chapterRow.setTrailer(0);
                catalogueMapper.insert(chapterRow);
            } else {
                chapterRow.setCourseId(courseId);
                chapterRow.setParentId(0L);
                chapterRow.setIndex(chapterIndex);
                catalogueMapper.updateById(chapterRow);
            }
            kept.add(chapterRow.getId());

            List<CatalogueNodeDTO> sections = chapter.getSections() == null ? List.of() : chapter.getSections();
            int sectionIndex = 0;
            for (CatalogueNodeDTO section : sections) {
                sectionIndex++;
                subjectCount++;
                CourseCatalogue row = poll(pool, 2, section.getName());
                if (row == null) {
                    row = new CourseCatalogue();
                    row.setCourseId(courseId);
                    row.setName(section.getName());
                    row.setChapterType(2);
                    row.setParentId(chapterRow.getId());
                    row.setIndex(sectionIndex);
                    row.setTrailer(0);
                    catalogueMapper.insert(row);
                } else {
                    row.setCourseId(courseId);
                    row.setParentId(chapterRow.getId());
                    row.setIndex(sectionIndex);
                    catalogueMapper.updateById(row);
                }
                kept.add(row.getId());
            }
        }

        // 草稿里已经不存在的旧目录 → 逻辑删除（行仍在库里，讲义可回溯）
        for (CourseCatalogue c : existing) {
            if (!kept.contains(c.getId())) {
                catalogueMapper.deleteById(c.getId());
            }
        }
        return new int[]{chapters.size(), subjectCount};
    }

    private static String catalogueKey(Integer chapterType, String name) {
        return (chapterType == null ? 0 : chapterType) + "\u0000" + (name == null ? "" : name);
    }

    private static CourseCatalogue poll(Map<String, Deque<CourseCatalogue>> pool, int chapterType, String name) {
        Deque<CourseCatalogue> deque = pool.get(catalogueKey(chapterType, name));
        return deque == null ? null : deque.pollFirst();
    }

    /**
     * 清洗两级目录：丢弃名称为空的占位行，校验列宽。
     * <p>
     * 不静默截断 —— 名称超长直接报错。静默截断会把"保存成功"变成"数据悄悄变样"，
     * 这类坑在本项目已经踩过一次（封面 URL 被砍尾）。
     */
    private List<CatalogueNodeDTO> cleanChapters(List<CatalogueNodeDTO> chapters) {
        List<CatalogueNodeDTO> result = new ArrayList<>();
        if (chapters == null) {
            return result;
        }
        for (CatalogueNodeDTO chapter : chapters) {
            if (chapter == null || StringUtils.isBlank(chapter.getName())) {
                continue;
            }
            CatalogueNodeDTO clean = new CatalogueNodeDTO();
            clean.setName(requireName(chapter.getName(), "章节"));
            List<CatalogueNodeDTO> sections = new ArrayList<>();
            if (chapter.getSections() != null) {
                for (CatalogueNodeDTO section : chapter.getSections()) {
                    if (section == null || StringUtils.isBlank(section.getName())) {
                        continue;
                    }
                    CatalogueNodeDTO cleanSection = new CatalogueNodeDTO();
                    cleanSection.setName(requireName(section.getName(), "小节"));
                    sections.add(cleanSection);
                }
            }
            clean.setSections(sections);
            result.add(clean);
        }
        return result;
    }

    private static String requireName(String raw, String label) {
        String name = raw.trim();
        if (name.length() > MAX_NAME_LENGTH) {
            throw new BadRequestException(label + "名称过长，最多 " + MAX_NAME_LENGTH + " 个字符");
        }
        return name;
    }

    private String writeCatalogueJson(List<CatalogueNodeDTO> chapters) {
        try {
            return JSON.writeValueAsString(chapters);
        } catch (Exception e) {
            throw new BadRequestException("章节目录格式不正确");
        }
    }

    /** 解析草稿目录 JSON；脏数据不抛异常，退化为空目录，保证编辑页仍可打开 */
    private List<CatalogueNodeDTO> readCatalogueJson(String json) {
        if (StringUtils.isBlank(json)) {
            return List.of();
        }
        try {
            List<CatalogueNodeDTO> nodes = JSON.readValue(json, CATALOGUE_TYPE);
            return nodes == null ? List.of() : nodes;
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 课程下架。
     * <p>
     * <b>下架的即时生效机制</b>：本方法只把 {@code course.status} 置为 0；
     * "下架后立刻从各展示位消失"靠<b>查询侧过滤</b>保证，而不是靠失效缓存/重建索引：
     * <ul>
     *   <li>{@code GET /courses/page}（课程列表 / 首页热门 / 课程发现）——非员工/教师角色被强制
     *       只看 {@code status=1}，见 {@code CourseController#page}；</li>
     *   <li>{@code GET /courses/{id}}（课程详情）——访客访问下架课程按"课程不存在"拒绝，
     *       见 {@code CourseController#getById}；</li>
     *   <li>{@code GET /course/name}（按名称检索课程 id）——只返回已上架课程，
     *       见 {@link #queryCourseIdsByName(String)}；</li>
     *   <li>交易侧下单/加购——下单前校验课程状态，下架课程不可新增购买，
     *       见 {@code zx-trade} 的 CartService#add / OrderService#placeOrder / #freeCourse。</li>
     * </ul>
     * 本项目课程数据<b>没有 Redis 缓存、也没有 ES 索引</b>（zx-search 未接搜索引擎），
     * 因此不存在"缓存没清导致残留"的问题；日后若给课程加缓存/索引，
     * 必须在本方法内补失效逻辑，否则下架将重新出现"改了库还能看到"的残留。
     */
    public void downShelf(Long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BadRequestException("课程不存在");
        }
        // 归属校验：教师只能下架本人名下课程（与「删除」同一口径，见类内权限矩阵）
        assertCourseManageable(course, "下架");
        course.setStatus(Constant.COURSE_STATUS_OFF_SHELF);
        courseMapper.updateById(course);
    }

    public void checkBeforeUpShelf(Long draftId) {
        CourseDraft draft = courseDraftMapper.selectById(draftId);
        if (draft == null) {
            throw new BizIllegalException("请先保存课程基本信息");
        }
        assertDraftManageable(draft, "发布");
        if (StringUtils.isBlank(draft.getName())) {
            throw new BizIllegalException("请填写课程名称");
        }
        if (draft.getCategoryIdLv1() == null) {
            throw new BizIllegalException("请选择课程分类");
        }
        if (draft.getPrice() == null) {
            throw new BizIllegalException("请填写课程价格");
        }
        if (draft.getTeacherId() == null) {
            throw new BizIllegalException("请选择授课老师");
        }
    }

    /**
     * 删除课程（逻辑删除，{@code course.deleted = 1}）。
     * <p>
     * <b>角色权限校验规则（与控制器 {@code @RequireRole} 注解双重把关）</b>：
     * <ul>
     *   <li>员工 STAFF(1)：可删除<b>任意</b>课程，不受归属限制；</li>
     *   <li>教师 TEACHER(3)：仅可删除<b>本人名下</b>课程 —— 要求
     *       {@code course.teacher_id == 当前登录用户}，否则 403；</li>
     *   <li>其他角色（含学员、以及没有角色头的内部调用）：一律 403 —— 注解层已经拒绝，
     *       这里再兜底，防止将来新增的内部调用绕过注解直接进 Service 造成越权删除。</li>
     * </ul>
     * <b>生效范围</b>：只逻辑删除课程本体。不级联删除章节/小节（{@code course_catalogue}）、
     * 学员课表与订单 —— 已购学员的学习资产与历史交易必须可追溯；
     * 删除后的课程同样被上面的查询侧过滤规则挡住，不会残留在任何展示位。
     * <p>
     * ⚠ 归属判定用 {@code teacher_id}（授课老师），与「我的课程」语义一致：
     * 教师能删除自己授课的课程，包括管理员代为创建、但把授课老师指定为该教师的课程。
     * 历史数据若 {@code teacher_id} 为空，则该课程只有员工(1)能删。
     */
    public void delete(Long courseId) {
        if (courseId == null) {
            throw new BadRequestException("课程 id 不能为空");
        }
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BadRequestException("课程不存在");
        }
        // 归属校验：统一走 assertCourseManageable（员工不受限 / 教师仅本人课程 / 其他 403）
        assertCourseManageable(course, "删除");
        courseMapper.deleteById(courseId);
    }

    public void checkName(String name) {
        Long count = courseMapper.selectCount(new LambdaQueryWrapper<Course>().eq(Course::getName, name));
        if (count > 0) {
            throw new BizIllegalException("课程名称已存在");
        }
    }

    /** 门户检索排序口径：销量倒序（精品好课） */
    private static final String PORTAL_SORT_BEST = "best";
    /** 门户检索排序口径：仅免费课 */
    private static final String PORTAL_SORT_FREE = "free";
    /** 门户检索默认返回条数 */
    private static final int DEFAULT_PORTAL_LIMIT = 8;
    /** 门户检索最大返回条数（防止调用方传入极大值拖垮查询） */
    private static final int MAX_PORTAL_LIMIT = 50;

    /** 排序白名单：前端排序字段 → 数据库列名（防 SQL 注入；enrollNum 为学习人数字段别名） */
    private static final Map<String, String> SORT_FIELD_MAPPING = Map.of(
            "enrollNum", "sold",
            "price", "price",
            "id", "id",
            "sold", "sold");

    /**
     * 课程分页（课程列表 / 首页热门 / 课程发现 / 管理端课程工作台共用）。
     *
     * @param status    状态过滤；{@code null} 表示不按状态过滤（仅员工/教师工作台会出现）
     * @param ownerScope 归属范围：{@code null} 表示不限（员工、以及学员/访客看全站已上架）；
     *                   非空表示只看该 teacher_id 名下课程（教师工作台）
     */
    public PageDTO<CourseVO> pageQuery(PageQuery query, String name, Integer status, Long ownerScope) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<Course>()
                .like(StringUtils.isNotBlank(name), Course::getName, name)
                .eq(status != null, Course::getStatus, status)
                .eq(ownerScope != null, Course::getTeacherId, ownerScope);
        // 排序字段白名单映射：未知/非法字段清除，走默认排序（id 倒序=最新上架），防注入且保证排序生效
        String sortBy = query.getSortBy();
        if (StringUtils.isNotBlank(sortBy)) {
            String column = SORT_FIELD_MAPPING.get(sortBy);
            if (column == null) {
                query.setSortBy(null);
            } else {
                query.setSortBy(column);
                query.setIsAsc(Boolean.TRUE.equals(query.getIsAsc()));
            }
        }
        Page<Course> page = courseMapper.selectPage(query.toMpPage("id", false), wrapper);
        return PageDTO.of(page, CourseVO::of);
    }

    // ============ 内部接口实现 ============

    public List<CourseSimpleInfoDTO> querySimpleInfoList(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return courseMapper.selectBatchIds(ids).stream()
                .map(c -> BeanUtils.copyBean(c, CourseSimpleInfoDTO.class))
                .collect(Collectors.toList());
    }

    public CourseSimpleInfoDTO queryCourseInfoById(Long id) {
        Course course = courseMapper.selectById(id);
        return course == null ? null : BeanUtils.copyBean(course, CourseSimpleInfoDTO.class);
    }

    /**
     * 按课程名模糊检索课程 id（供搜索服务按关键词定位课程）。
     * <p>
     * 只返回<b>已上架</b>课程：搜索结果属于面向用户的展示位，已下架课程不得出现在这里
     * （修复"课程下架后仍能被搜到"）。
     * <p>
     * 注意与 {@link #querySimpleInfoList(List)} 的区别：后者用于给「已购课程 / 历史订单 / 购物车」
     * 回显课程名与封面，属于交易与学习资产回显，<b>不能</b>按状态过滤，
     * 否则下架后学员的历史记录会变成空白。交易侧"不允许新增购买"的校验放在调用方，
     * 见 zx-trade 的 CartService#add 与 OrderService#placeOrder / #freeCourse。
     */
    public List<Long> queryCourseIdsByName(String name) {
        return courseMapper.selectList(new LambdaQueryWrapper<Course>()
                        .like(Course::getName, name)
                        .eq(Course::getStatus, Constant.COURSE_STATUS_ON_SHELF))
                .stream().map(Course::getId).collect(Collectors.toList());
    }

    /**
     * 查询全部已上架课程（供学情分析推荐使用）
     */
    public List<CourseSimpleInfoDTO> queryAllSimpleInfo() {
        return courseMapper.selectList(new LambdaQueryWrapper<Course>()
                        .eq(Course::getStatus, 1)
                        .orderByDesc(Course::getId))
                .stream()
                .map(c -> BeanUtils.copyBean(c, CourseSimpleInfoDTO.class))
                .collect(Collectors.toList());
    }

    /**
     * 门户课程检索（供 zx-search 的 {@code /courses/portal} 与 {@code /recommend/**} 消费）。
     * <p>
     * <b>只返回已上架课程</b>：搜索结果与推荐位都属于面向用户的展示位，
     * 必须与课程上下架状态强一致——否则会出现「课程已下架、搜索里还搜得到」这类
     * 与真实数据不符的误导（这是 zx-search 此前恒定返回示例课程的根因之一）。
     * <p>
     * 排序口径：
     * <ul>
     *   <li>{@code best}：销量倒序（精品好课 / 热门推荐）；</li>
     *   <li>{@code free}：仅免费课，按 id 倒序；</li>
     *   <li>{@code new} 或其它值：按 id 倒序（最新上架，默认口径）。</li>
     * </ul>
     *
     * @param keyword 课程名模糊关键字，为空表示不限
     * @param sort    排序口径，见上
     * @param limit   返回条数上限，越界自动收敛到 [1, {@value #MAX_PORTAL_LIMIT}]
     */
    public List<CourseSimpleInfoDTO> portalQuery(String keyword, String sort, Integer limit) {
        int size = limit == null ? DEFAULT_PORTAL_LIMIT : limit;
        size = Math.max(1, Math.min(size, MAX_PORTAL_LIMIT));
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<Course>()
                .eq(Course::getStatus, Constant.COURSE_STATUS_ON_SHELF)
                .like(StringUtils.isNotBlank(keyword), Course::getName, keyword);
        if (PORTAL_SORT_BEST.equals(sort)) {
            wrapper.orderByDesc(Course::getSold).orderByDesc(Course::getId);
        } else if (PORTAL_SORT_FREE.equals(sort)) {
            wrapper.eq(Course::getFree, 1).orderByDesc(Course::getId);
        } else {
            wrapper.orderByDesc(Course::getId);
        }
        // 用分页对象限制条数，而不是拼 LIMIT 字符串（后者是注入面）
        return courseMapper.selectPage(new Page<>(1, size), wrapper).getRecords().stream()
                .map(c -> BeanUtils.copyBean(c, CourseSimpleInfoDTO.class))
                .collect(Collectors.toList());
    }

    /**
     * 批量查询课程目录（章节/小节）信息（供学习服务聚合学习记录展示）
     */
    public List<CourseCatalogueDTO> queryCatalogueList(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return catalogueMapper.selectBatchIds(ids).stream()
                .map(CourseService::toCatalogueDto)
                .collect(Collectors.toList());
    }

    /**
     * 查询指定课程的全部目录（章节 + 小节），按 index 升序。
     * <p>
     * 供学习服务判断"整门课程是否学完"（小节总数 vs 已完成数），
     * 从而在课程完成时发放课程级积分奖励。
     */
    public List<CourseCatalogueDTO> queryCataloguesByCourse(Long courseId) {
        if (courseId == null) {
            return List.of();
        }
        return catalogueMapper.selectList(new LambdaQueryWrapper<CourseCatalogue>()
                        .eq(CourseCatalogue::getCourseId, courseId)
                        .orderByAsc(CourseCatalogue::getIndex))
                .stream()
                .map(CourseService::toCatalogueDto)
                .collect(Collectors.toList());
    }

    /**
     * 目录 PO → DTO（含章节内容字段：讲义正文 / 要点 / 学习资料）。
     * 两处查询共用，避免字段漏映射导致下游拿不到讲义内容。
     */
    private static CourseCatalogueDTO toCatalogueDto(CourseCatalogue c) {
        CourseCatalogueDTO dto = new CourseCatalogueDTO();
        dto.setId(c.getId());
        dto.setCourseId(c.getCourseId());
        dto.setName(c.getName());
        dto.setIndex(c.getIndex());
        dto.setChapterType(c.getChapterType());
        dto.setParentId(c.getParentId());
        dto.setDuration(c.getDuration());
        dto.setTrailer(c.getTrailer());
        dto.setContent(c.getContent());
        dto.setKeyPoints(c.getKeyPoints());
        dto.setAttachmentUrl(c.getAttachmentUrl());
        dto.setAttachmentName(c.getAttachmentName());
        return dto;
    }
}
