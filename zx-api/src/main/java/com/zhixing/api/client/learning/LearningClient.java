package com.zhixing.api.client.learning;

import com.zhixing.api.dto.learning.DailyActiveDTO;
import com.zhixing.api.dto.learning.LearningRecordDTO;
import com.zhixing.api.dto.learning.LessonEnrollDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

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

    /**
     * 近 7 日日活统计（按学习记录去重用户数）
     */
    @GetMapping("/learning-records/stats/active")
    List<DailyActiveDTO> queryDailyActive();

    /**
     * 查询指定用户当前连续签到天数
     */
    @GetMapping("/sign-ins/users/{userId}/streak")
    Integer userSignStreak(@PathVariable("userId") Long userId);

    /**
     * 查询指定用户课程中心（课表）课程 id 集合。
     * 「已拥有」权威口径：课表存在即拥有（含学习进度 0、退款后未清课表）。
     */
    @GetMapping("/lessons/users/{userId}/course-ids")
    List<Long> listLessonCourseIds(@PathVariable("userId") Long userId);

    /**
     * 同步开课（写入我的课表）。
     * <p>
     * 订单支付成功 / 免费课直接到账后由交易服务同步调用，使课表<b>立即</b>出现该课程，
     * 保证「课程界面」的已拥有状态与「我的课表」实时一致；MQ 事件仍保留作为兜底。
     * 幂等：学习服务侧靠 lesson.uk_user_course 唯一索引去重。
     */
    @PostMapping("/lessons/internal/enroll")
    void enrollLesson(@RequestBody LessonEnrollDTO dto);
}
