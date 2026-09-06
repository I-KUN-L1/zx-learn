package com.zhixing.common.constants;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户角色枚举。
 * code 与 user.type 对齐（见 sql/init.sql：类型 1员工/2学员/3教师），
 * 由登录时写入 JWT role claim，网关解析后经 role-info 头透传。
 */
@Getter
@AllArgsConstructor
public enum UserRole {

    /** 员工（管理员） */
    STAFF(1, "admin"),
    /** 学员 */
    STUDENT(2, "student"),
    /** 教师 */
    TEACHER(3, "teacher");

    private final int code;
    private final String alias;
}
