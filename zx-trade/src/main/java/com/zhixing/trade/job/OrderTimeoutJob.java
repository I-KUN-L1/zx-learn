package com.zhixing.trade.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixing.trade.domain.po.Order;
import com.zhixing.trade.mapper.OrderMapper;
import com.zhixing.trade.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单超时关单兜底扫描。
 * <p>
 * 正常情况下由 RocketMQ 延迟消息触发关单；但当 MQ 挂掉/延迟消息丢失时，
 * 此定时任务会兜底拉起超时未支付订单，保证最终一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutJob {

    private final OrderMapper orderMapper;
    private final OrderService orderService;

    @Value("${tx.order.expire-minutes:15}")
    private int expireMinutes;

    @Scheduled(fixedDelay = 60000)
    public void closeExpiredOrders() {
        if (expireMinutes < 0) {
            return;
        }
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(expireMinutes);
        List<Order> pendings = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, OrderService.STATUS_UNPAID)
                .lt(Order::getCreateTime, deadline)
                .orderByAsc(Order::getId)
                .last("LIMIT 200"));
        if (pendings.isEmpty()) {
            return;
        }
        log.info("超时关单兜底扫描命中 {} 笔订单", pendings.size());
        for (Order order : pendings) {
            try {
                orderService.closeExpired(order.getId());
            } catch (Exception e) {
                log.error("兜底关单失败：orderId={}, err={}", order.getId(), e.getMessage());
            }
        }
    }
}