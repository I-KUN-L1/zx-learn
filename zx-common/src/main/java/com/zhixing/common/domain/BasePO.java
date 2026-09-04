package com.zhixing.common.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体公共字段基类：id（雪花）、创建/更新时间、操作人、逻辑删除
 */
@Data
public class BasePO implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Long creater;

    private Long updater;

    @TableLogic
    @TableField(select = false)
    private Integer deleted;
}
