package com.zhixing.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixing.common.utils.AssertUtils;
import com.zhixing.common.utils.UserContext;
import com.zhixing.exam.domain.po.QuestionResult;
import com.zhixing.exam.mapper.QuestionResultMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 答题记录服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionResultService {

    private final QuestionResultMapper resultMapper;

    /**
     * 提交答题结果
     */
    public Long submit(QuestionResult result) {
        AssertUtils.notNull(result, "答题结果不能为空");
        AssertUtils.notNull(result.getQuestionId(), "题目 id 不能为空");
        Long userId = UserContext.getUser();
        result.setUserId(userId);
        resultMapper.insert(result);
        log.info("答题记录：userId={}, questionId={}, correct={}", userId, result.getQuestionId(), result.getCorrect());
        return result.getId();
    }

    /**
     * 查询指定用户全部答题记录（内部，供学情分析聚合）
     */
    public List<QuestionResult> listByUser(Long userId) {
        return resultMapper.selectList(new LambdaQueryWrapper<QuestionResult>()
                .eq(QuestionResult::getUserId, userId)
                .orderByDesc(QuestionResult::getCreateTime));
    }

    /**
     * 统计指定用户答题情况：{count, correct, accuracy}
     */
    public Map<String, Object> statsByUser(Long userId) {
        List<QuestionResult> list = listByUser(userId);
        long count = list.size();
        long correct = list.stream().filter(r -> Boolean.TRUE.equals(r.getCorrect())).count();
        double accuracy = count == 0 ? 0 : Math.round(correct * 1000.0 / count) / 10.0;
        return Map.of("count", count, "correct", correct, "accuracy", accuracy);
    }
}
