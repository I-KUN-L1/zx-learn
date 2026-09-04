package com.zhixing.media.controller;

import com.zhixing.common.domain.R;
import com.zhixing.common.exceptions.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 媒资管理（骨架实现：文件信息存储，视频上传/播放签名占位）
 */
@Slf4j
@RestController
@RequestMapping("/medias")
public class MediaController {

    private final Map<Long, Map<String, Object>> store = new ConcurrentHashMap<>();
    private final AtomicLong idGen = new AtomicLong(1);

    @GetMapping
    public R<Object> page() {
        return R.ok(store.values());
    }

    @PostMapping
    public R<Long> save(@RequestBody Map<String, Object> media) {
        if (media == null || media.get("name") == null) {
            throw new BadRequestException("媒资名称不能为空");
        }
        Long id = idGen.getAndIncrement();
        media.put("id", id);
        store.put(id, media);
        log.info("保存媒资：id={}, name={}", id, media.get("name"));
        return R.ok(id);
    }

    @GetMapping("/signature/upload")
    public R<Map<String, String>> uploadSignature() {
        return R.ok(Map.of("signature", UUID.randomUUID().toString()));
    }

    @GetMapping("/signature/play")
    public R<Map<String, String>> playSignature(@RequestParam Long mediaId) {
        return R.ok(Map.of("signature", UUID.randomUUID().toString(), "mediaId", String.valueOf(mediaId)));
    }

    @GetMapping("/signature/preview")
    public R<Map<String, String>> previewSignature(@RequestParam Long mediaId) {
        return R.ok(Map.of("signature", UUID.randomUUID().toString(), "mediaId", String.valueOf(mediaId)));
    }

    @DeleteMapping("/{mediaId}")
    public R<Void> delete(@PathVariable Long mediaId) {
        if (store.remove(mediaId) == null) {
            throw new BadRequestException("媒资不存在");
        }
        log.info("删除媒资：id={}", mediaId);
        return R.ok();
    }

    @DeleteMapping
    public R<Void> deleteBatch(@RequestBody Long[] mediaIds) {
        for (Long id : mediaIds) {
            store.remove(id);
        }
        log.info("批量删除媒资：count={}", mediaIds == null ? 0 : mediaIds.length);
        return R.ok();
    }
}
