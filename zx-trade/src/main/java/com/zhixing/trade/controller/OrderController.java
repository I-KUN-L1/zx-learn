package com.zhixing.trade.controller;

import com.zhixing.api.dto.trade.TradeStatsDTO;
import com.zhixing.common.annotation.NoWrapper;
import com.zhixing.common.annotation.RequireRole;
import com.zhixing.common.constants.UserRole;
import com.zhixing.common.domain.R;
import com.zhixing.common.utils.InternalOnlyGuard;
import com.zhixing.common.utils.UserContext;
import com.zhixing.trade.domain.dto.OrderFormDTO;
import com.zhixing.trade.domain.po.Order;
import com.zhixing.trade.service.OrderService;
import com.zhixing.trade.service.PayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 订单 + 订单明细。
 * <p>
 * 权限：下单 / 支付 / 查订单均为学员端行为，仅学员(2)可操作，
 * 管理员(员工)与教师账号一律 403，防止管理账号参与学员交易行为。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final PayService payService;

    /**
     * 下单：订单 / 明细 / 本地消息同事务落库，生成雪花订单号；可选使用优惠券。
     */
    @PostMapping("/orders/placeOrder")
    @RequireRole(UserRole.STUDENT)
    public R<Long> placeOrder(@RequestBody OrderFormDTO form) {
        return R.ok(orderService.placeOrder(form));
    }

    /**
     * 支付回调：验签 → 流水幂等 → 更新订单 → 发支付成功事件。
     */
    @PostMapping("/orders/pay/callback")
    @RequireRole(UserRole.STUDENT)
    public R<Void> payCallback(@RequestBody OrderFormDTO body) {
        payService.payCallback(body);
        return R.ok();
    }

    /**
     * 生成支付回调签名（Mock 渠道联调用，便于用 curl 自测）
     */
    @GetMapping("/orders/pay/sign")
    @RequireRole(UserRole.STUDENT)
    public R<String> buildSign(@RequestParam Long orderId,
                               @RequestParam Long amount,
                               @RequestParam String payNo) {
        return R.ok(payService.buildSign(orderId, amount, payNo));
    }

    /**
     * 手动触发超时关单（联调/演示用；生产不开放）
     */
    @PostMapping("/orders/{id}/timeout")
    @RequireRole(UserRole.STUDENT)
    public R<Void> triggerTimeout(@PathVariable Long id) {
        orderService.closeExpired(id);
        return R.ok();
    }

    @GetMapping("/orders/page")
    @RequireRole(UserRole.STUDENT)
    public R<List<Order>> page() {
        return R.ok(orderService.page(UserContext.getUserId()));
    }

    @GetMapping("/orders/{id}")
    @RequireRole(UserRole.STUDENT)
    public R<Order> getById(@PathVariable Long id) {
        return R.ok(orderService.getById(id));
    }

    /**
     * 0 元课直购到账（免费课程报名）
     */
    @PostMapping("/orders/freeCourse/{courseId}")
    @RequireRole(UserRole.STUDENT)
    public R<Long> freeCourse(@PathVariable Long courseId) {
        return R.ok(orderService.freeCourse(courseId));
    }

    // ============ 订单明细内部接口（Feign 调用，不包装） ============

    @GetMapping("/order-details/enrollNum")
    @NoWrapper
    public Integer countEnrollNum(@RequestParam Long courseId) {
        return orderService.countEnrollNum(courseId);
    }

    @GetMapping("/order-details/course/{id}")
    @NoWrapper
    public Boolean checkCourseBought(@PathVariable("id") Long courseId) {
        return orderService.checkCourseBought(courseId);
    }

    /**
     * 交易统计聚合（内部 Feign 接口，供管理端看板消费，不包装）。
     * 仅限服务间调用：外部用户（含管理员）经网关访问一律 403。
     */
    @GetMapping("/order-details/stats/dashboard")
    @NoWrapper
    public TradeStatsDTO dashboardStats() {
        InternalOnlyGuard.checkInternal();
        return orderService.dashboardStats();
    }
}
