package com.zhixing.course.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 课程草稿表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("course_draft")
public class CourseDraft extends BasePO {

    /** 对应正式课程 id（首次编辑为 null） */
    private Long courseId;
    private String name;
    private String coverUrl;
    private Long price;
    private Long categoryIdLv1;
    private Long categoryIdLv2;
    private Long categoryIdLv3;
    private Long teacherId;
    private Integer free;
    private String description;
    /** 编辑步骤：1-基础信息 2-目录 3-视频 4-题目 */
    private Integer step;
    /** 是否已提交上架 */
    private Integer submitted;
}
