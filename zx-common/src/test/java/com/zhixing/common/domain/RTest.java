package com.zhixing.common.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 统一响应 R 的单元测试
 */
class RTest {

    @Test
    void ok_without_data_returns_code_200() {
        R<Void> r = R.ok();
        assertEquals(200, r.getCode());
        assertEquals("OK", r.getMsg());
        assertNull(r.getData());
        assertTrue(r.success());
    }

    @Test
    void ok_with_data_returns_data() {
        R<String> r = R.ok("hello");
        assertEquals(200, r.getCode());
        assertEquals("hello", r.getData());
        assertTrue(r.success());
    }

    @Test
    void error_returns_default_code_zero() {
        R<Void> r = R.error("系统繁忙");
        assertEquals(0, r.getCode());
        assertEquals("系统繁忙", r.getMsg());
        assertFalse(r.success());
    }

    @Test
    void error_with_code_returns_custom_code() {
        R<Void> r = R.error(500, "内部错误");
        assertEquals(500, r.getCode());
        assertFalse(r.success());
    }
}
