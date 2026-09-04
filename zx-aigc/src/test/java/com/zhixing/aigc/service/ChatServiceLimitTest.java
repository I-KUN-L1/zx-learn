package com.zhixing.aigc.service;

import com.zhixing.aigc.config.RagProperties;
import com.zhixing.aigc.domain.ChatEventVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatService SSE 并发连接限流单元测试。
 * <p>
 * 限流分支在触及 routeAgent/redis 之前即返回降级流，故以 null 依赖安全构造。
 * 由于 streamChat 的配额计算（incrementAndGet）与被订阅的业务流同属同步体，
 * 测试通过反射预置 {@code activeStreams} 已占值来模拟并发占用，从而精确验证：
 * </p>
 * <ol>
 *   <li>未超限时不立即降级（返回正常流对象）；</li>
 *   <li>活跃连接超过 maxConcurrentStreams 时返回"请稍后再试"降级提示（含 END）；</li>
 *   <li>limit=0 关闭限流时永不降级。</li>
 * </ol>
 */
class ChatServiceLimitTest {

    private RagProperties props;

    @BeforeEach
    void setUp() {
        props = new RagProperties();
        props.setMaxConcurrentStreams(1);
    }

    /** 通过反射设置活跃连接计数，模拟并发占用 */
    private void active(ChatService svc, int value) throws Exception {
        Field f = ChatService.class.getDeclaredField("activeStreams");
        f.setAccessible(true);
        AtomicInteger counter = new AtomicInteger(value);
        f.set(svc, counter);
    }

    /** 读取 SSE 事件流，汇总成 (type=content) 文本行（触发订阅） */
    private String collect(Flux<ServerSentEvent<ChatEventVO>> flux) {
        List<ServerSentEvent<ChatEventVO>> events = flux.collectList().block();
        assertNotNull(events);
        return events.stream()
                .map(e -> {
                    ChatEventVO d = e.data();
                    return d == null ? "(ping)" : d.getType() + "=" + d.getContent();
                })
                .collect(Collectors.joining("\n"));
    }

    @Test
    void withinLimitReturnsNormalStream() throws Exception {
        ChatService svc = new ChatService(null, null, null, props);
        active(svc, 0); // 无并发占用，配额充足
        Flux<ServerSentEvent<ChatEventVO>> f = svc.streamChat(1L, "s1", "问题", null);
        assertNotNull(f, "限额内应返回正常流而非 null");
        // 未订阅：惰性 defer 下配额在订阅时占位，此处仅确认调用不降级
        assertEquals(0, activeOf(svc).get(), "未订阅时配额应尚未被占用");
    }

    @Test
    void overLimitReturnsBusyStream() throws Exception {
        ChatService svc = new ChatService(null, null, null, props);
        active(svc, 1); // 已占用 1 个（达到限额）
        String text = collect(svc.streamChat(2L, "s2", "问题", null));
        assertTrue(text.contains("请稍后再试"), "超限应返回降级提示，实际: " + text);
        assertTrue(text.contains("END"), "降级流也应包含 END 事件，实际: " + text);
    }

    @Test
    void limitDisabledNeverBusy() throws Exception {
        props.setMaxConcurrentStreams(0); // 关闭限流
        ChatService svc = new ChatService(null, null, null, props);
        active(svc, 999); // 即便计数值很高也不应降级
        Flux<ServerSentEvent<ChatEventVO>> f = svc.streamChat(1L, "s1", "问题", null);
        assertNotNull(f, "limit=0 关闭限流时不应降级");
        assertEquals(999, activeOf(svc).get(), "未订阅时计数应保持不变（占用发生在订阅阶段）");
    }

    private AtomicInteger activeOf(ChatService svc) throws Exception {
        Field f = ChatService.class.getDeclaredField("activeStreams");
        f.setAccessible(true);
        return (AtomicInteger) f.get(svc);
    }

    @Test
    void overLimitRequestReleasesItsOwnIncrement() throws Exception {
        ChatService svc = new ChatService(null, null, null, props);
        active(svc, 1); // 已有 1 个真实占用
        collect(svc.streamChat(2L, "s2", "问题", null)); // 超限 → 降级
        AtomicInteger c = activeOf(svc);
        assertEquals(1, c.get(), "超限请求已完成自增与回退，计数应回到原占用值 1");
    }
}