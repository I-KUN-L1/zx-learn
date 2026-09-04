package com.zhixing.media.controller;

import com.zhixing.common.domain.R;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件管理
 */
@RestController
@RequestMapping("/files")
public class FileController {

    private final Map<Long, String> store = new ConcurrentHashMap<>();

    @PostMapping
    public R<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        long id = System.currentTimeMillis();
        String name = file == null ? "unknown" : file.getOriginalFilename();
        store.put(id, name);
        return R.ok(Map.of("id", id, "name", name, "url", "/files/" + id));
    }

    @GetMapping("/{id}")
    public R<Map<String, String>> get(@PathVariable Long id) {
        return R.ok(Map.of("id", String.valueOf(id), "name", store.getOrDefault(id, "")));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        store.remove(id);
        return R.ok();
    }
}
