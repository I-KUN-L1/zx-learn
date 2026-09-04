package com.zhixing.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("消息序列化失败", e);
        }
    }
}