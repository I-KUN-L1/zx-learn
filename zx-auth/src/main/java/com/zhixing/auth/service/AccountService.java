package com.zhixing.auth.service;

import com.zhixing.api.client.user.UserClient;
import com.zhixing.api.dto.user.LoginFormDTO;
import com.zhixing.api.dto.user.UserDTO;
import com.zhixing.auth.common.util.JwtTool;
import com.zhixing.auth.domain.po.LoginRecord;
import com.zhixing.auth.domain.vo.LoginResultVO;
import com.zhixing.auth.mapper.LoginRecordMapper;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.exceptions.UnauthorizedException;
import com.zhixing.common.utils.StringUtils;
import com.zhixing.common.utils.WebUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 账号服务：登录、登出、刷新
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private static final long ACCESS_TOKEN_TTL = 30 * 60 * 1000L;
    private static final long REFRESH_TOKEN_TTL = 30L * 24 * 60 * 60 * 1000L;

    private final JwtTool jwtTool;
    private final UserClient userClient;
    private final LoginRecordMapper loginRecordMapper;

    public LoginResultVO login(LoginFormDTO loginFormDTO, boolean isAdmin, HttpServletRequest request) {
        if (loginFormDTO == null
                || StringUtils.isBlank(loginFormDTO.getCellPhone())
                || StringUtils.isBlank(loginFormDTO.getPassword())) {
            throw new BadRequestException("手机号或密码不能为空");
        }
        // 调用用户服务校验账号密码
        UserDTO user;
        try {
            user = userClient.queryUserDetail(loginFormDTO, isAdmin);
        } catch (Exception e) {
            throw new UnauthorizedException("用户名或密码错误");
        }
        if (user == null) {
            throw new UnauthorizedException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() != 1) {
            throw new UnauthorizedException("账号已被禁用");
        }

        LoginResultVO result = new LoginResultVO();
        result.setUserId(user.getId());
        result.setUsername(user.getName());
        // user.type（1员工/2学员/3教师）写入 token role claim，供网关透传做接口级权限校验
        result.setAccessToken(jwtTool.createAccessToken(user.getId(), user.getType(), ACCESS_TOKEN_TTL));
        result.setRefreshToken(jwtTool.createRefreshToken(user.getId(), REFRESH_TOKEN_TTL));
        result.setExpireTime(ACCESS_TOKEN_TTL / 1000);

        // 记录登录日志（失败不影响登录主流程）
        try {
            LoginRecord record = new LoginRecord();
            record.setUserId(user.getId());
            record.setCellPhone(user.getCellPhone());
            record.setIpv4(WebUtils.getClientIp(request));
            record.setLoginType(isAdmin ? 2 : 1);
            record.setLoginTime(LocalDateTime.now());
            loginRecordMapper.insert(record);
        } catch (Exception e) {
            log.warn("登录日志写入失败：userId={}", user.getId(), e);
        }
        return result;
    }

    public LoginResultVO refresh(String refreshToken) {
        if (StringUtils.isBlank(refreshToken)) {
            throw new UnauthorizedException("登录已过期，请重新登录");
        }
        try {
            Long userId = jwtTool.parseUserId(refreshToken);
            LoginResultVO result = new LoginResultVO();
            result.setUserId(userId);
            // 刷新时补查用户类型，保证续签 token 仍携带 role claim（查询失败不阻断续签，仅降级为无角色）
            Integer role = null;
            try {
                role = userClient.queryUserType(userId);
            } catch (Exception e) {
                log.warn("刷新 token 查询用户角色失败：userId={}, err={}", userId, e.getMessage());
            }
            result.setAccessToken(jwtTool.createAccessToken(userId, role, ACCESS_TOKEN_TTL));
            result.setExpireTime(ACCESS_TOKEN_TTL / 1000);
            return result;
        } catch (Exception e) {
            throw new UnauthorizedException("登录已过期，请重新登录");
        }
    }
}
