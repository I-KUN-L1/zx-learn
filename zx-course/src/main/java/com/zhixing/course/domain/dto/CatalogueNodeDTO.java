package com.zhixing.course.domain.dto;

import lombok.Data;

import java.util.List;

/**
 * 草稿章节目录节点（两级：章 → 小节）。
 * <p>
 * 对应前端「新建课程」分步表单第 2 步提交的结构，落库时序列化为
 * {@code course_draft.catalogue_json}。上架（upShelf）时由
 * {@code CourseService#syncCatalogue} 合并进正式目录表 {@code course_catalogue}。
 */
@Data
public class CatalogueNodeDTO {

    /** 前端回填时携带的既有目录 id（新增节点为 null，合并时按名称匹配而非依赖该 id） */
    private Long id;

    /** 目录名称（章节名 / 小节名） */
    private String name;

    /** 子小节（仅章节点持有；小节节点为 null） */
    private List<CatalogueNodeDTO> sections;
}
