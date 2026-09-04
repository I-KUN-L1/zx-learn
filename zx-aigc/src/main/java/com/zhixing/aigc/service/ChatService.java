package com.zhixing.aigc.service;

import com.zhixing.aigc.agent.*;
import com.zhixing.aigc.config.RagProperties;
import com.zhixing.aigc.domain.ChatEventVO;
import com.zhixing.aigc.memory.ChatMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 聊天服务（SSE 增强版）：
 * <ul>
 *   <li>事件携带自增 id，客户端断线后携带 Last-Event-ID 重连，服务端从 Redis 事件缓冲回放未投递增量；</li>
 *   <li>生成期间周期性发送 :ping 注释心跳，防止代理/网关超时断开连接。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final RouteAgent routeAgent;
    private final ChatMemory chatMemory;
    private final StringRedisTemplate redisTemplate;
    private final RagProperties ragProperties;

    private static final String EVT_KEY_PREFIX = "aigc:sse:";

    /** 当前活跃的 SSE 流式对话连接数（用于并发连接限流，避免打满连接容量） */
    private final AtomicInteger activeStreams = new AtomicInteger(0);

    /**
     * 流式聊天（SSE）。lastEventId 非空表示断线重连：只回放已生成但未送达的增量，不再重复生成。
     */
    public Flux<ServerSentEvent<ChatEventVO>> streamChat(Long userId, String sessionId, String question,
                                                         Long lastEventId) {
        if (sessionId == null || question == null || question.isBlank()) {
            return Flux.just(sse(ChatEventVO.end(), 0L));
        }
        if (lastEventId != null && lastEventId >= 0) {
            // 断线重连：回放事件缓冲中 id > lastEventId 的增量，避免丢失已生成内容
            // 回放是瞬时补发，无需心跳；否则无限心跳会阻止 END 后连接完成
            return replayEvents(sessionId, lastEventId);
        }
        int limit = ragProperties.getMaxConcurrentStreams();
        return Flux.defer(() -> {
            // 订阅时才真实建立 SSE 连接，故在此处占用/检查配额，语义与连接占用对齐
            int cur = activeStreams.incrementAndGet();
            if (limit > 0 && cur > limit) {
                activeStreams.decrementAndGet();
                log.warn("SSE 并发连接数超限({}), 返回降级提示", limit);
                return busyStream();
            }
            return streamOnce(userId, sessionId, question)
                    .doFinally(sig -> activeStreams.decrementAndGet());
        });
    }

    private Flux<ServerSentEvent<ChatEventVO>> streamOnce(Long userId, String sessionId, String question) {
        flushBuffer(sessionId); // 新一轮对话：清空旧缓冲

        AgentType type = routeAgent.route(question);
        AbstractAgent agent = routeAgent.getAgent(type);
        ChatContext context = buildContext(userId, sessionId, question);
        chatMemory.saveMessage(sessionId, "user", question);

        AtomicLong seq = new AtomicLong(0);
        Flux<ServerSentEvent<ChatEventVO>> events = Flux.concat(
                Flux.just(sse(ChatEventVO.start(type.name()), seq.getAndIncrement())),
                agent.stream(context)
                        .doOnNext(delta -> buffer(sessionId, delta))
                        .map(delta -> sse(ChatEventVO.delta(delta), seq.getAndIncrement()))
                        .onErrorResume(e -> {
                            log.error("流式生成异常：{}", e.getMessage());
                            return Flux.just(sse(ChatEventVO.delta("抱歉，服务暂时不可用，请稍后再试。"), seq.getAndIncrement()));
                        }),
                Flux.defer(() -> {
                    chatMemory.saveMessage(sessionId, "assistant", summaryOf(sessionId));
                    clearBuffer(sessionId);
                    return Flux.just(sse(ChatEventVO.end(), seq.getAndIncrement()));
                })
        );
        // 心跳仅用于生成期间的保活：END 事件一旦发出即终止整个流（含心跳），
        // 否则无限心跳 Flux 会让连接在对话结束后一直滞留，占用连接与并发配额
        return events.mergeWith(heartbeat())
                .takeUntil(evt -> evt.data() != null && "END".equals(evt.data().getType()));
    }

    /** 连接超限时的降级响应：以 SSE 形式返回提示，保持客户端 SSE 协议通顺 */
    private Flux<ServerSentEvent<ChatEventVO>> busyStream() {
        return Flux.just(
                sse(ChatEventVO.delta("当前同时对话人数较多，请稍后再试。"), 0L),
                sse(ChatEventVO.end(), 1L));
    }

    /**
     * 文本对话（非流式）
     */
    public String textChat(Long userId, String sessionId, String question) {
        if (question == null || question.isBlank()) {
            return "";
        }
        AgentType type = routeAgent.route(question);
        AbstractAgent agent = routeAgent.getAgent(type);
        ChatContext context = buildContext(userId, sessionId, question);
        chatMemory.saveMessage(sessionId, "user", question);
        String answer = agent.answer(context);
        chatMemory.saveMessage(sessionId, "assistant", answer);
        return answer;
    }

    private ChatContext buildContext(Long userId, String sessionId, String question) {
        ChatContext context = new ChatContext();
        context.setUserId(userId);
        context.setSessionId(sessionId);
        context.setQuestion(question);
        context.setHistory(chatMemory.loadAsMap(sessionId));
        return context;
    }

    // ---------------- SSE 心跳 ----------------

    private Flux<ServerSentEvent<ChatEventVO>> heartbeat() {
        int seconds = ragProperties.getHeartbeatSeconds();
        if (seconds <= 0) {
            return Flux.empty();
        }
        return Flux.interval(Duration.ofSeconds(seconds))
                .map(i -> ServerSentEvent.<ChatEventVO>builder()
                        .comment("ping")
                        .build())
                .onErrorResume(e -> Flux.empty());
    }

    private ServerSentEvent<ChatEventVO> sse(ChatEventVO event, long id) {
        return ServerSentEvent.<ChatEventVO>builder()
                .id(String.valueOf(id))
                .event("message")
                .data(event)
                .build();
    }

    // ---------------- 事件缓冲（断线重连回放） ----------------

    private String evtKey(String sessionId) {
        return EVT_KEY_PREFIX + sessionId;
    }

    private void buffer(String sessionId, String delta) {
        try {
            String key = evtKey(sessionId);
            redisTemplate.opsForList().rightPush(key, delta);
            redisTemplate.opsForList().trim(key, -ragProperties.getReplayCount(), -1);
        } catch (Exception e) {
            // Redis 不可用时退化为无缓冲，不影响对话
            log.debug("事件缓冲写入失败：{}", e.getMessage());
        }
    }

    private void flushBuffer(String sessionId) {
        try {
            redisTemplate.delete(evtKey(sessionId));
        } catch (Exception ignored) {
        }
    }

    private void clearBuffer(String sessionId) {
        flushBuffer(sessionId);
    }

    private String summaryOf(String sessionId) {
        try {
            List<String> deltas = redisTemplate.opsForList().range(evtKey(sessionId), 0, -1);
            if (deltas == null || deltas.isEmpty()) {
                return "";
            }
            return String.join("", deltas);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 回放缓冲中 id > lastEventId 的增量事件（缓冲 index i 对应 id = i+1）。
     */
    private Flux<ServerSentEvent<ChatEventVO>> replayEvents(String sessionId, long lastEventId) {
        try {
            List<String> deltas = redisTemplate.opsForList().range(evtKey(sessionId), 0, -1);
            if (deltas == null || deltas.isEmpty()) {
                return Flux.empty();
            }
            AtomicLong seq = new AtomicLong(lastEventId);
            Flux<ServerSentEvent<ChatEventVO>> replay = Flux.fromIterable(deltas)
                    .skip(lastEventId) // 跳过已送达的 id：index(lastEventId-1) 及之前 → skip(lastEventId)
                    .map(d -> sse(ChatEventVO.delta(d), seq.getAndIncrement()));
            // END 事件必须惰性构造：Flux.just 会急切求值，导致 END 的 id 在订阅前就被占用、
            // 小于前面的增量 id，破坏 Last-Event-ID 语义
            return replay.concatWith(Flux.defer(
                    () -> Flux.just(sse(ChatEventVO.end(), seq.getAndIncrement()))));
        } catch (Exception e) {
            return Flux.empty();
        }
    }

    /**
     * 停止生成（预留：通过会话标记中断）
     */
    public void stop(String sessionId) {
        log.info("停止生成会话: {}", sessionId);
    }

    public Map<String, String> templates() {
        return Map.of(
                "course", "推荐一门适合零基础学习 Java 的课程",
                "consult", "如何查看我的学习进度？",
                "buy", "帮我介绍一下课程的购买流程",
                "knowledge", "什么是微服务架构？"
        );
    }
}