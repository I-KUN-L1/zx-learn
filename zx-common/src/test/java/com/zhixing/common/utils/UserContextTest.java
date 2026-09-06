package com.zhixing.common.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用户上下文（ThreadLocal）的单元测试，重点覆盖 @RequireRole 依赖的 hasRole 判定
 */
class UserContextTest {

    @BeforeEach
    void setUp() {
        UserContext.remove();
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    @Test
    @DisplayName("未设置角色时 getUserId 返回默认 0，getRole 返回 null")
    void defaults_when_nothing_set() {
        assertEquals(0L, UserContext.getUserId());
        assertNull(UserContext.getUser());
        assertNull(UserContext.getRole());
    }

    @Test
    @DisplayName("setUser / setRole 后可正确读回")
    void set_and_get_user_and_role() {
        UserContext.setUser(9527L);
        UserContext.setRole(1);
        assertEquals(9527L, UserContext.getUser());
        assertEquals(9527L, UserContext.getUserId());
        assertEquals(1, UserContext.getRole());
    }

    @Test
    @DisplayName("hasRole：未设置角色时恒为 false")
    void hasRole_false_when_role_not_set() {
        assertFalse(UserContext.hasRole(1));
        assertFalse(UserContext.hasRole(1, 2, 3));
    }

    @Test
    @DisplayName("hasRole：允许集为 null 或空时恒为 false")
    void hasRole_false_when_allowed_empty() {
        UserContext.setRole(1);
        assertFalse(UserContext.hasRole());
        assertFalse(UserContext.hasRole((Integer[]) null));
    }

    @Test
    @DisplayName("hasRole：单角色命中与未命中")
    void hasRole_single_role_match() {
        UserContext.setRole(2);
        assertTrue(UserContext.hasRole(2));
        assertFalse(UserContext.hasRole(1));
    }

    @Test
    @DisplayName("hasRole：多角色任一命中即放行")
    void hasRole_any_of_multiple_roles() {
        UserContext.setRole(3);
        assertTrue(UserContext.hasRole(1, 3));
        assertFalse(UserContext.hasRole(1, 2));
    }

    @Test
    @DisplayName("remove 同时清理用户与角色，避免线程复用串号")
    void remove_clears_both_thread_locals() {
        UserContext.setUser(1L);
        UserContext.setRole(1);
        UserContext.remove();
        assertNull(UserContext.getUser());
        assertNull(UserContext.getRole());
        assertEquals(0L, UserContext.getUserId());
    }
}
