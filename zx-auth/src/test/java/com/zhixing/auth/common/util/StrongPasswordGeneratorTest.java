package com.zhixing.auth.common.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 强密码生成器单元测试：长度、字符类别覆盖与随机性
 */
class StrongPasswordGeneratorTest {

    private static final String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGIT = "0123456789";
    private static final String SYMBOL = "!@#$%^&*()-_+=<>?";

    @Test
    void generates16LengthPasswordContainingAllCharacterClasses() {
        for (int i = 0; i < 200; i++) {
            String password = StrongPasswordGenerator.generateStrong();
            assertNotNull(password);
            assertEquals(16, password.length());
            assertTrue(containsAny(password, UPPER), "应包含大写字母: " + password);
            assertTrue(containsAny(password, LOWER), "应包含小写字母: " + password);
            assertTrue(containsAny(password, DIGIT), "应包含数字: " + password);
            assertTrue(containsAny(password, SYMBOL), "应包含符号: " + password);
        }
    }

    @Test
    void successiveGenerationsAreRandomized() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            seen.add(StrongPasswordGenerator.generateStrong());
        }
        // SecureRandom 下 1000 次生成不应出现重复
        assertEquals(1000, seen.size());
    }

    @Test
    void rejectsLengthShorterThanCharacterClasses() {
        assertThrows(IllegalArgumentException.class, () -> StrongPasswordGenerator.generate(3));
    }

    private boolean containsAny(String password, String pool) {
        for (char c : pool.toCharArray()) {
            if (password.indexOf(c) >= 0) {
                return true;
            }
        }
        return false;
    }
}