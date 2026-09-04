package com.zhixing.auth.controller;

import com.zhixing.api.dto.user.LoginFormDTO;
import com.zhixing.auth.common.constants.JwtConstants;
import com.zhixing.auth.domain.dto.FirstChangePasswordDTO;
import com.zhixing.auth.domain.vo.LoginResultVO;
import com.zhixing.auth.service.AccountService;
import com.zhixing.auth.service.AdminBootstrapService;
import com.zhixing.common.domain.R;
import com.zhixing.common.utils.CookieBuilder;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 账号管理
 */
@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final AdminBootstrapService adminBootstrapService;

    @PostMapping("/login")
    @Operation(summary = "用户端登录")
    public R<LoginResultVO> login(@RequestBody LoginFormDTO loginFormDTO,
                                  HttpServletRequest request, HttpServletResponse response) {
        LoginResultVO result = accountService.login(loginFormDTO, false, request);
        writeRefreshCookie(result, response, false);
        return R.ok(result);
    }

    @PostMapping("/admin/login")
    @Operation(summary = "管理端登录")
    public R<LoginResultVO> adminLogin(@RequestBody LoginFormDTO loginFormDTO,
                                       HttpServletRequest request, HttpServletResponse response) {
        LoginResultVO result = accountService.login(loginFormDTO, true, request);
        writeRefreshCookie(result, response, true);
        return R.ok(result);
    }

    @PostMapping("/password/first-change")
    @Operation(summary = "首次登录修改初始密码")
    public R<Void> firstChangePassword(@RequestBody FirstChangePasswordDTO dto) {
        // 由 zx-user 校验原密码（BCrypt）并落库；成功后 zx-auth 删除 .bootstrap-credentials 凭据文件
        adminBootstrapService.changeBootstrapPassword(dto.getCellPhone(), dto.getOldPassword(), dto.getNewPassword());
        return R.ok();
    }

    @PostMapping("/logout")
    @Operation(summary = "退出登录")
    public R<Void> logout(HttpServletResponse response) {
        CookieBuilder.newBuilder(JwtConstants.JWT_REFRESH_COOKIE_KEY).value("").maxAge(0).write(response);
        CookieBuilder.newBuilder(JwtConstants.JWT_ADMIN_REFRESH_COOKIE_KEY).value("").maxAge(0).write(response);
        return R.ok();
    }

    @GetMapping("/refresh")
    @Operation(summary = "刷新 token")
    public R<LoginResultVO> refresh(HttpServletRequest request) {
        String refreshToken = getCookieValue(request, JwtConstants.JWT_REFRESH_COOKIE_KEY);
        if (refreshToken == null) {
            refreshToken = getCookieValue(request, JwtConstants.JWT_ADMIN_REFRESH_COOKIE_KEY);
        }
        return R.ok(accountService.refresh(refreshToken));
    }

    private void writeRefreshCookie(LoginResultVO result, HttpServletResponse response, boolean admin) {
        String key = admin ? JwtConstants.JWT_ADMIN_REFRESH_COOKIE_KEY : JwtConstants.JWT_REFRESH_COOKIE_KEY;
        CookieBuilder.newBuilder(key)
                .value(result.getRefreshToken())
                .path("/")
                .maxAge(30 * 24 * 60 * 60)
                .httpOnly(true)
                .sameSite("Lax")
                .write(response);
    }

    private String getCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (name.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
