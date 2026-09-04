package com.zhixing.learning.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 学习笔记
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("note")
public class Note extends BasePO {

    /** 用户 id */
    private Long userId;

    /** 课程 id */
    private Long courseId;

    /** 课时 id */
    private Long lessonId;

    /** 笔记内容 */
    private String content;

    /** 是否私密：0-公开 1-私密 */
    private Integer privacy;
}