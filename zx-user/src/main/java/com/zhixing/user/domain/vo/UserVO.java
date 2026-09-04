package com.zhixing.user.domain.vo;

import com.zhixing.user.domain.po.User;
import com.zhixing.user.domain.po.UserDetail;
import lombok.Data;

/**
 * 用户信息视图
 */
@Data
public class UserVO {

    private Long id;
    private String cellPhone;
    private String username;
    private String name;
    private Integer type;
    private Integer status;
    private String icon;
    private String email;
    private String city;
    private Integer gender;
    private UserDetail detail;

    public static UserVO of(User user, UserDetail detail) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setCellPhone(user.getCellPhone());
        vo.setUsername(user.getUsername());
        vo.setName(user.getName());
        vo.setType(user.getType());
        vo.setStatus(user.getStatus());
        vo.setIcon(user.getIcon());
        vo.setEmail(user.getEmail());
        vo.setCity(user.getCity());
        vo.setGender(user.getGender());
        vo.setDetail(detail);
        return vo;
    }
}
