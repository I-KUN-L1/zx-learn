package com.zhixing.common.utils;

import com.zhixing.common.constants.UserRole;
import com.zhixing.common.exceptions.ForbiddenException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * OwnerAccessGuard 资源所有者守卫单测。
 *
 * <p>回归背景：/learning-records/users/**、/question-results/users/** 等内部型
 * 接口曾无任何保护，外部用户（学员）经网关可读取任意他人学习记录与答题统计（IDOR）。
 */
class OwnerAccessGuardTest {

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    @Test
    @DisplayName("内部 Feign 调用（无 user-info 头 → UserContext 为空）直接放行")
    void internalCallWithoutUserContextShouldPass() {
        assertDoesNotThrow(() -> OwnerAccessGuard.checkOwnerOrInternal(999L));
    }

    @Test
    @DisplayName("学员查询本人数据放行")
    void ownerSelfAccessShouldPass() {
        UserContext.setUser(100L);
        UserContext.setRole(UserRole.STUDENT.getCode());
        assertDoesNotThrow(() -> OwnerAccessGuard.checkOwnerOrInternal(100L));
    }

    @Test
    @DisplayName("学员查询他人数据拒绝（IDOR 回归用例）")
    void studentAccessingOthersShouldBeForbidden() {
        UserContext.setUser(100L);
        UserContext.setRole(UserRole.STUDENT.getCode());
        assertThrows(ForbiddenException.class, () -> OwnerAccessGuard.checkOwnerOrInternal(200L));
    }

    @Test
    @DisplayName("STAFF 可查询任意用户数据")
    void staffAccessingOthersShouldPass() {
        UserContext.setUser(1L);
        UserContext.setRole(UserRole.STAFF.getCode());
        assertDoesNotThrow(() -> OwnerAccessGuard.checkOwnerOrInternal(200L));
    }

    @Test
    @DisplayName("教师查询他人数据拒绝（仅本人与 STAFF 放行）")
    void teacherAccessingOthersShouldBeForbidden() {
        UserContext.setUser(300L);
        UserContext.setRole(UserRole.TEACHER.getCode());
        assertThrows(ForbiddenException.class, () -> OwnerAccessGuard.checkOwnerOrInternal(200L));
    }
}
