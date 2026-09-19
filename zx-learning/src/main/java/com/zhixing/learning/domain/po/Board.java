package com.zhixing.learning.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 课程讨论话题（学习通式课程内容页的「讨论」区）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("board")
public class Board extends BasePO {

    /** 课程 id */
    private Long courseId;

    /** 发帖用户 id */
    private Long userId;

    /** 发帖人昵称快照（避免每次列表都跨服务回表） */
    private String userName;

    /** 话题标题 */
    private String title;

    /** 话题内容 */
    private String content;

    /** 回复数 */
    private Integer replyCount;

    /** 是否置顶：0 否 / 1 是 */
    private Integer top;
}
