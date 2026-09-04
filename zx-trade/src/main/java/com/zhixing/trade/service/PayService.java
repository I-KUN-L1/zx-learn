package com.zhixing.trade.service;

import com.zhixing.api.dto.trade.OrderPaidMsg;
import com.zhixing.api.dto.trade.QuotaMsg;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.mq.MqTopics;
import com.zhixing.trade.domain.dto.OrderFormDTO;
import com.zhixing.trade.domain.po.Order;
import com.zhixing.trade.domain.po.PayRecord;
import com.zhixing.trade.mapper.PayRecordMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * 支付回调服务。
 * <p>
 * 流程：验签 → 支付流水幂等（pay_no 唯一）→ 更新订单状态 → 发订单支付成功事件。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayService {

    private final PayRecordMapper payRecordMapper;
    private final OrderService orderService;
    private final OrderMsgService orderMsgService;
    private final TransactionTemplate transactionTemplate;

    @Value("${pay.callback-secret:}")
    private String callbackSecret;

    /**
     * 验签密钥 fail-fast：无默认值，缺失时启动即失败，避免带弱默认密钥上线。
     */
    @PostConstruct
    void checkCallbackSecret() {
        if (callbackSecret == null || callbackSecret.isBlank()) {
            throw new IllegalStateException("缺少支付回调验签密钥：请通过环境变量 PAY_CALLBACK_SECRET 配置（见 .env.example）");
        }
    }

    /**
     * 处理支付回调（幂等）：
     * <ol>
     *   <li>验签失败直接拒绝；</li>
     *   <li>{@code pay_no} 唯一索引 + 预先插入流水，保证同一笔回调只处理一次；</li>
     *   <li>更新订单为已支付；</li>
     *   <li>写入本地消息表，异步发"订单支付成功"事件。</li>
     * </ol>
     */
    public void payCallback(OrderFormDTO form) {
        verifySign(form);

        boolean first = createPayRecordOnce(form);
        if (!first) {
            log.info("支付回调幂等，已处理过：payNo={}", form.getPayNo());
            return;
        }

        Boolean transitioned = transactionTemplate.execute(status -> {
            boolean changed = orderService.markPaid(form.getId(), form.getPayType());
            if (changed) {
                Order order = orderService.getById(form.getId());
                OrderPaidMsg msg = new OrderPaidMsg();
                msg.setOrderId(order.getId());
                msg.setOrderNo(order.getOrderNo());
                msg.setUserId(order.getUserId());
                msg.setCourseId(order.getCourseId());
                msg.setCourseName(order.getCourseName());
                msg.setAmount(order.getTotalFee());
                msg.setPayType(form.getPayType());
                msg.setPayNo(form.getPayNo());
                orderMsgService.enqueue(order.getId(), "orderPaid",
                        MqTopics.TOPIC_ORDER_PAID, MqTopics.Tags.ORDER_PAID, msg);
                // 名额确认：锁定转销量（zx-course 消费）
                QuotaMsg quotaMsg = new QuotaMsg();
                quotaMsg.setOrderId(order.getId());
                quotaMsg.setCourseId(order.getCourseId());
                quotaMsg.setUserId(order.getUserId());
                orderMsgService.enqueue(order.getId(), "quotaConfirm",
                        MqTopics.TOPIC_COURSE_QUOTA, MqTopics.Tags.QUOTA_CONFIRM, quotaMsg);
            }
            return changed;
        });
        log.info("支付回调完成：orderId={}, transitioned={}", form.getId(), transitioned);
    }

    /**
     * 生成回调签名（Mock 渠道用于本地联调）
     */
    public String buildSign(Long orderId, Long amount, String payNo) {
        return hmac(orderId + "|" + amount + "|" + payNo, callbackSecret);
    }

    /**
     * 校验回调签名，验签失败抛出异常
     */
    public void verifySign(OrderFormDTO form) {
        if (form == null || form.getSign() == null || form.getSign().isBlank()) {
            throw new BadRequestException("回调签名缺失");
        }
        String expected = buildSign(form.getId(), form.getTotalFee(), form.getPayNo());
        if (!expected.equalsIgnoreCase(form.getSign())) {
            throw new BadRequestException("回调签名校验失败");
        }
        log.info("支付回调验签通过：orderId={}", form.getId());
    }

    /**
     * 以支付流水表做幂等：pay_no 唯一，重复插入返回 false。
     */
    private boolean createPayRecordOnce(OrderFormDTO form) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            PayRecord record = new PayRecord();
            record.setOrderId(form.getId());
            record.setPayNo(form.getPayNo());
            record.setPayType(form.getPayType());
            record.setAmount(form.getTotalFee());
            record.setStatus(0);
            record.setCallbackTime(LocalDateTime.now());
            record.setRaw(form.getRaw());
            try {
                payRecordMapper.insert(record);
                return true;
            } catch (DuplicateKeyException e) {
                return false;
            }
        }));
    }

    private String hmac(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("签名计算失败", e);
        }
    }
}