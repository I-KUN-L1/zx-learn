package com.zhixing.course.domain.dto;

import lombok.Data;

/**
 * 课程基本信息表单
 */
@Data
public class CourseFormDTO {

    private Long id;
    private String name;
    private String coverUrl;
    private Long price;
    private Long categoryIdLv1;
    private Long categoryIdLv2;
    private Long categoryIdLv3;
    private Long teacherId;
    private Integer free;
    private String description;
}
