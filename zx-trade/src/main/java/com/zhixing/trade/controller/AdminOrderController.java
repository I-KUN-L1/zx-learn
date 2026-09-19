package com.zhixing.trade.controller;

import com.zhixing.common.annotation.NoWrapper;
import com.zhixing.common.annotation.RequireRole;
import com.zhixing.common.constants.UserRole;
import com.zhixing.common.domain.PageDTO;
import com.zhixing.common.domain.R;
import com.zhixing.trade.domain.dto.AdminOrderQueryDTO;
import com.zhixing.trade.domain.vo.AdminOrderStatsVO;
import com.zhixing.trade.domain.vo.AdminOrderVO;
import com.zhixing.trade.service.AdminOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 管理员端订单管理。
 * <p>
 * 权限：类级 {@code @RequireRole(UserRole.STAFF)} —— 仅管理员（员工）可访问，
 * 学员与教师访问任一接口均返回 403；角色由网关透传的 role-info 决定，无法通过前端绕过。
 * <p>
 * 路径前缀 {@code /orders/admin/**} 已由网关 {@code /orders/**} 规则转发到 zx-trade，
 * 与学员端 {@code /orders/**} 共用同一路由但控制器分离，接口契约互不污染。
 */
@Slf4j
@RestController
@RequestMapping("/orders/admin")
@RequiredArgsConstructor
@RequireRole(UserRole.STAFF)
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    /** 订单分页（支持订单号/用户/关键词/状态/课程/时间/金额多条件筛选） */
    @GetMapping("/page")
    public R<PageDTO<AdminOrderVO>> page(AdminOrderQueryDTO query) {
        return R.ok(adminOrderService.page(query));
    }

    /** 订单详情（含下单用户信息） */
    @GetMapping("/{id}")
    public R<AdminOrderVO> detail(@PathVariable Long id) {
        return R.ok(adminOrderService.detail(id));
    }

    /** 修改订单状态：0 待支付 1 已支付 2 已关闭 3 退款中 4 已退款 */
    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        adminOrderService.updateStatus(id, status);
        return R.ok();
    }

    /**
     * 删除订单（清理无用订单）。
     * <p>
     * 仅允许删除不贡献销售数据的订单：待支付(0)、已关闭(2)、已退款(4)；
     * 已支付(1) 与 退款中(3) 会被拒绝。逻辑删除，不影响销售额统计口径。
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        adminOrderService.deleteOrder(id);
        return R.ok();
    }

    /** 退款审核：通过则订单转已退款，拒绝则回退已支付 */
    @PutMapping("/refund/audit")
    public R<Void> auditRefund(@RequestBody Map<String, Object> body) {
        Long refundId = asLong(body.get("refundId"));
        boolean approved = Boolean.parseBoolean(String.valueOf(body.get("approved")));
        String remark = body.get("remark") == null ? null : String.valueOf(body.get("remark"));
        adminOrderService.auditRefund(refundId, approved, remark);
        return R.ok();
    }

    /** 退款申请分页（审核工作台） */
    @GetMapping("/refunds")
    public R<PageDTO<Map<String, Object>>> refunds(@RequestParam(required = false) Integer pageNo,
                                                   @RequestParam(required = false) Integer pageSize,
                                                   @RequestParam(required = false) Integer status) {
        return R.ok(adminOrderService.refundPage(pageNo, pageSize, status));
    }

    /** 订单统计（各状态订单量 + 已支付销售额） */
    @GetMapping("/statistics")
    public R<AdminOrderStatsVO> statistics() {
        return R.ok(adminOrderService.stats());
    }

    /**
     * 学员相关课程（退款审批辅助）：学员全部订单课程 + 学习进度/时长/最近学习时间 + 消费汇总。
     */
    @GetMapping("/users/{userId}/courses")
    public R<Map<String, Object>> userCourses(@PathVariable Long userId) {
        return R.ok(adminOrderService.userCourses(userId));
    }

    /**
     * 订单导出（CSV，带 UTF-8 BOM，Excel 双击直接可读）。
     * <p>
     * ⚠ 必须标 {@code @NoWrapper}：全局 {@code WrapperResponseBodyAdvice} 会把所有
     * 非 R 返回体包装成 {@code R}，包括本方法的 {@code ResponseEntity<byte[]>}。
     * 被包装后 {@code ByteArrayHttpMessageConverter.getContentLength} 拿到的是 R
     * 而不是 byte[]，直接 ClassCastException → 500「系统繁忙」，导出功能完全不可用。
     */
    @GetMapping("/export")
    @NoWrapper
    public ResponseEntity<byte[]> export(AdminOrderQueryDTO query) {
        byte[] csv = adminOrderService.exportCsv(query);
        String filename = URLEncoder.encode("订单导出.csv", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"orders.csv\"; filename*=UTF-8''" + filename)
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }

    private Long asLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(v));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
