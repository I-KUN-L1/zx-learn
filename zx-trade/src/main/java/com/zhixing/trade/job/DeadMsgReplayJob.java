package com.zhixing.trade.job;

import com.zhixing.trade.metrics.OrderMsgMetrics;
import com.zhixing.trade.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 死信重放任务：把超出重试上限、已被本地消息表永久放弃的消息退回「待投递」再投一次。
 * <p>
 * 为什么需要它：{@link LessonReconcileJob} 只补偿 {@code orderPaid} 一类死信，
 * 而 {@code LOCK}/{@code CONFIRM}（zx-course 课程名额）与 {@code USE}/{@code REFUND}
 * （券核销流水）同样会因 MQ 不可用/消费失败转死信 —— 这些消息原先**没有任何补偿通道**，
 * 会永久残留（全量测试实测存在 3 条长期死信）且无告警，可能造成名额/券的静默不一致。
 * <p>
 * 安全性：重放仅改消息状态，业务幂等由消费端保证（名额流水去重、券核销按订单幂等）；
 * 每次重放只给一次投递机会，失败立即回到死信，不会形成无限重放。
 * 可通过 {@code tx.order.dead-replay-enabled=false} 关闭。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadMsgReplayJob {

    private final OrderService orderService;
    private final OrderMsgMetrics metrics;

    /** 是否启用重放（默认启用）。关闭后仍会刷新死信指标与告警，只是不再自动退回待投递 */
    @Value("${tx.order.dead-replay-enabled:true}")
    private boolean enabled;

    /** 单轮最多重放条数 */
    @Value("${tx.order.dead-replay-batch:200}")
    private int batch;

    @Scheduled(fixedDelayString = "${tx.order.dead-replay-interval:1800000}")
    public void replayDeadMsgs() {
        try {
            if (enabled) {
                int replayed = orderService.replayDeadMsgs(batch);
                if (replayed > 0) {
                    log.info("死信重放完成：本轮退回待投递 {} 条", replayed);
                }
            }
            // 指标与告警**独立于重放开关**：死信的可见性必须始终成立，
            // 否则关掉重放等于把"已付款未开课"这类事故彻底藏起来。
            metrics.refresh(orderService.deadMsgSummary());
        } catch (Exception e) {
            log.error("死信重放任务异常：{}", e.getMessage());
        }
    }
}
