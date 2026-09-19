package com.zhixing.trade.job;

import com.zhixing.trade.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 课表对账补偿任务：修复「订单已支付，但 zx-learning 课表里没有该课程」的不一致。
 * <p>
 * 为什么需要它：正常链路下开课有两条保障 —— 支付成功时的同步开课（Feign afterCommit）
 * 与本地消息表的 orderPaid 事件（MQ）。但两者都可能失败（学习服务不可用 / MQ 不可用），
 * 而本地消息表在重试超过 max_retry 后会转成<b>死信</b>并永久放弃投递
 * （见 {@code order_msg.status = 3}）。此时订单已支付、课表却永远为空，用户再点购买
 * 还会被"课程已拥有"拦截 —— 两端状态不一致且用户被卡死。
 * <p>
 * 本任务只针对「orderPaid 事件转死信」的订单做补开课补偿，语义精确，不会误伤正常业务。
 * 可通过 {@code tx.lesson.reconcile-enabled=false} 关闭。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LessonReconcileJob {

    private final OrderService orderService;

    /** 是否启用（默认启用） */
    @Value("${tx.lesson.reconcile-enabled:true}")
    private boolean enabled;

    /** 单轮最多处理的死信订单数 */
    @Value("${tx.lesson.reconcile-batch:200}")
    private int batch;

    @Scheduled(fixedDelayString = "${tx.lesson.reconcile-interval:300000}")
    public void reconcileLessons() {
        if (!enabled) {
            return;
        }
        try {
            int healed = orderService.reconcileDeadPaidOrders(batch);
            if (healed > 0) {
                log.info("课表对账补偿完成：本轮补开课 {} 门", healed);
            }
        } catch (Exception e) {
            log.error("课表对账补偿任务异常：{}", e.getMessage());
        }
    }
}
