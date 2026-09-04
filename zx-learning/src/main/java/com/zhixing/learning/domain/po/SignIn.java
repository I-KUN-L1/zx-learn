package com.zhixing.learning.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 签到记录
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sign_in")
public class SignIn extends BasePO {

    /** 用户 id */
    private Long userId;

    /** 签到日期 */
    private LocalDate signDate;

    /** 连续签到天数 */
    private Integer streak;

    /** 获得积分 */
    private Integer points;
}