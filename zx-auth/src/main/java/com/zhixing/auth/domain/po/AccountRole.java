package com.zhixing.auth.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 账号-角色关联
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("account_role")
public class AccountRole extends BasePO {

    private Long accountId;
    private Long roleId;
}
