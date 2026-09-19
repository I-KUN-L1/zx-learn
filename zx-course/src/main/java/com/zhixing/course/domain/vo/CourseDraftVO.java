package com.zhixing.course.domain.vo;

import com.zhixing.course.domain.po.CourseDraft;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 课程草稿视图（草稿箱列表用）。
 * <p>
 * 刻意**不包含** catalogueJson：草稿目录是编辑态细节，列表页用不到，
 * 却可能有几 KB，放进分页列表会白白放大响应体。
 */
@Data
public class CourseDraftVO {

    private Long id;
    /** 关联的正式课程 id（从未上架过则为 null） */
    private Long courseId;
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
    /** 编辑步骤：1-基础信息 2-目录 3-视频 4-题目 */
    private Integer step;
    /** 是否已提交上架：0-草稿箱 1-已发布 */
    private Integer submitted;
    private LocalDateTime updateTime;

    public static CourseDraftVO of(CourseDraft draft) {
        CourseDraftVO vo = new CourseDraftVO();
        vo.setId(draft.getId());
        vo.setCourseId(draft.getCourseId());
        vo.setName(draft.getName());
        vo.setCoverUrl(draft.getCoverUrl());
        vo.setPrice(draft.getPrice());
        vo.setCategoryIdLv1(draft.getCategoryIdLv1());
        vo.setCategoryIdLv2(draft.getCategoryIdLv2());
        vo.setCategoryIdLv3(draft.getCategoryIdLv3());
        vo.setTeacherId(draft.getTeacherId());
        vo.setFree(draft.getFree());
        vo.setDescription(draft.getDescription());
        vo.setStep(draft.getStep());
        vo.setSubmitted(draft.getSubmitted());
        vo.setUpdateTime(draft.getUpdateTime());
        return vo;
    }
}
