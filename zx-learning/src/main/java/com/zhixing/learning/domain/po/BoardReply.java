package com.zhixing.learning.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 讨论回复。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("board_reply")
public class BoardReply extends BasePO {

    /** 话题 id */
    private Long boardId;

    /** 回复用户 id */
    private Long userId;

    /** 回复人昵称快照 */
    private String userName;

    /** 被回复的楼层 id（0 = 直接回复话题） */
    private Long parentId;

    /** 回复内容 */
    private String content;
}
