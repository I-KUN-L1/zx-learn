package com.zhixing.common.utils;

import cn.hutool.core.bean.BeanUtil;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Bean 拷贝工具
 */
public class BeanUtils {

    public static <T> T copyBean(Object source, Class<T> target) {
        if (source == null) {
            return null;
        }
        return BeanUtil.copyProperties(source, target);
    }

    public static <T> T copyProperties(Object source, Class<T> target) {
        return copyBean(source, target);
    }

    /**
     * 复制属性到已存在的目标对象
     */
    public static void copyProperties(Object source, Object target) {
        if (source == null || target == null) {
            return;
        }
        BeanUtil.copyProperties(source, target);
    }

    public static <T> List<T> copyList(List<?> sourceList, Class<T> target) {
        if (sourceList == null) {
            return List.of();
        }
        return sourceList.stream().map(s -> copyBean(s, target)).collect(Collectors.toList());
    }
}
