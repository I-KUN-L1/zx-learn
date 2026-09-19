package com.zhixing.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixing.api.client.learning.LearningClient;
import com.zhixing.api.dto.learning.LearningRecordDTO;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.exceptions.BizIllegalException;
import com.zhixing.common.exceptions.ForbiddenException;
import com.zhixing.common.utils.UserContext;
import com.zhixing.trade.domain.po.Order;
import com.zhixing.trade.domain.po.RefundApply;
import com.zhixing.trade.mapper.RefundApplyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 退款申请服务（持久化）。
 * <p>
 * 分级退款策略（学员端申请）：
 * <ul>
 *   <li><b>基础条件</b>：仅"已支付"且支付后 {@value #REFUND_WINDOW_DAYS} 天内的订单可申请退款；</li>
 *   <li><b>进一步条件（自动退款）</b>：课程无任何学习记录（未开始学习）时系统直接退款成功，
 *       订单流转"已退款"，并落一条"已通过"退款单留痕；</li>
 *   <li>未满足进一步条件：生成"待审核"退款单，订单流转"退款中"，由管理员人工审批。</li>
 * </ul>
 * 状态机联动订单：申请退款 -> 订单进入"退款中"；审批通过 -> 订单"已退款"，拒绝 -> 回退"已支付"。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    /** 待审核 */
    private static final int PENDING = 0;
    /** 已通过 */
    private static final int APPROVED = 1;
    /** 已拒绝 */
    private static final int REJECTED = 2;

    /** 可申请退款的期限（自支付时间起，天） */
    public static final int REFUND_WINDOW_DAYS = 7;

    /** 自动退款 */
    public static final String MODE_INSTANT = "INSTANT";
    /** 转人工审核 */
    public static final String MODE_AUDIT = "AUDIT";

    private final RefundApplyMapper refundApplyMapper;
    private final OrderService orderService;
    private final LearningClient learningClient;

    /**
     * 申请退款（兼容旧契约 {@code POST /refund-apply}）：内部走分级退款策略。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long apply(Map<String, Object> apply) {
        Long orderId = longOf(apply == null ? null : apply.get("orderId"));
        if (orderId == null) {
            throw new BadRequestException("订单 id 不能为空");
        }
        String reason = apply == null ? null : strOf(apply.get("reason"));
        return (Long) applySmart(orderId, reason).get("refundId");
    }

    /**
     * 分级退款申请：满足进一步条件直接退款成功，否则转管理员审核。
     *
     * @return {@code {mode: INSTANT|AUDIT, refundId, message}}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> applySmart(Long orderId, String reason) {
        if (orderId == null) {
            throw new BadRequestException("订单 id 不能为空");
        }
        Order order = orderService.getById(orderId);
        if (!Objects.equals(order.getUserId(), UserContext.getUserId())) {
            throw new ForbiddenException("无权操作他人订单");
        }
        // ---- 基础条件：已支付 + 在可退款期限内 ----
        if (!Integer.valueOf(OrderService.STATUS_PAID).equals(order.getStatus())) {
            throw new BizIllegalException("仅已支付订单可申请退款");
        }
        LocalDateTime paidAt = order.getPayTime() != null ? order.getPayTime() : order.getCreateTime();
        if (paidAt != null && paidAt.plusDays(REFUND_WINDOW_DAYS).isBefore(LocalDateTime.now())) {
            throw new BizIllegalException("已超过可退款期限（支付后 " + REFUND_WINDOW_DAYS + " 天内可申请）");
        }

        // ---- 进一步条件：课程未开始学习 -> 直接退款成功 ----
        if (isCourseUntouched(order)) {
            String remark = "系统自动审核通过：课程未开始学习，支付 " + REFUND_WINDOW_DAYS + " 天内无理由退款";
            RefundApply record = insertApply(order, reason, APPROVED, remark);
            orderService.markRefunded(orderId);
            log.info("退款自动通过（未学习）：refundId={}, orderId={}", record.getId(), orderId);
            return result(MODE_INSTANT, record.getId(), "退款成功，款项将原路退回");
        }

        // ---- 未满足进一步条件：转人工审核 ----
        RefundApply record = insertApply(order, reason, PENDING, null);
        orderService.applyRefund(orderId);
        log.info("退款申请转人工审核：refundId={}, orderId={}", record.getId(), orderId);
        return result(MODE_AUDIT, record.getId(), "退款申请已提交，将由管理员审核");
    }

    /**
     * 课程是否"未开始学习"：无学习记录，或记录中学习时长与进度均为 0。
     * <p>
     * 学习服务不可用时按"已学习"处理（fail-safe），保证异常不会让本该人工审核的申请被自动放行。
     */
    private boolean isCourseUntouched(Order order) {
        if (order.getUserId() == null || order.getCourseId() == null) {
            return false;
        }
        List<LearningRecordDTO> records;
        try {
            records = learningClient.listRecords(order.getUserId());
        } catch (Exception e) {
            log.warn("查询学习记录失败，按已学习处理转人工审核：orderId={}, cause={}", order.getId(), e.getMessage());
            return false;
        }
        if (records == null || records.isEmpty()) {
            return true;
        }
        return records.stream()
                .filter(r -> Objects.equals(r.getCourseId(), order.getCourseId()))
                .allMatch(r -> (r.getLearnDuration() == null || r.getLearnDuration() <= 0)
                        && (r.getProgress() == null || r.getProgress() <= 0));
    }

    private RefundApply insertApply(Order order, String reason, int status, String remark) {
        RefundApply record = new RefundApply();
        record.setOrderId(order.getId());
        record.setUserId(order.getUserId());
        record.setCourseId(order.getCourseId());
        record.setAmount(order.getTotalFee());
        record.setReason(reason);
        record.setStatus(status);
        record.setRemark(remark);
        refundApplyMapper.insert(record);
        return record;
    }

    private Map<String, Object> result(String mode, Long refundId, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mode", mode);
        m.put("refundId", refundId);
        m.put("message", message);
        return m;
    }

    /**
     * 退款审批：approved=true 通过 -> 订单已退款；否则拒绝 -> 订单回退已支付
     */
    @Transactional(rollbackFor = Exception.class)
    public void approval(Map<String, Object> body) {
        Long id = longOf(body.get("id"));
        if (id == null) {
            throw new BadRequestException("退款单 id 不能为空");
        }
        Boolean approved = Boolean.valueOf(String.valueOf(body.get("approved")));
        RefundApply record = refundApplyMapper.selectById(id);
        if (record == null) {
            throw new BadRequestException("退款单不存在");
        }
        record.setStatus(approved ? APPROVED : REJECTED);
        record.setRemark(strOf(body.get("remark")));
        refundApplyMapper.updateById(record);

        orderService.confirmRefund(record.getOrderId(), approved);
    }

    public List<Map<String, Object>> page() {
        return refundApplyMapper.selectList(new LambdaQueryWrapper<RefundApply>()
                        .orderByDesc(RefundApply::getCreateTime)).stream()
                .map(RefundService::toMap)
                .collect(Collectors.toList());
    }

    public Map<String, Object> getById(Long id) {
        RefundApply record = refundApplyMapper.selectById(id);
        if (record == null) {
            throw new BadRequestException("退款单不存在");
        }
        return toMap(record);
    }

    private static Map<String, Object> toMap(RefundApply r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("orderId", r.getOrderId());
        m.put("userId", r.getUserId());
        m.put("courseId", r.getCourseId());
        m.put("amount", r.getAmount());
        m.put("reason", r.getReason());
        m.put("status", r.getStatus());
        m.put("remark", r.getRemark());
        m.put("createTime", r.getCreateTime());
        return m;
    }

    private static Long longOf(Object v) {
        return v instanceof Number n ? n.longValue() : null;
    }

    private static String strOf(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}