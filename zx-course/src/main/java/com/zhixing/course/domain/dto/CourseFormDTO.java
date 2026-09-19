package com.zhixing.course.domain.dto;

import lombok.Data;

import java.util.List;

/**
 * 课程基本信息表单（草稿保存 / 上架校验共用）。
 */
@Data
public class CourseFormDTO {

    /** 草稿 id：为空表示新建；不为空表示更新既有草稿 */
    private Long id;
    private String name;
    private String coverUrl;
    /** 价格（分） */
    private Long price;
    private Long categoryIdLv1;
    private Long categoryIdLv2;
    private Long categoryIdLv3;
    private Long teacherId;
    /** 是否免费：0-收费 1-免费 */
    private Integer free;
    private String description;
    /** 编辑步骤：1-基础信息 2-目录 3-视频 4-题目（缺省视为 1；服务端只前进不回退） */
    private Integer step;
    /**
     * 草稿章节目录（两级）。
     * <p>
     * ⚠ 历史缺陷：该字段原先既不在本 DTO 上、也没有对应的库列，
     * 前端第 2 步填的章节在保存时被**静默丢弃**（Jackson 默认忽略未知字段），
     * 表现为「保存成功，但章节再打开就没了」。现已持久化到 course_draft.catalogue_json。
     */
    private List<CatalogueNodeDTO> catalogueList;
}
