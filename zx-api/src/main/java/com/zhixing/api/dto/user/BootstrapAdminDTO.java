package com.zhixing.api.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 首个管理员引导创建请求：由 zx-auth 启动时调用 zx-user 落库
 */
@Data
public class BootstrapAdminDTO implements Serializable {

    /** 管理员手机号 */
    private String cellPhone;
    /** 管理员用户名 */
    private String username;
    /** 明文初始密码（由 zx-user 内部 BCrypt 加密后再落库） */
    private String password;
}