package com.zhixing.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixing.api.dto.course.CourseSimpleInfoDTO;
import com.zhixing.common.domain.PageDTO;
import com.zhixing.common.domain.PageQuery;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.exceptions.BizIllegalException;
import com.zhixing.common.utils.BeanUtils;
import com.zhixing.common.utils.StringUtils;
import com.zhixing.course.domain.dto.CourseFormDTO;
import com.zhixing.course.domain.po.Course;
import com.zhixing.course.domain.po.CourseCatalogue;
import com.zhixing.course.domain.po.CourseDraft;
import com.zhixing.course.domain.vo.CourseVO;
import com.zhixing.course.mapper.CourseCatalogueMapper;
import com.zhixing.course.mapper.CourseDraftMapper;
import com.zhixing.course.mapper.CourseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 课程服务
 */
@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseMapper courseMapper;
    private final CourseDraftMapper courseDraftMapper;
    private final CourseCatalogueMapper catalogueMapper;

    /**
     * 保存课程基本信息（写入草稿表）
     */
    public Long saveBaseInfo(CourseFormDTO form) {
        if (StringUtils.isBlank(form.getName())) {
            throw new BadRequestException("课程名称不能为空");
        }
        CourseDraft draft;
        if (form.getId() != null) {
            draft = courseDraftMapper.selectById(form.getId());
            if (draft == null) {
                throw new BadRequestException("课程草稿不存在");
            }
        } else {
            draft = new CourseDraft();
        }
        BeanUtils.copyProperties(form, draft);
        if (draft.getId() == null) {
            courseDraftMapper.insert(draft);
        } else {
            courseDraftMapper.updateById(draft);
        }
        return draft.getId();
    }

    public CourseDraft getBaseInfo(Long id) {
        return courseDraftMapper.selectById(id);
    }

    public CourseVO getCourseById(Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new BadRequestException("课程不存在");
        }
        CourseVO vo = CourseVO.of(course);
        vo.setCatalogues(catalogueMapper.selectList(
                new LambdaQueryWrapper<CourseCatalogue>()
                        .eq(CourseCatalogue::getCourseId, id)
                        .orderByAsc(CourseCatalogue::getIndex)));
        return vo;
    }

    /**
     * 课程上架：草稿同步到正式表
     */
    public void upShelf(Long draftId) {
        CourseDraft draft = courseDraftMapper.selectById(draftId);
        if (draft == null) {
            throw new BadRequestException("课程草稿不存在");
        }
        checkBeforeUpShelf(draftId);
        Course course;
        if (draft.getCourseId() != null) {
            course = courseMapper.selectById(draft.getCourseId());
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
            courseDraftMapper.updateById(draft);
        } else {
            courseMapper.updateById(course);
        }
    }

    public void downShelf(Long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BadRequestException("课程不存在");
        }
        course.setStatus(0);
        courseMapper.updateById(course);
    }

    public void checkBeforeUpShelf(Long draftId) {
        CourseDraft draft = courseDraftMapper.selectById(draftId);
        if (draft == null) {
            throw new BizIllegalException("请先保存课程基本信息");
        }
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

    public void delete(Long courseId) {
        courseMapper.deleteById(courseId);
    }

    public void checkName(String name) {
        Long count = courseMapper.selectCount(new LambdaQueryWrapper<Course>().eq(Course::getName, name));
        if (count > 0) {
            throw new BizIllegalException("课程名称已存在");
        }
    }

    public PageDTO<CourseVO> pageQuery(PageQuery query, String name, Integer status) {
        LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<Course>()
                .like(StringUtils.isNotBlank(name), Course::getName, name)
                .eq(status != null, Course::getStatus, status)
                .orderByDesc(Course::getCreateTime);
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

    public List<Long> queryCourseIdsByName(String name) {
        return courseMapper.selectList(new LambdaQueryWrapper<Course>().like(Course::getName, name))
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
}
