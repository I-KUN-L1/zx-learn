package com.zhixing.auth.domain.vo;

import lombok.Data;

/**
 * 登录结果
 */
@Data
public class LoginResultVO {

    private String accessToken;
    private Long expireTime;
    private String refreshToken;
    private Long userId;
    private String username;
}
