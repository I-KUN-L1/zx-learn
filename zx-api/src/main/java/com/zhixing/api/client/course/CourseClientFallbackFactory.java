package com.zhixing.api.client.course;

import com.zhixing.api.dto.course.CourseSimpleInfoDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 课程服务降级工厂。
 * <p>
 * 降级策略（fail-safe）：
 * <ul>
 *   <li>单课程查询 {@code queryCourseInfoById} 返回 {@code null}：下单金额校验等调用方拿到 null 会判定"课程不可用"，拒绝下单或按业务兜底，避免用不可靠价格/数据落库；</li>
 *   <li>批量/罗列查询返回空集合，调用方按"无数据"处理，避免级联失败。</li>
 * </ul>
 * 以日志告警，便于监控课程服务是否异常。
 */
@Slf4j
@Component
public class CourseClientFallbackFactory implements FallbackFactory<CourseClient> {

    @Override
    public CourseClient create(Throwable cause) {
        log.error("course-service 调用失败，已降级：", cause);
        return new CourseClient() {
            @Override
            public List<CourseSimpleInfoDTO> queryCourseSimpleInfoList(List<Long> ids) {
                return List.of();
            }

            @Override
            public CourseSimpleInfoDTO queryCourseInfoById(Long id) {
                log.warn("course-service 不可用，queryCourseInfoById({}) 降级返回 null", id);
                return null;
            }

            @Override
            public List<Long> queryCourseIdByName(String name) {
                return List.of();
            }

            @Override
            public List<CourseSimpleInfoDTO> queryAllSimpleInfo() {
                return List.of();
            }
        };
    }
}