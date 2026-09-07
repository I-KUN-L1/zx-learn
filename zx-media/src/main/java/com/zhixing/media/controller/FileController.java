package com.zhixing.media.controller;

import com.zhixing.common.annotation.RequireRole;
import com.zhixing.common.constants.UserRole;
import com.zhixing.common.domain.R;
import com.zhixing.common.exceptions.DbException;
import com.zhixing.media.storage.MediaStorage;
import com.zhixing.media.storage.MediaStorage.StoredFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;

/**
 * 文件管理：图片上传与公开访问。
 * <p>
 * 契约：
 * <ul>
 *   <li>POST /files —— 图片上传（员工/教师），返回 {@code {key, name, url}}，
 *       url 为浏览器可访问地址（OSS 模式为 OSS/CDN 域名，本地模式为 /api/files/view/{key}）</li>
 *   <li>GET /files/view/{key} —— 图片公开访问（网关白名单）：
 *       OSS 模式 302 重定向到对象存储地址，本地模式由媒体服务回源输出</li>
 * </ul>
 * 约束：仅支持图片格式（jpg/jpeg/png/webp/gif），单文件不超过 10MB。
 */
@Slf4j
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    /** 允许上传的图片扩展名（小写，含点） */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".webp", ".gif");

    /** 单文件大小上限：10MB */
    private static final long MAX_SIZE = 10L * 1024 * 1024;

    private final MediaStorage mediaStorage;

    @PostMapping
    @RequireRole({UserRole.STAFF, UserRole.TEACHER})
    public R<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return R.error(400, "请选择要上传的文件");
        }
        String original = file.getOriginalFilename();
        String ext = extension(original);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            return R.error(400, "仅支持图片格式：jpg/jpeg/png/webp/gif");
        }
        if (file.getSize() > MAX_SIZE) {
            return R.error(400, "图片大小不能超过 10MB");
        }
        try (InputStream in = file.getInputStream()) {
            StoredFile stored = mediaStorage.store(original, file.getContentType(), in, file.getSize());
            return R.ok(Map.of("key", stored.key(), "name", original == null ? "" : original, "url", stored.url()));
        } catch (Exception e) {
            log.error("文件上传失败：name={}", original, e);
            throw new DbException("文件上传失败，请稍后重试");
        }
    }

    /**
     * 图片公开访问：OSS 模式 302 重定向，本地模式回源输出文件流。
     * PathPattern 的 {*} 捕获包含斜杠的完整 key（如 files/202609/xxx.jpg）。
     */
    @GetMapping("/view/{*key}")
    public void view(@PathVariable String key, HttpServletResponse response) throws Exception {
        // {*} 捕获的 key 带前导 "/"
        String realKey = key.startsWith("/") ? key.substring(1) : key;
        if (mediaStorage.redirectable()) {
            response.sendRedirect(mediaStorage.resolveUrl(realKey));
            return;
        }
        MediaStorage.MediaResource resource = mediaStorage.load(realKey);
        if (resource == null) {
            response.sendError(404);
            return;
        }
        response.setContentType(resource.contentType());
        response.setContentLengthLong(resource.size());
        try (InputStream in = resource.stream()) {
            in.transferTo(response.getOutputStream());
        }
    }

    private static String extension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot).toLowerCase();
    }
}
