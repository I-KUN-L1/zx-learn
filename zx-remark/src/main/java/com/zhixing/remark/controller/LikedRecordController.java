package com.zhixing.remark.controller;

import com.zhixing.common.domain.R;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 点赞服务
 */
@Slf4j
@RestController
@RequestMapping("/likes")
public class LikedRecordController {

    private final Map<Long, Set<Long>> likeStore = new ConcurrentHashMap<>();

    @PostMapping
    public R<Boolean> like(@RequestBody Map<String, Long> body) {
        Long bizId = body.get("bizId");
        if (bizId == null) {
            throw new BadRequestException("bizId 不能为空");
        }
        Long userId = UserContext.getUserId();
        Set<Long> users = likeStore.computeIfAbsent(bizId, k -> ConcurrentHashMap.newKeySet());
        boolean liked;
        if (users.contains(userId)) {
            users.remove(userId);
            liked = false;
        } else {
            users.add(userId);
            liked = true;
        }
        log.info("用户 {} 点赞/取消 {}：liked={}", userId, bizId, liked);
        return R.ok(liked);
    }

    @GetMapping("/list")
    public R<Map<Long, Boolean>> status(@RequestParam("bizIds") List<Long> bizIds) {
        Long userId = UserContext.getUserId();
        Map<Long, Boolean> result = bizIds.stream().collect(Collectors.toMap(
                id -> id,
                id -> likeStore.getOrDefault(id, Set.of()).contains(userId)));
        return R.ok(result);
    }
}
