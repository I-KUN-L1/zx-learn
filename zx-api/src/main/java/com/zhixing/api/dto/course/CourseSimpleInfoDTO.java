package com.zhixing.api.dto.course;

import lombok.Data;

import java.io.Serializable;

/**
 * 课程简信息（跨服务）
 */
@Data
public class CourseSimpleInfoDTO implements Serializable {

    private Long id;
    private String name;
    private String coverUrl;
    private Long price;
    private Long categoryIdLv1;
    private Long categoryIdLv2;
    private Long categoryIdLv3;
    private Integer status;
    private Integer free;
}
