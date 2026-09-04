package com.zhixing.api.client.user;

import com.zhixing.api.dto.user.BootstrapAdminDTO;
import com.zhixing.api.dto.user.LoginFormDTO;
import com.zhixing.api.dto.user.PasswordChangeDTO;
import com.zhixing.api.dto.user.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 用户服务降级工厂
 */
@Slf4j
@Component
public class UserClientFallbackFactory implements FallbackFactory<UserClient> {

    @Override
    public UserClient create(Throwable cause) {
        log.error("user-service 调用失败", cause);
        return new UserClient() {
            @Override
            public UserDTO queryUserDetail(LoginFormDTO loginFormDTO, boolean isStaff) {
                return null;
            }

            @Override
            public Boolean adminExists() {
                return Boolean.TRUE;
            }

            @Override
            public UserDTO createBootstrapAdmin(BootstrapAdminDTO dto) {
                return null;
            }

            @Override
            public void changeBootstrapPassword(PasswordChangeDTO dto) {
                throw new IllegalStateException("user-service 不可用，无法修改初始密码", cause);
            }

            @Override
            public List<UserDTO> queryUserByIds(List<Long> ids) {
                return List.of();
            }

            @Override
            public Integer queryUserType(Long id) {
                return null;
            }

            @Override
            public Map<String, Long> exchangeUserId(String phone) {
                return Map.of();
            }

            @Override
            public UserDTO queryMe() {
                return null;
            }
        };
    }
}
