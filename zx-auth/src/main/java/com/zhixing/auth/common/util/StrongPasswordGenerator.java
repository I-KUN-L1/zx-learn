package com.zhixing.auth.common.util;

import java.security.SecureRandom;

/**
 * 强随机密码生成器：使用 {@link SecureRandom} 生成定长、且同时包含大小写字母/数字/符号的密码。
 */
public final class StrongPasswordGenerator {

    private static final int DEFAULT_LENGTH = 16;
    private static final char[] UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final char[] LOWER = "abcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final char[] DIGIT = "0123456789".toCharArray();
    private static final char[] SYMBOL = "!@#$%^&*()-_+=<>?".toCharArray();
    private static final char[][] POOLS = {UPPER, LOWER, DIGIT, SYMBOL};

    private StrongPasswordGenerator() {
    }

    /**
     * 生成默认长度的强密码（16 位，含大小写/数字/符号）
     */
    public static String generateStrong() {
        return generate(DEFAULT_LENGTH);
    }

    /**
     * 生成指定长度的强密码，保证每个字符类别至少出现一次
     */
    public static String generate(int length) {
        if (length < POOLS.length) {
            throw new IllegalArgumentException("length must be >= " + POOLS.length);
        }
        SecureRandom random = new SecureRandom();
        char[] password = new char[length];
        // 先保证每个类别各占一位
        for (int i = 0; i < POOLS.length; i++) {
            password[i] = POOLS[i][random.nextInt(POOLS[i].length)];
        }
        // 剩余位从全量字符集中随机填充
        for (int i = POOLS.length; i < length; i++) {
            char[] pool = POOLS[random.nextInt(POOLS.length)];
            password[i] = pool[random.nextInt(pool.length)];
        }
        // 打乱顺序，避免前 4 位固定归类
        for (int i = length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = password[i];
            password[i] = password[j];
            password[j] = tmp;
        }
        return new String(password);
    }
}