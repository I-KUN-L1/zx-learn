package com.zhixing.trade.controller;

import com.zhixing.common.annotation.RequireRole;
import com.zhixing.common.constants.UserRole;
import com.zhixing.common.domain.R;
import com.zhixing.trade.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 退款申请（持久化）。
 * 契约不变：apply / approval / page / getById。
 * 权限：退款审核为管理端操作，仅员工(1)；学员申请与查询不做限制。
 */
@RestController
@RequestMapping("/refund-apply")
@RequiredArgsConstructor
public class RefundApplyController {

    private final RefundService refundService;

    @PostMapping
    public R<Long> apply(@RequestBody Map<String, Object> apply) {
        return R.ok(refundService.apply(apply));
    }

    @PutMapping("/approval")
    @RequireRole(UserRole.STAFF)
    public R<Void> approval(@RequestBody Map<String, Object> body) {
        refundService.approval(body);
        return R.ok();
    }

    @GetMapping("/page")
    public R<List<Map<String, Object>>> page() {
        return R.ok(refundService.page());
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> getById(@PathVariable Long id) {
        return R.ok(refundService.getById(id));
    }
}