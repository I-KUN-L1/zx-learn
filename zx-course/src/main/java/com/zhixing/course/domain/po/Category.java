package com.zhixing.course.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 课程分类（三级）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("category")
public class Category extends BasePO {

    private String name;
    private Long parentId;
    /** 层级：1/2/3 */
    private Integer level;
    /** 状态：1-启用 0-停用 */
    private Integer status;
    private Integer sort;
}
