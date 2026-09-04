package com.zhixing.insight.controller;

import com.zhixing.common.annotation.NoWrapper;
import com.zhixing.common.domain.R;
import com.zhixing.common.utils.UserContext;
import com.zhixing.insight.domain.dto.ProfileVO;
import com.zhixing.insight.domain.dto.RecommendVO;
import com.zhixing.insight.domain.dto.ReportVO;
import com.zhixing.insight.service.InsightReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 智能学情分析接口
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
    public R<Long> generate() {
        return R.ok(reportService.generateReport(UserContext.getUser()));
    }

    /**
     * 内部：为指定用户生成学情报告（供其他服务触发）
     */
    @PostMapping("/reports/generate/{userId}")
    @NoWrapper
    public Long generateFor(@PathVariable Long userId) {
        return reportService.generateReport(userId);
    }

    /**
     * 查询我的最新学情报告（无则自动生成）
     */
    @GetMapping("/reports/latest")
    public R<ReportVO> latest() {
        return R.ok(reportService.latestReport(UserContext.getUser()));
    }

    /**
     * 我的能力画像（雷达图）
     */
    @GetMapping("/profiles/mine")
    public R<ProfileVO> myProfile() {
        return R.ok(reportService.profile(UserContext.getUser()));
    }

    /**
     * 内部：指定用户能力画像（供其他服务调用）
     */
    @GetMapping("/profiles/{userId}")
    @NoWrapper
    public ProfileVO profileFor(@PathVariable Long userId) {
        return reportService.profile(userId);
    }

    /**
     * 个性化学习路径推荐
     */
    @GetMapping("/learning-path")
    public R<RecommendVO> learningPath() {
        return R.ok(reportService.recommend(UserContext.getUser()));
    }

    /**
     * 全局学情看板
     */
    @GetMapping("/dashboard")
    public R<Map<String, Object>> dashboard() {
        return R.ok(reportService.dashboard());
    }
}
