package com.zhixing.user.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user")
public class User extends BasePO {

    /** 手机号 */
    private String cellPhone;
    /** 用户名 */
    private String username;
    /** 密码 */
    private String password;
    /** 姓名 */
    private String name;
    /** 用户类型：1-员工 2-学员 3-教师 */
    private Integer type;
    /** 状态：0-禁用 1-正常 */
    private Integer status;
    /** 头像 */
    private String icon;
    /** 邮箱 */
    private String email;
    /** 城市 */
    private String city;
    /** 性别 */
    private Integer gender;
}
