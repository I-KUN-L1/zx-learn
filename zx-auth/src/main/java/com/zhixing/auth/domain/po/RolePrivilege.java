package com.zhixing.auth.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色-权限关联
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("role_privilege")
public class RolePrivilege extends BasePO {

    private Long roleId;
    private Long privilegeId;
}
