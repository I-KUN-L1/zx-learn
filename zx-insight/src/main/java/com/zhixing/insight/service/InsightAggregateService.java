package com.zhixing.insight.service;

import com.zhixing.api.client.exam.ExamClient;
import com.zhixing.api.client.learning.LearningClient;
import com.zhixing.api.dto.learning.LearningRecordDTO;
import com.zhixing.insight.domain.dto.UserLearningStatsDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 学习数据聚合服务：跨服务拉取学生学习/答题数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsightAggregateService {

    private final LearningClient learningClient;
    private final ExamClient examClient;

    /**
     * 聚合指定用户的学习数据
     */
    public UserLearningStatsDTO aggregate(Long userId) {
        UserLearningStatsDTO stats = new UserLearningStatsDTO();
        stats.setUserId(userId);

        // 学习记录
        List<LearningRecordDTO> records = safeLearningRecords(userId);
        long totalDuration = 0;
        int courseCount = 0;
        int finished = 0;
        int progressSum = 0;
        for (LearningRecordDTO r : records) {
            totalDuration += r.getLearnDuration() == null ? 0 : r.getLearnDuration();
            if (Boolean.TRUE.equals(r.getFinished())) {
                finished++;
            }
            progressSum += r.getProgress() == null ? 0 : r.getProgress();
        }
        courseCount = (int) records.stream().map(LearningRecordDTO::getCourseId).distinct().count();
        stats.setTotalDuration(totalDuration);
        stats.setCourseCount(courseCount);
        stats.setFinishedCourseCount(finished);
        stats.setAvgProgress(records.isEmpty() ? 0 : Math.min(100, progressSum / records.size()));
        stats.setCourseIds(records.stream().map(LearningRecordDTO::getCourseId).distinct().toList());

        // 答题统计
        Map<String, Object> quizStats = safeQuizStats(userId);
        long count = ((Number) quizStats.getOrDefault("count", 0)).longValue();
        long correct = ((Number) quizStats.getOrDefault("correct", 0)).longValue();
        stats.setQuizCount(count);
        stats.setQuizCorrect(correct);
        double accuracy = count == 0 ? 0 : Math.round(correct * 1000.0 / count) / 10.0;
        stats.setQuizAccuracy(accuracy);

        // 活跃天数：每 5 条学习记录估算 1 个活跃天
        stats.setActiveDays(records.isEmpty() ? 0 : Math.max(1, records.size() / 5));

        log.debug("学情聚合完成 userId={}, 时长={}s, 课程={}, 答题={}, 正确率={}%",
                userId, totalDuration, courseCount, count, accuracy);
        return stats;
    }

    private List<LearningRecordDTO> safeLearningRecords(Long userId) {
        try {
            List<LearningRecordDTO> list = learningClient.listRecords(userId);
            return list == null ? List.of() : list;
        } catch (Exception e) {
            log.warn("拉取学习记录失败 userId={}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> safeQuizStats(Long userId) {
        try {
            return examClient.statsByUser(userId);
        } catch (Exception e) {
            log.warn("拉取答题统计失败 userId={}: {}", userId, e.getMessage());
            return Map.of("count", 0, "correct", 0, "accuracy", 0.0);
        }
    }
}
