package com.zhixing.api.client.learning;

import com.zhixing.api.dto.learning.LearningRecordDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * 学习服务客户端
 */
@FeignClient(value = "learning-service", contextId = "learningClient")
public interface LearningClient {

    /**
     * 查询指定用户全部学习记录
     */
    @GetMapping("/learning-records/users/{userId}/all")
    List<LearningRecordDTO> listRecords(@PathVariable("userId") Long userId);

    /**
     * 查询指定用户学习总时长（秒）
     */
    @GetMapping("/learning-records/users/{userId}/sum")
    Long sumDuration(@PathVariable("userId") Long userId);
}
