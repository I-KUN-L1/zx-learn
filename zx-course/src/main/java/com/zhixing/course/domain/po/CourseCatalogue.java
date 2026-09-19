package com.zhixing.course.domain.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

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
    /** 讲义正文（Markdown），章节内容页「讲义」区渲染 */
    private String content;
    /** 本节要点，多个要点以 "|" 分隔（章节内容页以标签形式展示） */
    private String keyPoints;
    /** 学习资料地址（可选，空则前端不渲染资料区） */
    private String attachmentUrl;
    /** 学习资料名称（下载按钮文案） */
    private String attachmentName;

    /**
     * 子小节（仅章节节点持有，用于前端树形/章节折叠展示）。
     * <p>
     * 不落库：课程目录在库中是"章节 + 小节"的扁平表（parent_id 关联），
     * 由 {@code CourseService#getCourseById} 组装成两级结构后下发，
     * 前端无需再次按 parentId 归并。
     */
    @TableField(exist = false)
    private List<CourseCatalogue> sections;
}
