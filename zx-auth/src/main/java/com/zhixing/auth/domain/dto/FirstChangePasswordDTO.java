package com.zhixing.auth.domain.dto;

import lombok.Data;

/**
 * 首次登录改密请求
 */
@Data
public class FirstChangePasswordDTO {

    /** 手机号 */
    private String cellPhone;
    /** 初始密码 */
    private String oldPassword;
    /** 新密码 */
    private String newPassword;
}