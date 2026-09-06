package com.zhixing.exam.controller;

import com.zhixing.common.annotation.RequireRole;
import com.zhixing.common.constants.UserRole;
import com.zhixing.common.domain.R;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.exam.domain.po.Question;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 题目管理（骨架实现：内存存储）
 */
@Slf4j
@RestController
@RequestMapping("/questions")
public class QuestionController {

    private final Map<Long, Question> store = new ConcurrentHashMap<>();
    private final AtomicLong idGen = new AtomicLong(1);

    @PostMapping
    @RequireRole({UserRole.STAFF, UserRole.TEACHER})
    public R<Long> add(@RequestBody Question question) {
        if (question == null || question.getScore() == null) {
            throw new BadRequestException("题目内容不能为空");
        }
        question.setId(idGen.getAndIncrement());
        store.put(question.getId(), question);
        log.info("新增题目：id={}, score={}", question.getId(), question.getScore());
        return R.ok(question.getId());
    }

    @PutMapping("/{id}")
    @RequireRole({UserRole.STAFF, UserRole.TEACHER})
    public R<Void> update(@PathVariable Long id, @RequestBody Question question) {
        if (!store.containsKey(id)) {
            throw new BadRequestException("题目不存在");
        }
        question.setId(id);
        store.put(id, question);
        log.info("更新题目：id={}", id);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @RequireRole({UserRole.STAFF, UserRole.TEACHER})
    public R<Void> delete(@PathVariable Long id) {
        Question removed = store.remove(id);
        if (removed == null) {
            throw new BadRequestException("题目不存在");
        }
        log.info("删除题目：id={}", id);
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<Question> getById(@PathVariable Long id) {
        Question question = store.get(id);
        if (question == null) {
            throw new BadRequestException("题目不存在");
        }
        return R.ok(question);
    }

    @GetMapping("/list")
    public R<List<Question>> list(@RequestParam("ids") List<Long> ids) {
        return R.ok(ids.stream().map(store::get).filter(q -> q != null).collect(Collectors.toList()));
    }

    @GetMapping("/scores")
    public R<Map<Long, Integer>> scores(@RequestParam("ids") List<Long> ids) {
        return R.ok(ids.stream().filter(store::containsKey)
                .collect(Collectors.toMap(id -> id, id -> store.get(id).getScore())));
    }

    @GetMapping("/numOfTeacher")
    public R<Integer> numOfTeacher(@RequestParam Long teacherId) {
        return R.ok(store.size());
    }
}
