package com.zhixing.course.domain.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import com.zhixing.course.domain.dto.CatalogueNodeDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

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
    /** 草稿章节目录（JSON 数组，元素为 {name, sections:[{name}]}） */
    private String catalogueJson;
    /** 编辑步骤：1-基础信息 2-目录 3-视频 4-题目 */
    private Integer step;
    /** 是否已提交上架：0-草稿箱 1-已发布 */
    private Integer submitted;

    /**
     * 草稿章节目录（反序列化自 {@link #catalogueJson}，不落库）。
     * <p>
     * 下发给前端「编辑课程」表单时用它回填第 2 步——草稿章节必须能原样读回来，
     * 否则表单每次打开都退化成一行空白章节，之前填的内容看起来「没保存上」。
     */
    @TableField(exist = false)
    private List<CatalogueNodeDTO> catalogueList;
}

