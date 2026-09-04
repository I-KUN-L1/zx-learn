package com.zhixing.data.controller;

import com.zhixing.common.domain.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 数据看板（数据存 Redis）
 */
@Slf4j
@RestController
@RequestMapping("/data")
public class BoardController {

    private final StringRedisTemplate redisTemplate;

    public BoardController(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/board")
    public R<Map<Object, Object>> board() {
        Map<Object, Object> data = redisTemplate.opsForHash().entries("data:board");
        return R.ok(data.isEmpty() ? defaultBoard() : data);
    }

    @PutMapping("/board/set")
    public R<Void> setBoard(@RequestBody Map<String, String> data) {
        redisTemplate.opsForHash().putAll("data:board", data);
        log.info("更新看板数据：keys={}", data.keySet());
        return R.ok();
    }

    @GetMapping("/today")
    public R<Map<Object, Object>> today() {
        return R.ok(redisTemplate.opsForHash().entries("data:today"));
    }

    @PutMapping("/today/set")
    public R<Void> setToday(@RequestBody Map<String, String> data) {
        redisTemplate.opsForHash().putAll("data:today", data);
        log.info("更新今日数据：keys={}", data.keySet());
        return R.ok();
    }

    @GetMapping("/top10")
    public R<Map<Object, Object>> top10() {
        return R.ok(redisTemplate.opsForHash().entries("data:top10"));
    }

    @PutMapping("/top10/set")
    public R<Void> setTop10(@RequestBody Map<String, String> data) {
        redisTemplate.opsForHash().putAll("data:top10", data);
        log.info("更新 Top10 排行：keys={}", data.keySet());
        return R.ok();
    }

    private Map<Object, Object> defaultBoard() {
        Map<Object, Object> map = new HashMap<>();
        map.put("userCount", "1000");
        map.put("courseCount", "120");
        map.put("orderCount", "500");
        map.put("revenue", "100000");
        return map;
    }
}
