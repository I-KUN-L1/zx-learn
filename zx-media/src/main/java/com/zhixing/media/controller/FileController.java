package com.zhixing.media.controller;

import com.zhixing.common.annotation.RequireRole;
import com.zhixing.common.constants.UserRole;
import com.zhixing.common.domain.R;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件管理
 * <p>
 * 权限：上传/删除要求员工(1)或教师(3)；查询仅要求登录。
 */
@RestController
@RequestMapping("/files")
public class FileController {

    private final Map<Long, String> store = new ConcurrentHashMap<>();

    @PostMapping
    @RequireRole({UserRole.STAFF, UserRole.TEACHER})
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
    @RequireRole({UserRole.STAFF, UserRole.TEACHER})
    public R<Void> delete(@PathVariable Long id) {
        store.remove(id);
        return R.ok();
    }
}
