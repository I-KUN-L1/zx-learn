package com.zhixing.api.dto.course;

import lombok.Data;

import java.io.Serializable;

/**
 * 课程目录（章节/小节）信息 DTO
 */
@Data
public class CourseCatalogueDTO implements Serializable {

    private Long id;

    private Long courseId;

    /** 目录名称 */
    private String name;

    /** 顺序 */
    private Integer index;

    /** 类型：1-章 2-小节 */
    private Integer chapterType;

    /** 父章 id（小节所属章） */
    private Long parentId;

    /** 时长（秒） */
    private Integer duration;

    /** 是否试看：1 可试看 */
    private Integer trailer;

    /** 讲义正文（Markdown） */
    private String content;

    /** 本节要点，"|" 分隔 */
    private String keyPoints;

    /** 学习资料地址 */
    private String attachmentUrl;

    /** 学习资料名称 */
    private String attachmentName;
}
