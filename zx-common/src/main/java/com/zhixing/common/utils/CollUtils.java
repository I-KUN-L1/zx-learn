package com.zhixing.common.utils;

import cn.hutool.core.collection.CollUtil;

import java.util.Collection;
import java.util.List;

/**
 * 集合工具
 */
public class CollUtils extends CollUtil {

    public static boolean isEmpty(Collection<?> coll) {
        return coll == null || coll.isEmpty();
    }

    public static boolean isNotEmpty(Collection<?> coll) {
        return !isEmpty(coll);
    }

    public static <T> List<T> emptyList() {
        return List.of();
    }
}
