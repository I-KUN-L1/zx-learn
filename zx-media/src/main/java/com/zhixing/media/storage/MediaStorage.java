package com.zhixing.media.storage;

import java.io.InputStream;

/**
 * 媒体存储抽象：屏蔽本地磁盘与阿里云 OSS 的差异。
 * <ul>
 *   <li>{@link OssMediaStorage}：图片存 OSS，访问走 OSS/CDN 域名（302 重定向）</li>
 *   <li>{@link LocalStorageMediaStorage}：本地磁盘兜底，访问经媒体服务回源输出</li>
 * </ul>
 */
public interface MediaStorage {

    /**
     * 存储文件。
     *
     * @param originalFilename 原始文件名（用于扩展名推断存储 key）
     * @param contentType      文件 MIME 类型
     * @param in               文件内容流
     * @param size             文件大小（字节）
     * @return 存储 key 与浏览器可访问的 URL
     */
    StoredFile store(String originalFilename, String contentType, InputStream in, long size);

    /**
     * 读取文件内容（仅本地存储模式支持回源；OSS 模式返回 null，访问走重定向）。
     *
     * @param key 存储 key
     * @return 文件资源；不存在返回 null
     */
    MediaResource load(String key);

    /**
     * 由 key 还原浏览器可访问的 URL。
     */
    String resolveUrl(String key);

    /**
     * 访问是否通过 302 重定向到对象存储地址（OSS 模式为 true）。
     */
    boolean redirectable();

    /** 存储结果：key 用于后续读取/删除，url 供浏览器直接访问 */
    record StoredFile(String key, String url) {
    }

    /** 文件资源：带类型与长度，流由调用方关闭 */
    record MediaResource(String key, String contentType, long size, InputStream stream) {
    }
}
