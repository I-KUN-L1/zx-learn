package com.zhixing.api.client.exam;

import com.zhixing.api.dto.exam.QuestionResultDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 考试服务客户端
 */
@FeignClient(value = "exam-service", contextId = "examClient")
public interface ExamClient {

    @GetMapping("/questions/list")
    List<Object> queryQuestionList(@RequestParam("ids") List<Long> ids);

    @GetMapping("/questions/scores")
    Map<Long, Integer> queryQuestionScores(@RequestParam("ids") List<Long> ids);

    /**
     * 查询指定用户全部答题记录
     */
    @GetMapping("/question-results/users/{userId}/all")
    List<QuestionResultDTO> listResults(@PathVariable("userId") Long userId);

    /**
     * 查询指定用户答题统计
     */
    @GetMapping("/question-results/users/{userId}/stats")
    Map<String, Object> statsByUser(@PathVariable("userId") Long userId);
}
