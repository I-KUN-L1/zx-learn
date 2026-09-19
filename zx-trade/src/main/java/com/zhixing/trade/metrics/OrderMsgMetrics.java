package com.zhixing.trade.metrics;

import com.zhixing.trade.service.OrderService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 本地消息表死信的可观测性出口。
 * <p>
 * 背景：{@code order_msg} 超重试上限后转死信（status=3）意味着**业务事件永久未送达**
 * （如"已付款但没开课"）。{@code DeadMsgReplayJob} 每 30 分钟重放一次并 {@code log.error}，
 * 但日志是"人去找"，不是"事来找人"。本类把它变成：
 * <ol>
 *   <li><b>指标</b>：{@code zx.trade.order.msg.dead} 暴露在 actuator，可被 Prometheus 抓取，
 *       告警规则直接写 {@code zx_trade_order_msg_dead > 0}；</li>
 *   <li><b>告警钩子</b>：配置 {@code tx.order.dead-alert-webhook} 后，死信非空即 POST 一条 JSON
 *       到该地址（企业微信/钉钉/自建告警网关均可），带冷却时间避免刷屏。</li>
 * </ol>
 * 指标值由定时任务刷新（{@link #refresh()}），Gauge 只读内存计数，
 * **抓取指标时不会打数据库**（避免监控采集反过来给 DB 加压）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMsgMetrics {

    /** 指标名：死信条数 */
    public static final String GAUGE_NAME = "zx.trade.order.msg.dead";

    private final OrderService orderService;
    private final MeterRegistry meterRegistry;

    /** 死信总数缓存（由 refresh() 更新，Gauge 只读它） */
    private final AtomicInteger deadCount = new AtomicInteger(0);

    /** 告警地址（为空则只记指标与日志，不外发） */
    @Value("${tx.order.dead-alert-webhook:}")
    private String alertWebhook;

    /** 告警冷却时间（秒），避免死信持续存在时每轮都外发 */
    @Value("${tx.order.dead-alert-cooldown-seconds:1800}")
    private long alertCooldownSeconds;

    private volatile Instant lastAlertAt = Instant.EPOCH;

    private HttpClient httpClient;

    @PostConstruct
    void register() {
        Gauge.builder(GAUGE_NAME, deadCount, AtomicInteger::doubleValue)
                .description("本地消息表死信条数（>0 = 存在业务事件永久未送达，需人工介入）")
                .register(meterRegistry);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    /**
     * 刷新死信计数，并在非空时触发告警（由死信重放任务每轮调用）。
     *
     * @param byTag 按消息 tag 的死信分布（直接来自本轮查询，避免重复查库）
     */
    public void refresh(Map<String, Integer> byTag) {
        int total = byTag == null ? 0 : byTag.values().stream().mapToInt(Integer::intValue).sum();
        deadCount.set(total);
        if (total > 0) {
            alert(total, byTag);
        }
    }

    /** 当前死信数（供健康检查/测试读取） */
    public int currentDeadCount() {
        return deadCount.get();
    }

    private void alert(int total, Map<String, Integer> byTag) {
        Instant now = Instant.now();
        if (now.isBefore(lastAlertAt.plusSeconds(alertCooldownSeconds))) {
            return;
        }
        lastAlertAt = now;
        String body = String.format(
                "{\"alert\":\"order_msg_dead\",\"level\":\"error\",\"count\":%d,\"byTag\":%s,\"at\":\"%s\"}",
                total, toJson(byTag), now);
        log.error("[告警] order_msg 死信 {} 条，按 tag 分布：{}", total, byTag);
        if (alertWebhook == null || alertWebhook.isBlank()) {
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(alertWebhook))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .exceptionally(e -> {
                        log.warn("死信告警外发失败：{}", e.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            // 告警通道自身异常绝不能影响主流程
            log.warn("死信告警外发异常：{}", e.getMessage());
        }
    }

    private String toJson(Map<String, Integer> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        map.forEach((k, v) -> sb.append('"').append(k).append("\":").append(v).append(','));
        sb.setLength(sb.length() - 1);
        return sb.append('}').toString();
    }
}
