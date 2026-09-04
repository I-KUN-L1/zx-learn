package com.zhixing.api.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 首次改密请求：先校验原密码（BCrypt），再写入新密码
 */
@Data
public class PasswordChangeDTO implements Serializable {

    /** 手机号 */
    private String cellPhone;
    /** 原密码（初始引导密码） */
    private String oldPassword;
    /** 新密码 */
    private String newPassword;
}