package com.zhixing.insight.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.api.client.course.CourseClient;
import com.zhixing.api.client.learning.LearningClient;
import com.zhixing.api.client.trade.TradeClient;
import com.zhixing.api.client.user.UserClient;
import com.zhixing.api.dto.course.CourseSimpleInfoDTO;
import com.zhixing.api.dto.learning.DailyActiveDTO;
import com.zhixing.api.dto.trade.TradeStatsDTO;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 学情报告服务：聚合 → 分析 → 生成 → 持久化 → 缓存
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsightReportService {

    private static final String REPORT_CACHE_KEY = "insight:report:";
    /** v2：画像结构对齐前端 InsightProfileVO 后升级缓存 key，避免旧结构脏数据 */
    private static final String PROFILE_CACHE_KEY = "insight:profile:v2:";

    private final InsightAggregateService aggregateService;
    private final InsightAnalyzer analyzer;
    private final InsightLlmClient llmClient;
    private final InsightReportMapper reportMapper;
    private final CourseClient courseClient;
    private final UserClient userClient;
    private final TradeClient tradeClient;
    private final LearningClient learningClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 生成学情报告（已登录用户）
     * <p>
     * 健壮性约定：本方法及其调用方（latest / recommend）<b>不允许向上抛异常</b>。
     * 学情页面是只读展示，任何下游（Redis / 大模型 / 学习与答题服务）抖动都应降级为
     * "用规则结果继续渲染"，而不是把 500「系统繁忙」抛给前端（历史缺陷：
     * Redis 缓存读写未做保护，缓存不可用时整页报"系统繁忙"且无数据）。
     */
    public Long generateReport(Long userId) {
        InsightReport report = buildReport(userId);
        return report == null ? null : report.getId();
    }

    /**
     * 构建并持久化一份当日学情报告。
     *
     * @return 落库成功返回实体；聚合/落库失败返回 null（调用方降级为空报告）
     */
    private InsightReport buildReport(Long userId) {
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
        report.setWeakness(truncate(writeJson(weakness), 1024));
        report.setRecommendations(truncate(writeJson(suggestions), 2048));

        String llmSummary = llmClient.generateSummary(dims, weakness, suggestions);
        if (llmSummary != null && !llmSummary.isBlank()) {
            report.setSummary(truncate(llmSummary, 2048));
            report.setAiGenerated(true);
        } else {
            report.setSummary(truncate(buildRuleSummary(dims, weakness), 2048));
            report.setAiGenerated(false);
        }
        try {
            reportMapper.insert(report);
        } catch (Exception e) {
            // 落库失败不阻断展示：返回内存对象，前端仍能看到本次分析结果
            log.warn("学情报告落库失败，降级为内存结果：userId={}, err={}", userId, e.getMessage());
            return report;
        }

        // 刷新缓存（缓存失败不影响主流程）
        cacheSet(REPORT_CACHE_KEY + userId, String.valueOf(report.getId()), 12, TimeUnit.HOURS);
        cacheDel(PROFILE_CACHE_KEY + userId);
        log.info("学情报告生成完成 userId={}, reportId={}, aiGenerated={}",
                userId, report.getId(), report.getAiGenerated());
        return report;
    }

    /**
     * 查询我的最新报告（无则自动生成）。
     * <p>
     * 全链路降级：缓存不可用 → 直查数据库；数据库无当日报告 → 现场生成；
     * 生成失败 → 返回零值报告（前端渲染空态而不是"系统繁忙"）。
     */
    public ReportVO latestReport(Long userId) {
        Long cachedId = parseLong(cacheGet(REPORT_CACHE_KEY + userId));
        if (cachedId != null) {
            InsightReport report = safeSelectById(cachedId);
            if (report != null && report.getReportDate() != null
                    && !report.getReportDate().isBefore(LocalDate.now())) {
                return toVO(report);
            }
        }
        InsightReport latest = null;
        try {
            latest = reportMapper.selectOne(new LambdaQueryWrapper<InsightReport>()
                    .eq(InsightReport::getUserId, userId)
                    .orderByDesc(InsightReport::getReportDate)
                    .last("limit 1"));
        } catch (Exception e) {
            log.warn("查询最新学情报告失败，将现场生成：userId={}, err={}", userId, e.getMessage());
        }
        if (latest == null || latest.getReportDate() == null
                || !latest.getReportDate().equals(LocalDate.now())) {
            InsightReport generated = buildReport(userId);
            if (generated != null) {
                latest = generated;
            }
        }
        return latest == null ? emptyReportVO(userId) : toVO(latest);
    }

    /** 生成失败时的零值报告，保证前端契约完整、页面不空白也不报错 */
    private ReportVO emptyReportVO(Long userId) {
        ReportVO vo = new ReportVO();
        vo.setUserId(userId);
        vo.setReportDate(LocalDate.now());
        vo.setDimensions(Map.of(
                "学习投入度", 0,
                "学习完成度", 0,
                "答题能力", 0,
                "知识广度", 0,
                "综合理解力", 0));
        vo.setWeakness(List.of());
        vo.setRecommendations(List.of());
        vo.setSummary("学情数据正在汇总，请稍后刷新查看。");
        vo.setAiGenerated(false);
        return vo;
    }

    /**
     * 能力画像（对齐前端 InsightProfileVO 契约：总览指标 + 雷达图 + 近 7 日趋势）
     * <p>
     * 缓存读写全部降级处理：Redis 不可用时直接由聚合数据现算，不影响页面展示。
     */
    public ProfileVO profile(Long userId) {
        String cached = cacheGet(PROFILE_CACHE_KEY + userId);
        if (cached != null) {
            try {
                ProfileVO vo = objectMapper.readValue(cached, ProfileVO.class);
                if (vo != null && vo.getAbilities() != null && vo.getTrends() != null) {
                    return vo;
                }
            } catch (Exception ignored) {
                // 缓存解析失败忽略，走现算
            }
        }
        UserLearningStatsDTO stats = aggregateService.aggregate(userId);
        Map<String, Integer> dims = analyzer.analyzeDimensions(stats);
        ProfileVO vo = new ProfileVO();
        vo.setUserId(userId);
        // 累计学习时长（秒 → 分钟）
        vo.setTotalDuration(stats.getTotalDuration() / 60);
        // 课程完成率：完成课程数 / 学习课程数
        vo.setCompletedRate(stats.getCourseCount() == 0 ? 0
                : (int) Math.round(stats.getFinishedCourseCount() * 100.0 / stats.getCourseCount()));
        vo.setContinuousDays(stats.getSignStreak());
        vo.setAbilities(dims.entrySet().stream()
                .map(e -> new ProfileVO.AbilityDTO(e.getKey(), e.getValue()))
                .collect(Collectors.toList()));
        // 近 7 日趋势（升序，日期 M/d），秒 → 分钟
        List<ProfileVO.TrendDTO> trends = new ArrayList<>(7);
        for (int i = 6; i >= 0; i--) {
            LocalDate d = LocalDate.now().minusDays(i);
            long seconds = stats.getDailyDurations() == null ? 0L
                    : stats.getDailyDurations().getOrDefault(d.toString(), 0L);
            trends.add(new ProfileVO.TrendDTO(d.getMonthValue() + "/" + d.getDayOfMonth(), seconds / 60));
        }
        vo.setTrends(trends);
        try {
            cacheSet(PROFILE_CACHE_KEY + userId,
                    objectMapper.writeValueAsString(vo), 2, TimeUnit.HOURS);
        } catch (Exception ignored) {
            // 序列化/缓存写入失败不影响主流程
        }
        return vo;
    }

    /**
     * 个性化学习路径推荐（对齐前端 LearningPathVO 契约：reason + steps）
     */
    public RecommendVO recommend(Long userId) {
        UserLearningStatsDTO stats = aggregateService.aggregate(userId);
        Map<String, Integer> dims = analyzer.analyzeDimensions(stats);
        List<String> weakness = analyzer.detectWeakness(stats);
        List<String> suggestions = analyzer.generateSuggestions(stats);

        RecommendVO vo = new RecommendVO();
        // 只调用一次大模型；无结果时回退规则总结（原实现重复调用 generateSummary 两次，浪费并拖慢接口）
        String llm = llmClient.generateSummary(dims, weakness, suggestions);
        vo.setReason(llm != null ? llm : buildRuleSummary(dims, weakness));

        // 推荐未学习课程
        vo.setSteps(recommendSteps(stats));
        return vo;
    }

    /**
     * 全局学情看板（管理端数据看板）。
     * 聚合：用户总量（zx-user）、交易统计（zx-trade）、在售课程（zx-course）、近 7 日日活（zx-learning）。
     * 返回结构与前端 DashboardVO 对齐：totalUsers / totalOrders / totalSales / totalCourses /
     * orderTrend / activeTrend / hotCourses。
     * 任一下游服务不可用时优雅降级为 0/空，保证看板始终可渲染。
     */
    public Map<String, Object> dashboard() {
        // 1. 用户总量
        long totalUsers = 0;
        try {
            Long count = userClient.queryTotalUsers();
            totalUsers = count == null ? 0 : count;
        } catch (Exception e) {
            log.warn("看板用户总量拉取失败: {}", e.getMessage());
        }

        // 2. 交易统计（已支付订单量 / 销售额 / 近 7 日订单趋势 / 热门课程）
        TradeStatsDTO trade = null;
        try {
            trade = tradeClient.queryTradeStats();
        } catch (Exception e) {
            log.warn("看板交易统计拉取失败: {}", e.getMessage());
        }
        long totalOrders = trade == null || trade.getTotalOrders() == null ? 0 : trade.getTotalOrders();
        long totalSales = trade == null || trade.getTotalSales() == null ? 0 : trade.getTotalSales();
        Map<String, TradeStatsDTO.TrendPoint> tradeTrend = new HashMap<>();
        if (trade != null && trade.getOrderTrend() != null) {
            trade.getOrderTrend().forEach(p -> tradeTrend.put(p.getDate(), p));
        }

        // 3. 在售课程（总数 + 名称映射）
        long totalCourses = 0;
        Map<Long, String> courseNames = new HashMap<>();
        try {
            List<CourseSimpleInfoDTO> courses = courseClient.queryAllSimpleInfo();
            if (courses != null) {
                totalCourses = courses.size();
                courses.forEach(c -> courseNames.put(c.getId(), c.getName()));
            }
        } catch (Exception e) {
            log.warn("看板在售课程拉取失败: {}", e.getMessage());
        }

        // 4. 近 7 日订单趋势（缺失日期补 0）
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> orderTrend = new ArrayList<>(7);
        for (int i = 6; i >= 0; i--) {
            String date = today.minusDays(i).toString();
            TradeStatsDTO.TrendPoint p = tradeTrend.get(date);
            orderTrend.add(Map.of(
                    "date", date,
                    "count", p == null || p.getCount() == null ? 0L : p.getCount(),
                    "amount", p == null || p.getAmount() == null ? 0L : p.getAmount()));
        }

        // 5. 近 7 日活跃趋势（缺失日期补 0）
        Map<String, Long> activeByDate = new HashMap<>();
        try {
            List<DailyActiveDTO> active = learningClient.queryDailyActive();
            if (active != null) {
                active.forEach(a -> activeByDate.put(a.getDate(), a.getCount() == null ? 0L : a.getCount()));
            }
        } catch (Exception e) {
            log.warn("看板日活统计拉取失败: {}", e.getMessage());
        }
        List<Map<String, Object>> activeTrend = new ArrayList<>(7);
        for (int i = 6; i >= 0; i--) {
            String date = today.minusDays(i).toString();
            activeTrend.add(Map.of("date", date, "count", activeByDate.getOrDefault(date, 0L)));
        }

        // 6. 热门课程 TOP5（id → 名称，课程已下架/不存在时降级为"课程 #id"）
        List<Map<String, Object>> hotCourses = new ArrayList<>();
        if (trade != null && trade.getHotCourses() != null) {
            for (TradeStatsDTO.CourseCount c : trade.getHotCourses()) {
                String name = courseNames.getOrDefault(c.getCourseId(), "课程 #" + c.getCourseId());
                hotCourses.add(Map.of("name", name, "count", c.getCount() == null ? 0L : c.getCount()));
            }
        }

        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("totalUsers", totalUsers);
        vo.put("totalOrders", totalOrders);
        vo.put("totalSales", totalSales);
        vo.put("totalCourses", totalCourses);
        vo.put("orderTrend", orderTrend);
        vo.put("activeTrend", activeTrend);
        vo.put("hotCourses", hotCourses);
        return vo;
    }

    // ============ 私有方法 ============

    /**
     * 推荐未学习课程为学习路径步骤（最多 3 步），课程服务不可用时降级返回空路径。
     */
    private List<RecommendVO.StepDTO> recommendSteps(UserLearningStatsDTO stats) {
        List<RecommendVO.StepDTO> steps = new ArrayList<>();
        List<Long> studied = stats.getCourseIds() == null ? List.of() : stats.getCourseIds();
        try {
            List<CourseSimpleInfoDTO> all = courseClient.queryAllSimpleInfo();
            List<String> reasons = List.of(
                    "结合当前薄弱点，建议优先补充该课程夯实基础",
                    "该课程与你的学习方向匹配度较高",
                    "新课上架，适合拓展知识广度");
            int idx = 0;
            for (CourseSimpleInfoDTO c : all) {
                if (steps.size() >= 3) {
                    break;
                }
                if (studied.contains(c.getId())) {
                    continue;
                }
                RecommendVO.StepDTO step = new RecommendVO.StepDTO();
                step.setOrder(steps.size() + 1);
                step.setCourseId(c.getId());
                step.setCourseName(c.getName());
                step.setReason(reasons.get(idx % reasons.size()));
                steps.add(step);
                idx++;
            }
        } catch (Exception e) {
            log.warn("推荐课程拉取失败 userId={}: {}", stats.getUserId(), e.getMessage());
        }
        return steps;
    }

    private String buildRuleSummary(Map<String, Integer> dims, List<String> weakness) {
        return "综合来看，你的学习投入度 " + dims.getOrDefault("学习投入度", 0)
                + " 分、答题能力 " + dims.getOrDefault("答题能力", 0)
                + " 分。主要薄弱点：" + String.join("；", weakness)
                + "。建议保持每日固定学习节奏，按推荐路径循序渐进。";
    }

    /* ==================== 缓存与健壮性工具（全部 fail-open） ==================== */

    /**
     * 读取缓存：Redis 不可用/超时/序列化异常时返回 null，由调用方走数据库或现算。
     * 学情页面为只读展示，缓存属于纯加速项，任何缓存异常都不应升级为接口 500。
     */
    private String cacheGet(String key) {
        try {
            return stringRedisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("学情缓存读取失败（降级直查数据库）：key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    /** 写入缓存：失败仅记日志，不影响主流程 */
    private void cacheSet(String key, String value, long timeout, TimeUnit unit) {
        try {
            stringRedisTemplate.opsForValue().set(key, value, timeout, unit);
        } catch (Exception e) {
            log.warn("学情缓存写入失败（忽略）：key={}, err={}", key, e.getMessage());
        }
    }

    /** 删除缓存：失败仅记日志 */
    private void cacheDel(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("学情缓存删除失败（忽略）：key={}, err={}", key, e.getMessage());
        }
    }

    private Long parseLong(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private InsightReport safeSelectById(Long id) {
        try {
            return reportMapper.selectById(id);
        } catch (Exception e) {
            log.warn("学情报告按 id 查询失败：id={}, err={}", id, e.getMessage());
            return null;
        }
    }

    /** 按数据库列长度截断，避免超长文本触发 DataIntegrityViolation → 500「系统繁忙」 */
    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
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
