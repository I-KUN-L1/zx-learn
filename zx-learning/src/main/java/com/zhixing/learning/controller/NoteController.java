package com.zhixing.learning.controller;

import com.zhixing.common.annotation.RequireRole;
import com.zhixing.common.constants.UserRole;
import com.zhixing.common.domain.PageDTO;
import com.zhixing.common.domain.PageQuery;
import com.zhixing.common.domain.R;
import com.zhixing.learning.domain.po.Note;
import com.zhixing.learning.service.NoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 学习笔记。
 * 权限：笔记为学员端行为，仅学员(2)可操作，管理员/教师一律 403。
 */
@RestController
@RequestMapping("/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    @PostMapping
    @RequireRole(UserRole.STUDENT)
    public R<Long> add(@RequestBody Note note) {
        return R.ok(noteService.add(note));
    }

    @PutMapping("/{id}")
    @RequireRole(UserRole.STUDENT)
    public R<Void> update(@PathVariable Long id, @RequestBody Note note) {
        note.setId(id);
        noteService.update(note);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequireRole(UserRole.STUDENT)
    public R<Void> delete(@PathVariable Long id) {
        noteService.delete(id);
        return R.ok();
    }

    @GetMapping("/page")
    @RequireRole(UserRole.STUDENT)
    public R<PageDTO<Map<String, Object>>> page(PageQuery query,
                                                @RequestParam(required = false) Long courseId,
                                                @RequestParam(required = false) Long lessonId) {
        return R.ok(noteService.page(query, courseId, lessonId));
    }
}
