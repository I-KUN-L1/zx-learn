package com.zhixing.auth.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 登录记录
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("login_record")
public class LoginRecord extends BasePO {

    private Long userId;
    private String cellPhone;
    private String ipv4;
    private Integer loginType;
    private LocalDateTime loginTime;
}
