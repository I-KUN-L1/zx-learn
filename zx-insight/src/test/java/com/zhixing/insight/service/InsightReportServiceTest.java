package com.zhixing.insight.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.api.client.course.CourseClient;
import com.zhixing.api.client.learning.LearningClient;
import com.zhixing.api.client.trade.TradeClient;
import com.zhixing.api.client.user.UserClient;
import com.zhixing.api.dto.course.CourseSimpleInfoDTO;
import com.zhixing.api.dto.learning.DailyActiveDTO;
import com.zhixing.api.dto.trade.TradeStatsDTO;
import com.zhixing.insight.mapper.InsightReportMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 管理端看板聚合单测：多服务数据聚合 / 缺失日期补 0 / 下游不可用降级 / 课程名称映射。
 *
 * <p>回归背景：/insight/dashboard 曾仅返回学情报告统计（reportCount 等字段），
 * 与前端 DashboardVO（totalUsers/totalOrders/totalSales/totalCourses/orderTrend/activeTrend/hotCourses）
 * 完全不匹配，导致管理端数据看板全部显示 0、图表空白。
 */
@ExtendWith(MockitoExtension.class)
class InsightReportServiceTest {

    @Mock
    private InsightAggregateService aggregateService;
    @Mock
    private InsightAnalyzer analyzer;
    @Mock
    private InsightLlmClient llmClient;
    @Mock
    private InsightReportMapper reportMapper;
    @Mock
    private CourseClient courseClient;
    @Mock
    private UserClient userClient;
    @Mock
    private TradeClient tradeClient;
    @Mock
    private LearningClient learningClient;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private InsightReportService service;

    private CourseSimpleInfoDTO course(Long id, String name) {
        CourseSimpleInfoDTO c = new CourseSimpleInfoDTO();
        c.setId(id);
        c.setName(name);
        return c;
    }

    @Test
    void dashboardAggregatesAllSourcesToDashboardVOShape() {
        when(userClient.queryTotalUsers()).thenReturn(42L);
        when(tradeClient.queryTradeStats()).thenReturn(new TradeStatsDTO(
                3L, 180000L,
                List.of(new TradeStatsDTO.TrendPoint(LocalDate.now().toString(), 2L, 150000L)),
                List.of(new TradeStatsDTO.CourseCount(5L, 2L), new TradeStatsDTO.CourseCount(999L, 1L))));
        when(courseClient.queryAllSimpleInfo()).thenReturn(List.of(
                course(5L, "Spring 实战"), course(7L, "JVM 入门")));
        when(learningClient.queryDailyActive()).thenReturn(List.of(
                new DailyActiveDTO(LocalDate.now().toString(), 3L)));

        Map<String, Object> vo = service.dashboard();

        // 指标卡
        assertEquals(42L, vo.get("totalUsers"));
        assertEquals(3L, vo.get("totalOrders"));
        assertEquals(180000L, vo.get("totalSales"));
        assertEquals(2L, vo.get("totalCourses"));
        // 近 7 日订单趋势：固定 7 点，今日命中 2 单
        List<?> orderTrend = (List<?>) vo.get("orderTrend");
        assertEquals(7, orderTrend.size());
        Map<?, ?> todayOrder = (Map<?, ?>) orderTrend.get(6);
        assertEquals(LocalDate.now().toString(), todayOrder.get("date"));
        assertEquals(2L, todayOrder.get("count"));
        assertEquals(150000L, todayOrder.get("amount"));
        // 近 7 日活跃趋势：今日 3 人
        List<?> activeTrend = (List<?>) vo.get("activeTrend");
        assertEquals(7, activeTrend.size());
        assertEquals(3L, ((Map<?, ?>) activeTrend.get(6)).get("count"));
        // 热门课程：名称映射成功 + 不存在课程降级为"课程 #id"
        List<?> hot = (List<?>) vo.get("hotCourses");
        assertEquals(2, hot.size());
        assertEquals("Spring 实战", ((Map<?, ?>) hot.get(0)).get("name"));
        assertEquals(2L, ((Map<?, ?>) hot.get(0)).get("count"));
        assertEquals("课程 #999", ((Map<?, ?>) hot.get(1)).get("name"));
    }

    @Test
    void dashboardFillsMissingTrendDaysWithZero() {
        // 下游仅返回今日一个趋势点 / 空日活：其余 6 天必须补 0，不得出现 NPE 或缺日期
        when(userClient.queryTotalUsers()).thenReturn(0L);
        when(tradeClient.queryTradeStats()).thenReturn(new TradeStatsDTO(
                1L, 100L,
                List.of(new TradeStatsDTO.TrendPoint(LocalDate.now().toString(), 1L, 100L)),
                List.of()));
        when(courseClient.queryAllSimpleInfo()).thenReturn(List.of());
        when(learningClient.queryDailyActive()).thenReturn(List.of());

        Map<String, Object> vo = service.dashboard();

        List<?> orderTrend = (List<?>) vo.get("orderTrend");
        assertEquals(7, orderTrend.size());
        // 升序：第 6 位为昨日，应为补 0 点
        Map<?, ?> yesterday = (Map<?, ?>) orderTrend.get(5);
        assertEquals(LocalDate.now().minusDays(1).toString(), yesterday.get("date"));
        assertEquals(0L, yesterday.get("count"));
        assertEquals(0L, yesterday.get("amount"));
        List<?> activeTrend = (List<?>) vo.get("activeTrend");
        assertEquals(7, activeTrend.size());
        activeTrend.forEach(d -> assertEquals(0L, ((Map<?, ?>) d).get("count")));
        assertTrue(((List<?>) vo.get("hotCourses")).isEmpty());
    }

    @Test
    void dashboardDegradesGracefullyWhenDownstreamUnavailable() {
        // 任一下游服务不可用：看板仍可渲染，指标降级为 0、趋势补 0、热门为空
        when(userClient.queryTotalUsers()).thenThrow(new RuntimeException("user-service down"));
        when(tradeClient.queryTradeStats()).thenThrow(new RuntimeException("trade-service down"));
        when(courseClient.queryAllSimpleInfo()).thenThrow(new RuntimeException("course-service down"));
        when(learningClient.queryDailyActive()).thenThrow(new RuntimeException("learning-service down"));

        Map<String, Object> vo = service.dashboard();

        assertEquals(0L, vo.get("totalUsers"));
        assertEquals(0L, vo.get("totalOrders"));
        assertEquals(0L, vo.get("totalSales"));
        assertEquals(0L, vo.get("totalCourses"));
        assertEquals(7, ((List<?>) vo.get("orderTrend")).size());
        assertEquals(7, ((List<?>) vo.get("activeTrend")).size());
        assertTrue(((List<?>) vo.get("hotCourses")).isEmpty());
    }
}
