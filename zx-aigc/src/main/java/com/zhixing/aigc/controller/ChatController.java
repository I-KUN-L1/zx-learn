package com.zhixing.aigc.controller;

import com.zhixing.aigc.domain.ChatEventVO;
import com.zhixing.aigc.service.ChatService;
import com.zhixing.common.annotation.NoWrapper;
import com.zhixing.common.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 聊天接口（SSE）
 */
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 流式聊天（SSE）：
     * - 通过 SSE 事件 id 支持断线重连，客户端可携带 Last-Event-ID 增量续传；
     * - 服务端周期性发送 :ping 注释心跳保活。
     */
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @NoWrapper
    public Flux<ServerSentEvent<ChatEventVO>> chat(@RequestBody Map<String, String> body,
                                                   @RequestHeader(value = "Last-Event-ID", required = false) Long lastEventId,
                                                   @RequestHeader(value = "user-info", required = false) Long userId) {
        String sessionId = body.get("sessionId");
        String question = body.get("question");
        return chatService.streamChat(userId, sessionId, question, lastEventId);
    }

    @PostMapping("/stop")
    public R<Void> stop(@RequestBody Map<String, String> body) {
        chatService.stop(body.get("sessionId"));
        return R.ok();
    }

    @PostMapping("/text")
    public R<String> text(@RequestBody Map<String, String> body,
                          @RequestHeader(value = "user-info", required = false) Long userId) {
        return R.ok(chatService.textChat(userId, body.get("sessionId"), body.get("question")));
    }

    @GetMapping("/templates")
    public R<Map<String, String>> templates() {
        return R.ok(chatService.templates());
    }
}