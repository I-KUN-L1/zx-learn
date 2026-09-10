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

    /**
     * 删除会话记录。
     * <p>
     * 兼容两种调用方式以杜绝 Content-Type 报错：
     * ① 规范 JSON body {@code {"sessionId":"xx"}}；② 历史遗留的 query 参数 {@code ?sessionId=xx}。
     * </p>
     */
    @DeleteMapping("/history")
    public R<Void> delete(@RequestBody(required = false) Map<String, String> body,
                          @RequestParam(required = false) String sessionId,
                          @RequestHeader(value = "user-info", required = false) Long userId) {
        String id = sessionId != null ? sessionId : (body != null ? body.get("sessionId") : null);
        sessionService.delete(id, userId == null ? 0L : userId);
        return R.ok();
    }

    @PutMapping("/history")
    public R<Void> updateTitle(@RequestBody Map<String, String> body) {
        sessionService.updateTitle(body.get("sessionId"), body.get("title"));
        return R.ok();
    }
}
