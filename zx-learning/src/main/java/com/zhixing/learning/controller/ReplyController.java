package com.zhixing.learning.controller;

import com.zhixing.common.annotation.RequireRole;
import com.zhixing.common.constants.UserRole;
import com.zhixing.common.domain.R;
import com.zhixing.learning.domain.po.BoardReply;
import com.zhixing.learning.service.BoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 讨论回复。
 * <p>
 * 回复是积分来源之一（+{@link com.zhixing.learning.service.PointsService#POINTS_REPLY_CREATE}），
 * 由服务端在落库后发放，幂等键为回复主键。
 */
@RestController
@RequestMapping("/replies")
@RequiredArgsConstructor
public class ReplyController {

    private final BoardService boardService;

    /** 回复话题（学员） */
    @PostMapping
    @RequireRole(UserRole.STUDENT)
    public R<Long> create(@RequestBody BoardReply reply) {
        return R.ok(boardService.createReply(reply));
    }

    /** 删除回复（作者本人或管理员） */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        boardService.deleteReply(id);
        return R.ok();
    }
}
