package com.zhixing.api.client.user;

import com.zhixing.api.dto.user.BootstrapAdminDTO;
import com.zhixing.api.dto.user.LoginFormDTO;
import com.zhixing.api.dto.user.PasswordChangeDTO;
import com.zhixing.api.dto.user.UserDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 用户服务客户端
 */
@FeignClient(value = "user-service", contextId = "userClient")
public interface UserClient {

    @PostMapping("/users/detail/{isStaff}")
    UserDTO queryUserDetail(@RequestBody LoginFormDTO loginFormDTO, @PathVariable("isStaff") boolean isStaff);

    // ============ 首个管理员引导 ============

    @GetMapping("/users/bootstrap/admin-exists")
    Boolean adminExists();

    @PostMapping("/users/bootstrap/admin")
    UserDTO createBootstrapAdmin(@RequestBody BootstrapAdminDTO dto);

    @PutMapping("/users/bootstrap/password")
    void changeBootstrapPassword(@RequestBody PasswordChangeDTO dto);

    @GetMapping("/users/list")
    List<UserDTO> queryUserByIds(@RequestParam("ids") List<Long> ids);

    @GetMapping("/users/{id}/type")
    Integer queryUserType(@PathVariable("id") Long id);

    @GetMapping("/users/ids")
    Map<String, Long> exchangeUserId(@RequestParam("phone") String phone);

    @GetMapping("/users/me")
    UserDTO queryMe();
}
