package com.zhixing.api.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 登录表单
 */
@Data
public class LoginFormDTO implements Serializable {

    private String cellPhone;
    private String password;
    private String username;
    private String verifyCode;
}
