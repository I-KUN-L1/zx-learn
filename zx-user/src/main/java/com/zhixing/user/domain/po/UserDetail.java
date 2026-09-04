package com.zhixing.user.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixing.common.domain.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 用户详情（教师/学员扩展）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user_detail")
public class UserDetail extends BasePO {

    private Long userId;
    /** 教师职称 */
    private String jobTitle;
    /** 教师简介 */
    private String intro;
    /** 学员生日 */
    private LocalDate birthday;
    /** 学员学历 */
    private String education;
    /** 学员职业 */
    private String occupation;
}
