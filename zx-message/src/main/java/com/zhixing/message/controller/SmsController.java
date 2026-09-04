package com.zhixing.message.controller;

import com.zhixing.common.domain.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 短信与收件箱
 */
@Slf4j
@RestController
public class SmsController {

    private final Map<Long, Map<String, Object>> inboxes = new ConcurrentHashMap<>();
    private final AtomicLong idGen = new AtomicLong(1);

    @PostMapping("/sms/message")
    @Async
    public R<Void> sendSms(@RequestBody Map<String, Object> message) {
        log.info("发送短信：{}", message);
        return R.ok();
    }

    @PostMapping("/inboxes")
    public R<Long> sendInbox(@RequestBody Map<String, Object> message) {
        Long id = idGen.getAndIncrement();
        message.put("id", id);
        inboxes.put(id, message);
        log.info("发送站内信：id={}", id);
        return R.ok(id);
    }

    @GetMapping("/inboxes")
    public R<List<Map<String, Object>>> inboxes() {
        return R.ok(List.copyOf(inboxes.values()));
    }
}
