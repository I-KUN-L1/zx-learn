package com.zhixing.learning.controller;

import com.zhixing.api.dto.learning.LessonEnrollDTO;
import com.zhixing.common.annotation.NoWrapper;
import com.zhixing.common.utils.InternalOnlyGuard;
import com.zhixing.learning.service.LessonService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 课表内部调用接口（Feign 直连，不包装）。
 * <p>
 * 权限：仅限服务间调用（交易服务在支付成功后同步开课），外部用户经网关访问一律 403
 * （见 {@link InternalOnlyGuard}）。
 */
@RestController
@RequestMapping("/lessons/internal")
@RequiredArgsConstructor
public class LessonInternalController {

    private final LessonService lessonService;

    /**
     * 同步开课：写入课表（幂等，uk_user_course 唯一索引兜底）。
     * <p>
     * 与 MQ「订单支付成功」事件双通道：同步调用保证课表<b>立即</b>可见，
     * MQ 事件作为同步失败时的最终一致兜底，两条通道都幂等，重复执行无副作用。
     */
    @PostMapping("/enroll")
    @NoWrapper
    public void enroll(@RequestBody LessonEnrollDTO dto) {
        InternalOnlyGuard.checkInternal();
        if (dto == null || dto.getUserId() == null || dto.getCourseId() == null) {
            return;
        }
        lessonService.enroll(dto.getUserId(), dto.getCourseId(), dto.getCourseName());
    }
}
