package com.zhixing.user.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.zhixing.user.domain.po.User;
import com.zhixing.user.domain.po.UserDetail;
import lombok.Data;

import java.time.LocalDateTime;

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

    /**
     * 注册时间（账号创建时间）。
     * <p>
     * 管理端「用户与权限」列表需要展示该字段；此前 VO 未映射 createTime，
     * 前端 formatDate(undefined) 恒为空，表现为"注册时间列不显示"。
     * 显式声明 @JsonFormat，避免序列化形态随全局配置漂移导致前端解析失败。
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 最近更新时间（便于运营排查账号变更） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

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
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());
        vo.setDetail(detail);
        return vo;
    }
}
