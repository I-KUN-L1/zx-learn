package com.zhixing.course.domain.vo;

import com.zhixing.course.domain.po.Course;
import com.zhixing.course.domain.po.CourseCatalogue;
import lombok.Data;

import java.util.List;

/**
 * 课程视图
 */
@Data
public class CourseVO {

    private Long id;
    private String name;
    private String coverUrl;
    private Long price;
    private Long categoryIdLv1;
    private Long categoryIdLv2;
    private Long categoryIdLv3;
    private Long teacherId;
    private Integer status;
    private Integer free;
    private Integer publishTimes;
    private String description;
    private List<CourseCatalogue> catalogues;

    public static CourseVO of(Course course) {
        CourseVO vo = new CourseVO();
        vo.setId(course.getId());
        vo.setName(course.getName());
        vo.setCoverUrl(course.getCoverUrl());
        vo.setPrice(course.getPrice());
        vo.setCategoryIdLv1(course.getCategoryIdLv1());
        vo.setCategoryIdLv2(course.getCategoryIdLv2());
        vo.setCategoryIdLv3(course.getCategoryIdLv3());
        vo.setTeacherId(course.getTeacherId());
        vo.setStatus(course.getStatus());
        vo.setFree(course.getFree());
        vo.setPublishTimes(course.getPublishTimes());
        vo.setDescription(course.getDescription());
        return vo;
    }
}
