package com.zhixing.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.trade.domain.po.Order;
import com.zhixing.trade.domain.po.RefundApply;
import com.zhixing.trade.mapper.RefundApplyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 退款申请服务（持久化）。
 * <p>
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

    private final RefundApplyMapper refundApplyMapper;
    private final OrderService orderService;

    /**
     * 申请退款，返回退款单 id
     */
    @Transactional(rollbackFor = Exception.class)
    public Long apply(Map<String, Object> apply) {
        Long orderId = longOf(apply.get("orderId"));
        if (orderId == null) {
            throw new BadRequestException("订单 id 不能为空");
        }
        Order order = orderService.getById(orderId);

        RefundApply record = new RefundApply();
        record.setOrderId(orderId);
        record.setUserId(order.getUserId());
        record.setCourseId(order.getCourseId());
        record.setAmount(order.getTotalFee());
        record.setReason(strOf(apply.get("reason")));
        record.setStatus(PENDING);
        refundApplyMapper.insert(record);

        // 订单流转到"退款中"
        orderService.applyRefund(orderId);
        log.info("退款申请成功：id={}, orderId={}", record.getId(), orderId);
        return record.getId();
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