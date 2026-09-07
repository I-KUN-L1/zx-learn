package com.zhixing.media.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.ObjectMetadata;
import com.zhixing.media.config.MediaStorageProperties;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 阿里云 OSS 存储：图片上传至 OSS，浏览器经 OSS/CDN 域名访问（访问接口 302 重定向）。
 */
@Slf4j
public class OssMediaStorage implements MediaStorage {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final MediaStorageProperties properties;
    private final OSS ossClient;

    public OssMediaStorage(MediaStorageProperties properties) {
        this.properties = properties;
        this.ossClient = new OSSClientBuilder().build(
                properties.getOss().getEndpoint(),
                properties.getOss().getAccessKeyId(),
                properties.getOss().getAccessKeySecret());
        log.info("媒体存储模式：OSS（bucket={}, endpoint={}）",
                properties.getOss().getBucket(), properties.getOss().getEndpoint());
    }

    @Override
    public StoredFile store(String originalFilename, String contentType, InputStream in, long size) {
        String key = buildKey(originalFilename);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType(contentType == null ? "application/octet-stream" : contentType);
        metadata.setContentLength(size);
        // 对象级公共读：Bucket 保持私有，仅上传的图片可被浏览器直链公开访问
        metadata.setObjectAcl(CannedAccessControlList.PublicRead);
        ossClient.putObject(properties.getOss().getBucket(), key, in, metadata);
        String url = resolveUrl(key);
        log.info("文件已上传 OSS：key={}, url={}", key, url);
        return new StoredFile(key, url);
    }

    @Override
    public MediaResource load(String key) {
        // OSS 模式访问走 302 重定向，不经服务回源
        return null;
    }

    @Override
    public String resolveUrl(String key) {
        String prefix = properties.getOss().getUrlPrefix();
        if (prefix != null && !prefix.isBlank()) {
            return stripTrailingSlash(prefix) + "/" + key;
        }
        String endpoint = properties.getOss().getEndpoint();
        // 三级域名（virtual-hosted，bucket.endpoint）风格：新建 Bucket 已禁用 path-style（endpoint/bucket）访问
        String host = endpoint.startsWith("http") ? endpoint.substring(endpoint.indexOf("://") + 3) : endpoint;
        return "https://" + properties.getOss().getBucket() + "." + stripTrailingSlash(host) + "/" + key;
    }

    @Override
    public boolean redirectable() {
        return true;
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

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
