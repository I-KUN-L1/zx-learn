package com.zhixing.insight.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.api.client.course.CourseClient;
import com.zhixing.api.dto.course.CourseSimpleInfoDTO;
import com.zhixing.insight.domain.dto.ProfileVO;
import com.zhixing.insight.domain.dto.RecommendVO;
import com.zhixing.insight.domain.dto.ReportVO;
import com.zhixing.insight.domain.dto.UserLearningStatsDTO;
import com.zhixing.insight.domain.po.InsightReport;
import com.zhixing.insight.mapper.InsightReportMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 学情报告服务：聚合 → 分析 → 生成 → 持久化 → 缓存
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsightReportService {

    private static final String REPORT_CACHE_KEY = "insight:report:";
    private static final String PROFILE_CACHE_KEY = "insight:profile:";

    private final InsightAggregateService aggregateService;
    private final InsightAnalyzer analyzer;
    private final InsightLlmClient llmClient;
    private final InsightReportMapper reportMapper;
    private final CourseClient courseClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 生成学情报告（已登录用户）
     */
    public Long generateReport(Long userId) {
        UserLearningStatsDTO stats = aggregateService.aggregate(userId);
        Map<String, Integer> dims = analyzer.analyzeDimensions(stats);
        List<String> weakness = analyzer.detectWeakness(stats);
        List<String> suggestions = analyzer.generateSuggestions(stats);

        InsightReport report = new InsightReport();
        report.setUserId(userId);
        report.setReportDate(LocalDate.now());
        report.setEngagement(dims.get("学习投入度"));
        report.setCompletion(dims.get("学习完成度"));
        report.setQuizAbility(dims.get("答题能力"));
        report.setBreadth(dims.get("知识广度"));
        report.setComprehension(dims.get("综合理解力"));
        report.setWeakness(writeJson(weakness));
        report.setRecommendations(writeJson(suggestions));

        String llmSummary = llmClient.generateSummary(dims, weakness, suggestions);
        if (llmSummary != null) {
            report.setSummary(llmSummary);
            report.setAiGenerated(true);
        } else {
            report.setSummary(buildRuleSummary(dims, weakness));
            report.setAiGenerated(false);
        }
        reportMapper.insert(report);

        // 刷新缓存
        stringRedisTemplate.opsForValue().set(REPORT_CACHE_KEY + userId, String.valueOf(report.getId()), 12, TimeUnit.HOURS);
        stringRedisTemplate.delete(PROFILE_CACHE_KEY + userId);
        log.info("学情报告生成完成 userId={}, reportId={}, aiGenerated={}", userId, report.getId(), report.getAiGenerated());
        return report.getId();
    }

    /**
     * 查询我的最新报告（无则自动生成）
     */
    public ReportVO latestReport(Long userId) {
        String cached = stringRedisTemplate.opsForValue().get(REPORT_CACHE_KEY + userId);
        if (cached != null) {
            InsightReport report = reportMapper.selectById(Long.valueOf(cached));
            if (report != null && !report.getReportDate().isBefore(LocalDate.now())) {
                return toVO(report);
            }
        }
        InsightReport latest = reportMapper.selectOne(new LambdaQueryWrapper<InsightReport>()
                .eq(InsightReport::getUserId, userId)
                .orderByDesc(InsightReport::getReportDate)
                .last("limit 1"));
        if (latest == null || !latest.getReportDate().equals(LocalDate.now())) {
            Long id = generateReport(userId);
            latest = reportMapper.selectById(id);
        }
        return toVO(latest);
    }

    /**
     * 能力画像（雷达图）
     */
    public ProfileVO profile(Long userId) {
        String cached = stringRedisTemplate.opsForValue().get(PROFILE_CACHE_KEY + userId);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, ProfileVO.class);
            } catch (Exception ignored) {
                // 缓存解析失败忽略
            }
        }
        UserLearningStatsDTO stats = aggregateService.aggregate(userId);
        Map<String, Integer> dims = analyzer.analyzeDimensions(stats);
        ProfileVO vo = new ProfileVO();
        vo.setUserId(userId);
        vo.setDimensions(dims);
        vo.setLevel(analyzer.levelOf(dims));
        try {
            stringRedisTemplate.opsForValue().set(PROFILE_CACHE_KEY + userId,
                    objectMapper.writeValueAsString(vo), 2, TimeUnit.HOURS);
        } catch (Exception ignored) {
            // 缓存写入失败不影响主流程
        }
        return vo;
    }

    /**
     * 个性化学习路径推荐
     */
    public RecommendVO recommend(Long userId) {
        UserLearningStatsDTO stats = aggregateService.aggregate(userId);
        Map<String, Integer> dims = analyzer.analyzeDimensions(stats);
        List<String> weakness = analyzer.detectWeakness(stats);
        List<String> suggestions = analyzer.generateSuggestions(stats);

        RecommendVO vo = new RecommendVO();
        vo.setUserId(userId);
        vo.setWeakness(weakness);
        vo.setSuggestions(suggestions);
        vo.setSummary(llmClient.generateSummary(dims, weakness, suggestions) != null
                ? llmClient.generateSummary(dims, weakness, suggestions)
                : buildRuleSummary(dims, weakness));

        // 推荐未学习课程
        vo.setCourses(recommendCourses(stats));
        return vo;
    }

    /**
     * 全局学情看板
     */
    public Map<String, Object> dashboard() {
        List<InsightReport> reports = reportMapper.selectList(
                new LambdaQueryWrapper<InsightReport>().eq(InsightReport::getReportDate, LocalDate.now()));
        long count = reports.size();
        double avgComprehension = reports.isEmpty() ? 0
                : reports.stream().mapToInt(r -> nvl(r.getComprehension())).average().orElse(0);
        double avgQuiz = reports.isEmpty() ? 0
                : reports.stream().mapToInt(r -> nvl(r.getQuizAbility())).average().orElse(0);
        long aiCount = reports.stream().filter(r -> Boolean.TRUE.equals(r.getAiGenerated())).count();
        return Map.of(
                "reportCount", count,
                "avgComprehension", Math.round(avgComprehension * 10) / 10.0,
                "avgQuizAbility", Math.round(avgQuiz * 10) / 10.0,
                "aiGeneratedCount", aiCount,
                "date", LocalDate.now().toString());
    }

    // ============ 私有方法 ============

    private List<RecommendVO.CourseRecommendDTO> recommendCourses(UserLearningStatsDTO stats) {
        List<RecommendVO.CourseRecommendDTO> result = new ArrayList<>();
        List<Long> studied = stats.getCourseIds() == null ? List.of() : stats.getCourseIds();
        try {
            List<CourseSimpleInfoDTO> all = courseClient.queryAllSimpleInfo();
            List<String> reasons = List.of(
                    "结合当前薄弱点，建议优先补充该课程夯实基础",
                    "该课程与你的学习方向匹配度较高",
                    "新课上架，适合拓展知识广度");
            int idx = 0;
            for (CourseSimpleInfoDTO c : all) {
                if (result.size() >= 3) {
                    break;
                }
                if (studied.contains(c.getId())) {
                    continue;
                }
                RecommendVO.CourseRecommendDTO dto = new RecommendVO.CourseRecommendDTO();
                dto.setId(c.getId());
                dto.setName(c.getName());
                dto.setPrice(c.getPrice());
                dto.setReason(reasons.get(idx % reasons.size()));
                result.add(dto);
                idx++;
            }
        } catch (Exception e) {
            log.warn("推荐课程拉取失败 userId={}: {}", stats.getUserId(), e.getMessage());
        }
        return result;
    }

    private String buildRuleSummary(Map<String, Integer> dims, List<String> weakness) {
        return "综合来看，你的学习投入度 " + dims.getOrDefault("学习投入度", 0)
                + " 分、答题能力 " + dims.getOrDefault("答题能力", 0)
                + " 分。主要薄弱点：" + String.join("；", weakness)
                + "。建议保持每日固定学习节奏，按推荐路径循序渐进。";
    }

    private ReportVO toVO(InsightReport r) {
        ReportVO vo = new ReportVO();
        vo.setId(r.getId());
        vo.setUserId(r.getUserId());
        vo.setReportDate(r.getReportDate());
        vo.setDimensions(Map.of(
                "学习投入度", nvl(r.getEngagement()),
                "学习完成度", nvl(r.getCompletion()),
                "答题能力", nvl(r.getQuizAbility()),
                "知识广度", nvl(r.getBreadth()),
                "综合理解力", nvl(r.getComprehension())));
        vo.setWeakness(readJsonList(r.getWeakness()));
        vo.setRecommendations(readJsonList(r.getRecommendations()));
        vo.setSummary(r.getSummary());
        vo.setAiGenerated(r.getAiGenerated());
        return vo;
    }

    private int nvl(Integer v) {
        return v == null ? 0 : v;
    }

    private String writeJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> readJsonList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }
}
