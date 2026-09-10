package com.zhixing.insight.controller;

import com.zhixing.common.annotation.NoWrapper;
import com.zhixing.common.annotation.RequireRole;
import com.zhixing.common.constants.UserRole;
import com.zhixing.common.domain.R;
import com.zhixing.common.utils.InternalOnlyGuard;
import com.zhixing.common.utils.UserContext;
import com.zhixing.insight.domain.dto.ProfileVO;
import com.zhixing.insight.domain.dto.RecommendVO;
import com.zhixing.insight.domain.dto.ReportVO;
import com.zhixing.insight.service.InsightReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 智能学情分析接口。
 * 权限：个人学情（报告/画像/路径推荐）为学员端行为，仅学员(2)；
 * 全局看板为管理端数据，仅员工(1)；标注 @NoWrapper 的接口为内部 Feign 调用，不做角色限制。
 */
@RestController
@RequestMapping("/insight")
@RequiredArgsConstructor
public class InsightController {

    private final InsightReportService reportService;

    /**
     * 生成我的学情报告
     */
    @PostMapping("/reports/generate")
    @RequireRole(UserRole.STUDENT)
    public R<Long> generate() {
        return R.ok(reportService.generateReport(UserContext.getUser()));
    }

    /**
     * 内部：为指定用户生成学情报告（供其他服务触发；外部经网关访问一律 403）
     */
    @PostMapping("/reports/generate/{userId}")
    @NoWrapper
    public Long generateFor(@PathVariable Long userId) {
        InternalOnlyGuard.checkInternal();
        return reportService.generateReport(userId);
    }

    /**
     * 查询我的最新学情报告（无则自动生成）
     */
    @GetMapping("/reports/latest")
    @RequireRole(UserRole.STUDENT)
    public R<ReportVO> latest() {
        return R.ok(reportService.latestReport(UserContext.getUser()));
    }

    /**
     * 我的能力画像（雷达图）
     */
    @GetMapping("/profiles/mine")
    @RequireRole(UserRole.STUDENT)
    public R<ProfileVO> myProfile() {
        return R.ok(reportService.profile(UserContext.getUser()));
    }

    /**
     * 内部：指定用户能力画像（供其他服务调用；外部经网关访问一律 403）
     */
    @GetMapping("/profiles/{userId}")
    @NoWrapper
    public ProfileVO profileFor(@PathVariable Long userId) {
        InternalOnlyGuard.checkInternal();
        return reportService.profile(userId);
    }

    /**
     * 个性化学习路径推荐
     */
    @GetMapping("/learning-path")
    @RequireRole(UserRole.STUDENT)
    public R<RecommendVO> learningPath() {
        return R.ok(reportService.recommend(UserContext.getUser()));
    }

    /**
     * 全局学情看板（管理端数据看板）
     */
    @GetMapping("/dashboard")
    @RequireRole(UserRole.STAFF)
    public R<Map<String, Object>> dashboard() {
        return R.ok(reportService.dashboard());
    }
}
