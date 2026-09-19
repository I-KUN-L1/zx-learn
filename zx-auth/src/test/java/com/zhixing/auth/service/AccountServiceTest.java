package com.zhixing.auth.service;

import com.zhixing.api.client.user.UserClient;
import com.zhixing.api.dto.user.LoginFormDTO;
import com.zhixing.api.dto.user.UserDTO;
import com.zhixing.auth.common.util.JwtTool;
import com.zhixing.auth.domain.vo.LoginResultVO;
import com.zhixing.auth.mapper.LoginRecordMapper;
import com.zhixing.common.exceptions.AccountDisabledException;
import com.zhixing.common.exceptions.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 登录服务单元测试：重点覆盖 P0-1 修复——
 * 错误凭据、远程空身份对象均必须 401，禁止签发无身份 token
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private JwtTool jwtTool;
    @Mock
    private UserClient userClient;
    @Mock
    private LoginRecordMapper loginRecordMapper;
    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private AccountService accountService;

    private LoginFormDTO form() {
        LoginFormDTO dto = new LoginFormDTO();
        dto.setCellPhone("13800000000");
        dto.setPassword("Any#Password123");
        return dto;
    }

    private void mockRequestIp() {
        // WebUtils.getClientIp 依次读取 X-Forwarded-For / X-Real-IP / remoteAddr
        lenient().when(request.getHeader(anyString())).thenReturn(null);
        lenient().when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    @Test
    void wrongCredentialsThrows401() {
        // 远程校验失败：zx-user 返回业务 401，经 RDecoder 转为 UnauthorizedException
        when(userClient.queryUserDetail(any(LoginFormDTO.class), anyBoolean()))
                .thenThrow(new UnauthorizedException("用户名或密码错误"));
        UnauthorizedException e = assertThrows(UnauthorizedException.class,
                () -> accountService.login(form(), true, request));
        assertEquals("用户名或密码错误", e.getMessage());
    }

    @Test
    void remoteFailureThrows401() {
        // 远程调用网络级失败（FeignException 等）同样 401
        when(userClient.queryUserDetail(any(LoginFormDTO.class), anyBoolean()))
                .thenThrow(new RuntimeException("connection refused"));
        assertThrows(UnauthorizedException.class, () -> accountService.login(form(), true, request));
    }

    @Test
    void emptyUserWithoutIdThrows401() {
        // P0-1 防御：远程返回"全字段为 null 的空对象"（历史缺陷中的空身份）必须 401
        when(userClient.queryUserDetail(any(LoginFormDTO.class), anyBoolean()))
                .thenReturn(new UserDTO());
        UnauthorizedException e = assertThrows(UnauthorizedException.class,
                () -> accountService.login(form(), true, request));
        assertEquals("用户名或密码错误", e.getMessage());
    }

    @Test
    void disabledUserThrowsAccountDisabled() {
        // 账号被禁用**不再是 401**：凭据错误才归 401（前端只提示"用户名或密码错误"），
        // 禁用需要明确处置指引，故单列业务码 423，前端据此弹"请联系管理员"专属弹窗
        // （见 zx-web/src/api/request.ts 的 ACCOUNT_DISABLED_CODE 与 utils/loginPrompt.ts）。
        UserDTO user = new UserDTO();
        user.setId(1L);
        user.setStatus(0);
        when(userClient.queryUserDetail(any(LoginFormDTO.class), anyBoolean())).thenReturn(user);
        AccountDisabledException e = assertThrows(AccountDisabledException.class,
                () -> accountService.login(form(), true, request));
        assertEquals("该账号已被禁用，请联系管理员", e.getMessage());
        assertEquals(423, e.getCode());
    }

    @Test
    void loginSuccessIssuesTokenWithIdentity() {
        mockRequestIp();
        UserDTO user = new UserDTO();
        user.setId(100L);
        user.setName("admin");
        user.setCellPhone("13800000000");
        user.setType(1);
        user.setStatus(1);
        when(userClient.queryUserDetail(any(LoginFormDTO.class), anyBoolean())).thenReturn(user);
        when(jwtTool.createAccessToken(anyLong(), anyInt(), anyLong())).thenReturn("access-token");
        when(jwtTool.createRefreshToken(anyLong(), anyLong())).thenReturn("refresh-token");

        LoginResultVO result = accountService.login(form(), true, request);

        assertEquals(100L, result.getUserId());
        assertEquals("access-token", result.getAccessToken());
        assertEquals("refresh-token", result.getRefreshToken());
        assertEquals(30 * 60L, result.getExpireTime());
    }
}
