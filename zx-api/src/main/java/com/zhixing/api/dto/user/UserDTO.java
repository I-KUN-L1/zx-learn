package com.zhixing.api.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 跨服务传输的用户信息
 */
@Data
public class UserDTO implements Serializable {

    private Long id;
    private String cellPhone;
    private String name;
    private String username;
    private Integer type;
    private Integer status;
    private String icon;
}
