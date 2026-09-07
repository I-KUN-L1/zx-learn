package com.zhixing.media.storage;

import com.zhixing.media.config.MediaStorageProperties;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 本地磁盘存储兜底：未配置 OSS 时的实现，文件落盘 {@code local-dir}，访问经媒体服务回源输出。
 * <p>URL 采用 {@code /api/files/view/{key}}（与前端统一 API 前缀一致，
 * 开发环境经 Vite 代理、生产经网关转发到媒体服务）。</p>
 */
@Slf4j
public class LocalStorageMediaStorage implements MediaStorage {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final Path baseDir;

    public LocalStorageMediaStorage(MediaStorageProperties properties) {
        this.baseDir = Paths.get(properties.getLocalDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new IllegalStateException("本地存储目录创建失败：" + baseDir, e);
        }
        log.info("媒体存储模式：本地磁盘（dir={}）", baseDir);
    }

    @Override
    public StoredFile store(String originalFilename, String contentType, InputStream in, long size) {
        String key = buildKey(originalFilename);
        Path target = baseDir.resolve(key).normalize();
        if (!target.startsWith(baseDir)) {
            throw new IllegalArgumentException("非法存储路径");
        }
        try {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("文件写入本地存储失败：" + target, e);
        }
        String url = resolveUrl(key);
        log.info("文件已存储本地：key={}, url={}", key, url);
        return new StoredFile(key, url);
    }

    @Override
    public MediaResource load(String key) {
        Path target = baseDir.resolve(key).normalize();
        if (!target.startsWith(baseDir) || !Files.isRegularFile(target)) {
            return null;
        }
        try {
            String contentType = Files.probeContentType(target);
            if (contentType == null) {
                contentType = URLConnection.guessContentTypeFromName(target.getFileName().toString());
            }
            return new MediaResource(key,
                    contentType == null ? "application/octet-stream" : contentType,
                    Files.size(target), Files.newInputStream(target));
        } catch (IOException e) {
            log.warn("读取本地文件失败：key={}", key, e);
            return null;
        }
    }

    @Override
    public String resolveUrl(String key) {
        return "/api/files/view/" + key;
    }

    @Override
    public boolean redirectable() {
        return false;
    }

    /** key 规则：files/{yyyyMM}/{uuid}.{ext}，按月分目录便于管理 */
    private String buildKey(String originalFilename) {
        String ext = "";
        if (originalFilename != null) {
            int dot = originalFilename.lastIndexOf('.');
            if (dot >= 0 && dot < originalFilename.length() - 1) {
                ext = originalFilename.substring(dot).toLowerCase();
            }
        }
        return "files/" + LocalDate.now().format(MONTH) + "/"
                + UUID.randomUUID().toString().replace("-", "") + ext;
    }
}
