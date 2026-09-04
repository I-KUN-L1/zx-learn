package com.zhixing.insight.service;

import com.zhixing.insight.config.InsightLlmProperties;
import com.zhixing.insight.domain.dto.UserLearningStatsDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学情规则引擎：基于聚合数据计算多维度能力评分、识别薄弱点、生成学习建议。
 * 不依赖外部 LLM，保证离线可运行。
 */
@Service
@RequiredArgsConstructor
public class InsightAnalyzer {

    private final InsightLlmProperties properties;

    /**
     * 计算多维度能力评分（0-100）
     */
    public Map<String, Integer> analyzeDimensions(UserLearningStatsDTO stats) {
        Map<String, Integer> dims = new LinkedHashMap<>();
        // 学习投入度：时长权重 60% + 课程数权重 40%
        int durationScore = scoreByRange(stats.getTotalDuration(), 3600, 7200, 28800);
        int courseScore = scoreByRange(stats.getCourseCount(), 1, 2, 5);
        dims.put("学习投入度", Math.min(100, (int) Math.round(durationScore * 0.6 + courseScore * 0.4)));
        // 学习完成度：平均进度 + 完成课程数加成
        dims.put("学习完成度", Math.min(100, stats.getAvgProgress()
                + Math.min(20, stats.getFinishedCourseCount() * 5)));
        // 答题能力：正确率
        dims.put("答题能力", (int) Math.round(stats.getQuizAccuracy()));
        // 知识广度：学习课程数
        dims.put("知识广度", Math.min(100, courseScore));
        // 综合理解力：答题能力 50% + 完成度 50%
        int comprehension = (int) Math.round(dims.get("答题能力") * 0.5 + dims.get("学习完成度") * 0.5);
        dims.put("综合理解力", comprehension);
        return dims;
    }

    /**
     * 识别薄弱点
     */
    public List<String> detectWeakness(UserLearningStatsDTO stats) {
        List<String> weakness = new ArrayList<>();
        InsightLlmProperties.Insight cfg = properties.getInsight();
        if (stats.getQuizCount() > 0 && stats.getQuizAccuracy() < cfg.getWeakAccuracy()) {
            weakness.add("答题正确率仅 " + stats.getQuizAccuracy() + "%，基础知识点掌握不牢");
        }
        if (stats.getCourseCount() > 0 && stats.getAvgProgress() < cfg.getWeakProgress()) {
            weakness.add("课程平均进度仅 " + stats.getAvgProgress() + "%，学习持续性不足");
        }
        if (stats.getCourseCount() < 2) {
            weakness.add("学习课程偏少，知识面有待拓展");
        }
        if (stats.getTotalDuration() < 3600) {
            weakness.add("累计学习时长不足 1 小时，投入度偏低");
        }
        if (weakness.isEmpty()) {
            weakness.add("当前暂无明显薄弱点，建议保持学习节奏并适度挑战进阶内容");
        }
        return weakness;
    }

    /**
     * 生成学习建议
     */
    public List<String> generateSuggestions(UserLearningStatsDTO stats) {
        List<String> suggestions = new ArrayList<>();
        if (stats.getQuizCount() > 0 && stats.getQuizAccuracy() < properties.getInsight().getWeakAccuracy()) {
            suggestions.add("针对做错的题目重做并整理错题本，重点复习薄弱知识点");
        }
        if (stats.getCourseCount() > 0 && stats.getAvgProgress() < properties.getInsight().getWeakProgress()) {
            suggestions.add("优先完成已购课程的学习，建议每日安排固定 30 分钟学习时间");
        }
        if (stats.getCourseCount() < 2) {
            suggestions.add("可尝试拓展 1-2 门与当前方向相关的进阶课程，提升知识广度");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("继续保持当前学习节奏，可尝试参加章节测验检验学习效果");
        }
        return suggestions;
    }

    /**
     * 生成学习等级
     */
    public String levelOf(Map<String, Integer> dims) {
        int avg = dims.values().stream().mapToInt(Integer::intValue).sum() / dims.size();
        if (avg >= 85) {
            return "卓越";
        }
        if (avg >= 70) {
            return "优秀";
        }
        if (avg >= 55) {
            return "良好";
        }
        if (avg >= 40) {
            return "一般";
        }
        return "入门";
    }

    /**
     * 分段评分：低于 low 记 20 分，低于 mid 记 50，低于 high 记 80，否则 100
     */
    private int scoreByRange(long value, long low, long mid, long high) {
        if (value < low) {
            return 20;
        }
        if (value < mid) {
            return 50;
        }
        if (value < high) {
            return 80;
        }
        return 100;
    }
}
