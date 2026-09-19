package com.zhixing.pay.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.zhixing.pay.domain.po.PayNotifyLog;
import com.zhixing.pay.domain.po.PayOrder;
import com.zhixing.pay.mapper.PayNotifyLogMapper;
import com.zhixing.pay.mapper.PayOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 支付单领域服务（持久化）。
 * <p>
 * 全部方法**刻意不加 {@code @Transactional}**：这里的幂等靠数据库唯一键兜底，
 * 逐条自动提交比"包一层事务再吞 DuplicateKeyException"更安全 —— 后者会把事务
 * 标记为 rollback-only，导致后续语句在提交时抛 {@code UnexpectedRollbackException}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayOrderService {

    /** 状态：待支付 */
    public static final int STATUS_PENDING = 0;
    /** 状态：已支付 */
    public static final int STATUS_PAID = 1;
    /** 状态：已关闭 */
    public static final int STATUS_CLOSED = 2;

    /** 渠道：支付宝 */
    public static final int CHANNEL_ALIPAY = 1;
    /** 渠道：微信支付 */
    public static final int CHANNEL_WXPAY = 2;

    /** 收银台跳转地址占位（骨架实现：真实接入时由渠道 SDK 返回） */
    private static final String PAY_URL_PLACEHOLDER = "weixin://wxpay/bizpayurl?pr=xxxx";

    /** 回调报文落库最大长度（与 pay_notify_log.raw VARCHAR(1024) 对齐） */
    private static final int RAW_MAX_LEN = 1024;

    /** 主键兜底查询的最大位数（超过 Long 安全范围直接放弃，避免 NumberFormatException → 500） */
    private static final int MAX_ID_DIGITS = 18;

    private final PayOrderMapper payOrderMapper;
    private final PayNotifyLogMapper payNotifyLogMapper;

    /**
     * 申请支付单（幂等）：同一业务订单号重复申请时**复用**已有支付单，而非新建。
     * 若已有支付单处于「已关闭」，则复活为「待支付」以允许用户重新支付。
     */
    public PayOrder applyOrReuse(String bizOrderNo, Long amount, Integer channel) {
        PayOrder order = new PayOrder();
        order.setId(IdWorker.getId());
        order.setBizOrderNo(bizOrderNo);
        order.setAmount(amount == null ? 0L : amount);
        order.setChannel(channel == null ? CHANNEL_ALIPAY : channel);
        order.setStatus(STATUS_PENDING);
        order.setPayUrl(PAY_URL_PLACEHOLDER);
        payOrderMapper.upsertOnBizOrderNo(order);
        PayOrder saved = findByBizOrderNo(bizOrderNo);
        log.info("申请支付单：bizOrderNo={}, payOrderId={}, status={}",
                bizOrderNo, saved == null ? null : saved.getId(), saved == null ? null : saved.getStatus());
        return saved;
    }

    /**
     * 查询支付单。
     * <p>
     * 路径参数名为 {@code bizOrderId}：优先按**业务订单号**匹配（符合参数语义），
     * 未命中再按支付单主键兜底（兼容早期按自增 id 查询的调用方）。
     * 两处都不命中返回 {@code null} —— 由调用方转成 404，绝不谎报"支付成功"。
     */
    public PayOrder findStatus(String bizOrderId) {
        if (bizOrderId == null || bizOrderId.isBlank()) {
            return null;
        }
        PayOrder byBizNo = findByBizOrderNo(bizOrderId);
        if (byBizNo != null) {
            return byBizNo;
        }
        // 主键兜底：位数受限，避免超长数字串触发 NumberFormatException（→ 500）
        if (bizOrderId.length() <= MAX_ID_DIGITS && bizOrderId.chars().allMatch(Character::isDigit)) {
            return payOrderMapper.selectById(Long.parseLong(bizOrderId));
        }
        return null;
    }

    /**
     * 受理渠道回调（幂等）。
     *
     * @return {@code true} 表示本次是新受理（首次到达）；{@code false} 表示重复投递已被幂等拦下
     */
    public boolean acceptNotify(String channel, String bizOrderNo, String payNo, String rawBody) {
        if (findNotifyLog(channel, payNo) != null) {
            log.info("渠道回调重复投递，已受理过：channel={}, payNo={}", channel, payNo);
            return false;
        }
        PayNotifyLog row = new PayNotifyLog();
        row.setChannel(channel);
        row.setBizOrderNo(bizOrderNo);
        row.setPayNo(payNo);
        row.setVerifyResult(1);
        row.setRaw(truncate(rawBody, RAW_MAX_LEN));
        try {
            payNotifyLogMapper.insert(row);
        } catch (DuplicateKeyException e) {
            // 并发重复投递：唯一键 uk_channel_pay_no 兜底
            log.info("渠道回调并发重复，唯一键拦下：channel={}, payNo={}", channel, payNo);
            return false;
        }
        markOrderPaid(channel, bizOrderNo, payNo);
        return true;
    }

    /** 渠道回调状态分布（运维排查用） */
    public Map<String, Object> notifySummary() {
        Long total = payNotifyLogMapper.selectCount(new LambdaQueryWrapper<>());
        return Map.of("notifyTotal", total == null ? 0L : total);
    }

    private void markOrderPaid(String channel, String bizOrderNo, String payNo) {
        if (bizOrderNo == null || bizOrderNo.isBlank()) {
            log.warn("渠道回调缺少 bizOrderNo，仅登记流水：channel={}, payNo={}", channel, payNo);
            return;
        }
        PayOrder order = findByBizOrderNo(bizOrderNo);
        if (order == null) {
            log.warn("渠道回调未匹配到支付单，仅登记流水：channel={}, bizOrderNo={}, payNo={}",
                    channel, bizOrderNo, payNo);
            return;
        }
        if (STATUS_PAID == (order.getStatus() == null ? -1 : order.getStatus())) {
            return;
        }
        order.setStatus(STATUS_PAID);
        order.setPayNo(payNo);
        order.setNotifyTime(LocalDateTime.now());
        payOrderMapper.updateById(order);
        log.info("支付单已置为已支付：payOrderId={}, bizOrderNo={}, payNo={}",
                order.getId(), bizOrderNo, payNo);
    }

    private PayNotifyLog findNotifyLog(String channel, String payNo) {
        return payNotifyLogMapper.selectOne(new LambdaQueryWrapper<PayNotifyLog>()
                .eq(PayNotifyLog::getChannel, channel)
                .eq(PayNotifyLog::getPayNo, payNo)
                .last("LIMIT 1"));
    }

    private PayOrder findByBizOrderNo(String bizOrderNo) {
        return payOrderMapper.selectOne(new LambdaQueryWrapper<PayOrder>()
                .eq(PayOrder::getBizOrderNo, bizOrderNo)
                .last("LIMIT 1"));
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
