package com.zhixing.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.mq.MqTopics;
import com.zhixing.common.mq.RocketMQTemplate;
import com.zhixing.common.utils.SnowflakeIdGenerator;
import com.zhixing.trade.domain.po.OrderMsg;
import com.zhixing.trade.mapper.OrderMsgMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 本地消息表（Outbox）服务。
 * <ul>
 *   <li>与订单在同一本地事务写入"待发送消息"，保证消息不丢失（最终一致）；</li>
 *   <li>定时任务扫描未投递消息补偿投递，解决 MQ 短暂不可用或宕机的场景；</li>
 *   <li>超过最大重试次数转为死信，人工介入。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderMsgService {

    /** 待投递 */
    public static final int STATUS_PENDING = 0;
    /** 已投递 */
    public static final int STATUS_DELIVERED = 1;
    /** 已消费 */
    public static final int STATUS_CONSUMED = 2;
    /** 死信 */
    public static final int STATUS_DEAD = 3;

    private final OrderMsgMapper orderMsgMapper;
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    @Value("${tx.order.msg-scan-batch:100}")
    private int scanBatch;

    /**
     * 登记一条待发送消息（须在业务事务内调用，与订单同事务提交）。
     *
     * @param bizSuffix 业务键后缀（如 couponUse / orderPaid / orderTimeout）
     */
    @Transactional(rollbackFor = Exception.class)
    public Long enqueue(Long orderId, String bizSuffix, String topic, String tag, Object payload) {
        OrderMsg msg = new OrderMsg();
        msg.setId(SnowflakeIdGenerator.getInstance().nextId());
        msg.setOrderId(orderId);
        msg.setBizKey(orderId + ":" + bizSuffix);
        msg.setTopic(topic);
        msg.setTag(tag);
        msg.setPayload(toJson(payload));
        msg.setStatus(STATUS_PENDING);
        msg.setRetryCount(0);
        msg.setMaxRetry(5);
        msg.setNextRetryTime(LocalDateTime.now());
        try {
            orderMsgMapper.insert(msg);
        } catch (DuplicateKeyException e) {
            log.warn("本地消息重复登记，忽略：bizKey={}", msg.getBizKey());
            return null;
        }
        return msg.getId();
    }

    /**
     * 定时扫描补偿投递：将未投递消息发送到 MQ，成功后标记已投递。
     */
    @Scheduled(fixedDelayString = "${tx.order.msg-scan-admin:3000}")
    public void deliverPendings() {
        List<OrderMsg> pendings = orderMsgMapper.selectList(new LambdaQueryWrapper<OrderMsg>()
                .eq(OrderMsg::getStatus, STATUS_PENDING)
                .le(OrderMsg::getNextRetryTime, LocalDateTime.now())
                .orderByAsc(OrderMsg::getId)
                .last("LIMIT " + scanBatch));
        for (OrderMsg msg : pendings) {
            boolean ok = rocketMQTemplate.sendPayload(msg.getTopic(), msg.getTag(), msg.getPayload());
            if (ok) {
                orderMsgMapper.update(null, new LambdaUpdateWrapper<OrderMsg>()
                        .eq(OrderMsg::getId, msg.getId())
                        .set(OrderMsg::getStatus, STATUS_DELIVERED)
                        .set(OrderMsg::getUpdateTime, LocalDateTime.now()));
            } else {
                int nextRetry = (msg.getRetryCount() == null ? 0 : msg.getRetryCount()) + 1;
                int status = nextRetry > msg.getMaxRetry() ? STATUS_DEAD : STATUS_PENDING;
                LocalDateTime nextTime = status == STATUS_DEAD ? null
                        : LocalDateTime.now().plusSeconds(Math.min(1L << nextRetry, 300));
                orderMsgMapper.update(null, new LambdaUpdateWrapper<OrderMsg>()
                        .eq(OrderMsg::getId, msg.getId())
                        .set(OrderMsg::getRetryCount, nextRetry)
                        .set(OrderMsg::getStatus, status)
                        .set(OrderMsg::getNextRetryTime, nextTime)
                        .set(OrderMsg::getUpdateTime, LocalDateTime.now()));
                if (status == STATUS_DEAD) {
                    log.error("本地消息投递失败达到上限，转为死信：bizKey={}", msg.getBizKey());
                }
            }
        }
    }

    /**
     * 查询「订单支付成功」事件投递失败、已转死信的订单 id（去重）。
     * <p>
     * 这些订单的 {@code orderPaid} 事件永远不会到达 zx-learning。若不补偿，
     * 就会出现「订单已支付、"我的课表"里却没有该课程」的不一致 —— 用户再点购买
     * 还会被"已拥有"拦截，形成死锁。由 {@code LessonReconcileJob} 消费本结果做补开课。
     */
    public List<Long> deadPaidOrderIds(int limit) {
        return orderMsgMapper.selectList(new LambdaQueryWrapper<OrderMsg>()
                        .select(OrderMsg::getOrderId)
                        .eq(OrderMsg::getTag, MqTopics.Tags.ORDER_PAID)
                        .eq(OrderMsg::getStatus, STATUS_DEAD)
                        .orderByAsc(OrderMsg::getId)
                        .last("LIMIT " + limit))
                .stream()
                .map(OrderMsg::getOrderId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /**
     * 补偿成功：把该订单的「支付成功」死信标记为已消费，避免每轮扫描重复补偿。
     */
    public void markPaidMsgConsumed(Long orderId) {
        orderMsgMapper.update(null, new LambdaUpdateWrapper<OrderMsg>()
                .eq(OrderMsg::getOrderId, orderId)
                .eq(OrderMsg::getTag, MqTopics.Tags.ORDER_PAID)
                .eq(OrderMsg::getStatus, STATUS_DEAD)
                .set(OrderMsg::getStatus, STATUS_CONSUMED)
                .set(OrderMsg::getUpdateTime, LocalDateTime.now()));
    }

    /**
     * 死信重放：把已转死信的消息退回「待投递」，交由 {@link #deliverPendings()} 再投一次。
     * <p>
     * 背景（全量测试发现）：原实现只有 {@code orderPaid} 一类死信有补偿通道
     * （{@code LessonReconcileJob}），其余 tag —— {@code LOCK}/{@code CONFIRM}（课程名额）、
     * {@code USE}/{@code REFUND}（券核销）—— 一旦投递失败转死信就**永久残留、无人消费、
     * 无任何告警**，会造成课程名额/券核销的静默不一致。
     * <p>
     * 安全性：重放只是把状态退回待投递，真正消费仍由各消费端幂等逻辑兜底
     * （{@code quota:lock:}/{@code quota:confirm:} 流水去重、券核销按订单幂等）。
     * 每次重放只给<b>一次</b>投递机会（retry_count 直接置为 maxRetry，失败即回到死信），
     * 避免"永久不可消费的毒消息"被无限重放放大流量。
     *
     * @param limit 单次重放上限
     * @return 实际退回待投递的消息条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int replayDeadMsgs(int limit) {
        List<OrderMsg> deads = orderMsgMapper.selectList(new LambdaQueryWrapper<OrderMsg>()
                .eq(OrderMsg::getStatus, STATUS_DEAD)
                .orderByAsc(OrderMsg::getId)
                .last("LIMIT " + limit));
        if (deads == null || deads.isEmpty()) {
            return 0;
        }
        int replayed = 0;
        for (OrderMsg msg : deads) {
            replayed += orderMsgMapper.update(null, new LambdaUpdateWrapper<OrderMsg>()
                    .eq(OrderMsg::getId, msg.getId())
                    .eq(OrderMsg::getStatus, STATUS_DEAD)
                    .set(OrderMsg::getRetryCount, msg.getMaxRetry() == null ? 5 : msg.getMaxRetry())
                    .set(OrderMsg::getStatus, STATUS_PENDING)
                    .set(OrderMsg::getNextRetryTime, LocalDateTime.now())
                    .set(OrderMsg::getUpdateTime, LocalDateTime.now()));
        }
        if (replayed > 0) {
            log.warn("死信重放：{} 条消息退回待投递（单次重放只给一次投递机会）", replayed);
        }
        return replayed;
    }

    /**
     * 死信快照（按 tag 计数），供运维/看板观测。
     * 正常水位应为空 Map —— 任何非空值都意味着有业务事件永久未送达。
     */
    public java.util.Map<String, Integer> deadMsgSummary() {
        List<OrderMsg> deads = orderMsgMapper.selectList(new LambdaQueryWrapper<OrderMsg>()
                .eq(OrderMsg::getStatus, STATUS_DEAD));
        java.util.Map<String, Integer> summary = new java.util.TreeMap<>();
        for (OrderMsg msg : deads) {
            String tag = msg.getTag() == null ? "UNKNOWN" : msg.getTag();
            summary.merge(tag, 1, Integer::sum);
        }
        return summary;
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("消息序列化失败", e);
        }
    }
}