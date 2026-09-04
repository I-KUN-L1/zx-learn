package com.zhixing.search.controller;

import com.zhixing.common.domain.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 课程搜索 + 推荐（骨架实现，生产可替换 Elasticsearch）
 */
@Slf4j
@RestController
public class CourseSearchController {

    @GetMapping("/courses/portal")
    public R<List<Map<String, Object>>> portal(@RequestParam(required = false) String keyword) {
        return R.ok(List.of(Map.of("id", 1, "name", "Java 从入门到精通"),
                Map.of("id", 2, "name", "Spring Cloud 微服务实战")));
    }

    @GetMapping("/courses/name")
    public R<List<Long>> name(@RequestParam String keyword) {
        return R.ok(List.of(1L, 2L));
    }

    @GetMapping("/recommend/best")
    public R<List<Map<String, Object>>> best() {
        return R.ok(List.of(Map.of("id", 1, "name", "精品好课")));
    }

    @GetMapping("/recommend/new")
    public R<List<Map<String, Object>>> newest() {
        return R.ok(List.of(Map.of("id", 2, "name", "新课推荐")));
    }

    @GetMapping("/recommend/free")
    public R<List<Map<String, Object>>> free() {
        return R.ok(List.of(Map.of("id", 3, "name", "精品公开课")));
    }

    @PostMapping("/interests")
    public R<Void> saveInterest(@RequestBody Map<String, Object> interest) {
        log.info("保存用户兴趣：{}", interest);
        return R.ok();
    }

    @GetMapping("/interests")
    public R<List<Map<String, Object>>> interests() {
        return R.ok(List.of());
    }
}
