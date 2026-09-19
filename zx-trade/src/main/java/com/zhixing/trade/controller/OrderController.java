package com.zhixing.trade.controller;

import com.zhixing.api.dto.trade.TradeStatsDTO;
import com.zhixing.common.annotation.NoWrapper;
import com.zhixing.common.annotation.RequireRole;
import com.zhixing.common.constants.UserRole;
import com.zhixing.common.domain.PageDTO;
import com.zhixing.common.domain.PageQuery;
import com.zhixing.common.domain.R;
import com.zhixing.common.utils.InternalOnlyGuard;
import com.zhixing.common.utils.UserContext;
import com.zhixing.trade.domain.dto.OrderFormDTO;
import com.zhixing.trade.domain.vo.OrderVO;
import com.zhixing.trade.service.OrderService;
import com.zhixing.trade.service.PayService;
import com.zhixing.trade.service.RefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
    private final RefundService refundService;

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
     * Mock 支付（前端"我的订单-去支付"直连通道）：内部生成合法签名走真实回调链路，
     * 流水幂等（MOCK-订单号），复用支付成功事件（课程开通/销量确认）。
     */
    @PostMapping("/orders/pay/mock/{id}")
    @RequireRole(UserRole.STUDENT)
    public R<Void> mockPay(@PathVariable Long id) {
        payService.mockPay(id);
        return R.ok();
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

    /**
     * 我的订单分页（对齐前端 PageDTO<OrderVO> 契约）。
     * status 为前端契约状态：1-待支付 2-已支付 3-已关闭 5-退款中 6-已退款，空为全部。
     */
    @GetMapping("/orders/page")
    @RequireRole(UserRole.STUDENT)
    public R<PageDTO<OrderVO>> page(PageQuery query,
                                    @RequestParam(required = false) Integer status) {
        return R.ok(orderService.pageQuery(query, status));
    }

    /**
     * 我的订单详情（对齐前端 OrderVO 契约：状态映射 + 明细组装；仅本人可查）。
     */
    @GetMapping("/orders/{id}")
    @RequireRole(UserRole.STUDENT)
    public R<OrderVO> getById(@PathVariable Long id) {
        return R.ok(orderService.getMyOrderVO(id));
    }

    /**
     * 0 元课直购到账（免费课程报名）
     */
    @PostMapping("/orders/freeCourse/{courseId}")
    @RequireRole(UserRole.STUDENT)
    public R<Long> freeCourse(@PathVariable Long courseId) {
        return R.ok(orderService.freeCourse(courseId));
    }

    /**
     * 学员删除订单（软删除）。
     * <p>
     * 仅允许删除<b>已终结</b>的订单：已支付、已关闭、已退款；待支付/退款中须先完成
     * 支付、取消或等待退款审核。删除只置 {@code user_deleted=1}，管理端仍保留该记录，
     * 不影响销售额等统计口径。
     */
    @DeleteMapping("/orders/{id}")
    @RequireRole(UserRole.STUDENT)
    public R<Void> deleteMyOrder(@PathVariable Long id) {
        orderService.deleteMyOrder(id);
        return R.ok();
    }

    /**
     * 申请退款（分级策略）：
     * <ul>
     *   <li>已支付且支付后 7 天内为可申请的基础条件；</li>
     *   <li>课程未开始学习 -> 直接退款成功（mode=INSTANT）；</li>
     *   <li>否则生成待审核退款单，订单转"退款中"（mode=AUDIT），由管理员审批。</li>
     * </ul>
     */
    @PostMapping("/orders/{id}/refund")
    @RequireRole(UserRole.STUDENT)
    public R<Map<String, Object>> applyRefund(@PathVariable Long id,
                                              @RequestBody(required = false) Map<String, Object> body) {
        String reason = body == null ? null : (body.get("reason") == null ? null : String.valueOf(body.get("reason")));
        return R.ok(refundService.applySmart(id, reason));
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
     * 当前学员已购课程 id 集合（已支付订单去重）。
     * 供课程列表/详情展示「已拥有」并禁用购买、确认下单页前置过滤已购课程。
     */
    @GetMapping("/orders/bought-course-ids")
    @RequireRole(UserRole.STUDENT)
    public R<List<Long>> boughtCourseIds() {
        return R.ok(orderService.boughtCourseIds());
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

    /**
     * 课表对账补偿（内部接口，运维手动触发 / 定时任务同源逻辑，不包装）。
     * <p>
     * 修复「订单已支付，但 zx-learning 课表里没有该课程」的不一致：定点补偿
     * {@code orderPaid} 事件投递失败转为死信（order_msg.status=3）的订单。
     * 返回本轮补开课的门数。定时任务 {@code LessonReconcileJob} 会自动跑同一逻辑，
     * 此接口用于 MQ 恢复后立即触发，或排障时手动核对。
     * 仅限服务间调用：外部用户（含管理员）经网关访问一律 403。
     */
    @PostMapping("/order-details/reconcile-lessons")
    @NoWrapper
    public Integer reconcileLessons(@RequestParam(value = "limit", required = false) Integer limit) {
        InternalOnlyGuard.checkInternal();
        return orderService.reconcileDeadPaidOrders(limit == null || limit <= 0 ? 200 : limit);
    }

    /**
     * 死信重放（内部接口，运维手动触发 / 定时任务同源逻辑，不包装）。
     * <p>
     * 把 {@code orderPaid} 之外（LOCK/CONFIRM/USE/REFUND）的死信退回待投递再投一次——
     * 这些类型原先无任何补偿通道，投递失败即永久残留且无告警。
     * 仅限服务间调用：外部用户（含管理员）经网关访问一律 403。
     */
    @PostMapping("/order-details/replay-dead-msgs")
    @NoWrapper
    public Integer replayDeadMsgs(@RequestParam(value = "limit", required = false) Integer limit) {
        InternalOnlyGuard.checkInternal();
        return orderService.replayDeadMsgs(limit == null || limit <= 0 ? 200 : limit);
    }

    /**
     * 死信快照（内部接口，不包装）：按 tag 统计死信条数，正常应为空对象。
     * 供管理端看板/巡检观测"是否有业务事件永久未送达"。
     */
    @GetMapping("/order-details/dead-msgs")
    @NoWrapper
    public Map<String, Integer> deadMsgs() {
        InternalOnlyGuard.checkInternal();
        return orderService.deadMsgSummary();
    }
}
