package com.zhixing.course.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 课程（正式表）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("course")
public class Course extends BasePO {

    private String name;
    /** 封面 */
    private String coverUrl;
    /** 价格（分） */
    private Long price;
    /** 一级分类 */
    private Long categoryIdLv1;
    private Long categoryIdLv2;
    private Long categoryIdLv3;
    /** 老师 id */
    private Long teacherId;
    /** 状态：1-上架 0-下架 */
    private Integer status;
    /** 是否免费：0-收费 1-免费 */
    private Integer free;
    /** 发布次数 */
    private Integer publishTimes;
    /** 简介 */
    private String description;
    /** 章数 */
    private Integer chapterCount;
    /** 小节数 */
    private Integer subjectCount;
    /** 销量 */
    private Integer sold;
}
