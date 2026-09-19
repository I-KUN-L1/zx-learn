package com.zhixing.trade.service;

import com.zhixing.api.dto.course.CourseSimpleInfoDTO;
import com.zhixing.common.exceptions.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 课程可购买性守卫单测：锁住「下架课程不允许新增下单/加购」这条交易侧收口。
 *
 * <p>覆盖三种取值语义，尤其是 {@code status == null} 必须放行 ——
 * 历史数据缺字段时宁可放行也不能误伤正常下单（放行是刻意选择，不是遗漏）。
 */
class CoursePurchaseGuardTest {

    private CourseSimpleInfoDTO course(Integer status) {
        CourseSimpleInfoDTO dto = new CourseSimpleInfoDTO();
        dto.setId(1001L);
        dto.setName("虚拟线程实战");
        dto.setStatus(status);
        return dto;
    }

    @Test
    @DisplayName("已上架课程（status=1）放行")
    void onShelfCourseIsPurchasable() {
        assertDoesNotThrow(() -> CoursePurchaseGuard.requirePurchasable(course(1)));
    }

    @Test
    @DisplayName("已下架课程（status=0）拒绝，报「课程已下架」")
    void offShelfCourseIsRejected() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> CoursePurchaseGuard.requirePurchasable(course(0)));

        assertEquals("课程已下架", ex.getMessage());
    }

    @Test
    @DisplayName("课程不存在（null）拒绝，报「课程不存在」")
    void missingCourseIsRejected() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> CoursePurchaseGuard.requirePurchasable(null));

        assertEquals("课程不存在", ex.getMessage());
    }

    @Test
    @DisplayName("状态缺失（null）放行：历史数据兜底，不误伤正常下单")
    void missingStatusIsPurchasable() {
        assertDoesNotThrow(() -> CoursePurchaseGuard.requirePurchasable(course(null)));
    }
}
