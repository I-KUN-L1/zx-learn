package com.zhixing.course.domain.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 课程章节目录
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("course_catalogue")
public class CourseCatalogue extends BasePO {

    private Long courseId;
    /** 目录名称 */
    private String name;
    /** 媒资 id */
    private Long mediaId;
    /** 顺序 */
    @TableField("`index`")
    private Integer index;
    /** 类型：1-章 2-小节 */
    private Integer chapterType;
    /** 父章 id（小节所属章） */
    private Long parentId;
    /** 时长（秒） */
    private Integer duration;
    /** 是否试看 */
    private Integer trailer;
}
