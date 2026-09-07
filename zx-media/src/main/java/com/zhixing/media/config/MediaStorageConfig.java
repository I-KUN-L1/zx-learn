package com.zhixing.media.config;

import com.zhixing.media.storage.LocalStorageMediaStorage;
import com.zhixing.media.storage.MediaStorage;
import com.zhixing.media.storage.OssMediaStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 媒体存储装配：按 {@code zx.media.storage.mode} 选择 OSS 或本地磁盘实现。
 * <ul>
 *   <li>auto（默认）：OSS 配置齐备（endpoint/AK/SK/bucket）则用 OSS，否则本地磁盘兜底</li>
 *   <li>oss：强制 OSS，配置缺失启动失败（fail-fast）</li>
 *   <li>local：强制本地磁盘</li>
 * </ul>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(MediaStorageProperties.class)
public class MediaStorageConfig {

    @Bean
    public MediaStorage mediaStorage(MediaStorageProperties properties) {
        String mode = properties.getMode() == null ? "auto" : properties.getMode().toLowerCase();
        boolean ossReady = properties.ossConfigured();
        return switch (mode) {
            case "oss" -> {
                if (!ossReady) {
                    throw new IllegalStateException(
                            "媒体存储模式为 oss，但 OSS_ENDPOINT/OSS_ACCESS_KEY_ID/OSS_ACCESS_KEY_SECRET/OSS_BUCKET 未配置完整");
                }
                yield new OssMediaStorage(properties);
            }
            case "local" -> new LocalStorageMediaStorage(properties);
            default -> ossReady
                    ? new OssMediaStorage(properties)
                    : new LocalStorageMediaStorage(properties);
        };
    }
}
