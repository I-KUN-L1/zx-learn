package com.zhixing.learning.controller;

import com.zhixing.common.domain.R;
import com.zhixing.learning.domain.po.Note;
import com.zhixing.learning.service.NoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 学习笔记
 */
@RestController
@RequestMapping("/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    @PostMapping
    public R<Long> add(@RequestBody Note note) {
        return R.ok(noteService.add(note));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Note note) {
        note.setId(id);
        noteService.update(note);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        noteService.delete(id);
        return R.ok();
    }

    @GetMapping("/page")
    public R<List<Note>> page() {
        return R.ok(noteService.page());
    }
}