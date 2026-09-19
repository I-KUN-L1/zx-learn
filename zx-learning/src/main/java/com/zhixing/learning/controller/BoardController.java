package com.zhixing.learning.controller;

import com.zhixing.common.annotation.RequireRole;
import com.zhixing.common.constants.UserRole;
import com.zhixing.common.domain.PageDTO;
import com.zhixing.common.domain.PageQuery;
import com.zhixing.common.domain.R;
import com.zhixing.learning.domain.po.Board;
import com.zhixing.learning.service.BoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 课程讨论区（学习通式课程内容页「讨论」Tab）。
 * <p>
 * 权限：浏览对已登录用户开放（教师/管理员可查看讨论氛围），
 * 发帖/删帖为学员端行为（删帖作者本人或管理员可操作，服务层二次校验归属）。
 */
@RestController
@RequestMapping("/boards")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    /** 课程讨论区话题分页 */
    @GetMapping("/page")
    public R<PageDTO<Map<String, Object>>> page(PageQuery query,
                                                @RequestParam(value = "courseId", required = false) Long courseId) {
        return R.ok(boardService.page(courseId, query));
    }

    /** 话题详情（含全部回复） */
    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(boardService.detail(id));
    }

    /** 发布话题（学员） */
    @PostMapping
    @RequireRole(UserRole.STUDENT)
    public R<Long> create(@RequestBody Board board) {
        return R.ok(boardService.createTopic(board));
    }

    /** 删除话题（作者本人或管理员） */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        boardService.deleteTopic(id);
        return R.ok();
    }
}
