package com.zhixing.media.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 媒体存储配置
 * <p>通过环境变量注入（见 .env.example）：
 * {@code MEDIA_STORAGE_MODE}（auto/oss/local）、{@code MEDIA_LOCAL_DIR}、
 * {@code OSS_ENDPOINT}、{@code OSS_ACCESS_KEY_ID}、{@code OSS_ACCESS_KEY_SECRET}、
 * {@code OSS_BUCKET}、{@code OSS_URL_PREFIX}。</p>
 */
@Data
@ConfigurationProperties(prefix = "zx.media.storage")
public class MediaStorageProperties {

    /** 存储模式：auto（配置了 OSS 则用 OSS，否则本地磁盘兜底）/ oss / local */
    private String mode = "auto";

    /** 本地存储目录（local/auto 兜底模式使用） */
    private String localDir = "./data/media";

    private final Oss oss = new Oss();

    @Data
    public static class Oss {
        /** OSS Endpoint，如 https://oss-cn-hangzhou.aliyuncs.com */
        private String endpoint;
        private String accessKeyId;
        private String accessKeySecret;
        private String bucket;
        /** 自定义访问域名（CDN 加速域名，可选；为空则用 bucket.endpoint 默认域名） */
        private String urlPrefix;
    }

    /**
     * OSS 必要配置是否齐备（endpoint/AK/SK/bucket 均非空）。
     */
    public boolean ossConfigured() {
        return isNotBlank(oss.endpoint) && isNotBlank(oss.accessKeyId)
                && isNotBlank(oss.accessKeySecret) && isNotBlank(oss.bucket);
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }
}
