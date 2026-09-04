package com.zhixing.auth.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 权限（API 访问路径权限）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("privilege")
public class Privilege extends BasePO {

    private Long menuId;
    private String method;
    private String uri;
    private String name;
    private String description;
}
