package com.zhixing.user.domain.dto;

import lombok.Data;

/**
 * 用户表单
 */
@Data
public class UserFormDTO {

    private Long id;
    private String cellPhone;
    private String username;
    private String password;
    private String name;
    private Integer type;
    private Integer status;
    private String icon;
    private String email;
    private String city;
    private Integer gender;
}
