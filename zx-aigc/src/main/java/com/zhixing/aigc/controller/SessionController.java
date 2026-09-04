package com.zhixing.aigc.controller;

import com.zhixing.aigc.domain.ChatSession;
import com.zhixing.aigc.memory.RedisMessage;
import com.zhixing.aigc.service.SessionService;
import com.zhixing.common.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 会话接口
 */
@RestController
@RequestMapping("/session")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @PostMapping
    public R<ChatSession> create(@RequestHeader(value = "user-info", required = false) Long userId) {
        return R.ok(sessionService.createSession(userId == null ? 0L : userId));
    }

    @GetMapping("/hot")
    public R<List<String>> hot() {
        return R.ok(sessionService.hotQuestions());
    }

    @GetMapping("/history")
    public R<List<ChatSession>> history(@RequestHeader(value = "user-info", required = false) Long userId) {
        return R.ok(sessionService.history(userId == null ? 0L : userId));
    }

    @GetMapping("/{sessionId}")
    public R<List<RedisMessage>> detail(@PathVariable String sessionId) {
        return R.ok(sessionService.detail(sessionId));
    }

    @DeleteMapping("/history")
    public R<Void> delete(@RequestBody Map<String, String> body,
                          @RequestHeader(value = "user-info", required = false) Long userId) {
        sessionService.delete(body.get("sessionId"), userId == null ? 0L : userId);
        return R.ok();
    }

    @PutMapping("/history")
    public R<Void> updateTitle(@RequestBody Map<String, String> body) {
        sessionService.updateTitle(body.get("sessionId"), body.get("title"));
        return R.ok();
    }
}
