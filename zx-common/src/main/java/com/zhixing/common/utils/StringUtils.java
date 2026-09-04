package com.zhixing.common.utils;

/**
 * 字符串工具
 */
public class StringUtils extends org.springframework.util.StringUtils {

    public static boolean isBlank(String str) {
        return str == null || str.isBlank();
    }

    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    public static Long parseLong(String str) {
        if (isBlank(str)) {
            return null;
        }
        try {
            return Long.parseLong(str.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String toString(Object obj) {
        return obj == null ? null : obj.toString();
    }
}
