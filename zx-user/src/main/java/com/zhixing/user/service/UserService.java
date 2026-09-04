package com.zhixing.user.service;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixing.api.dto.user.LoginFormDTO;
import com.zhixing.api.dto.user.UserDTO;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.exceptions.BizIllegalException;
import com.zhixing.common.exceptions.UnauthorizedException;
import com.zhixing.common.utils.BeanUtils;
import com.zhixing.common.utils.StringUtils;
import com.zhixing.common.utils.UserContext;
import com.zhixing.user.domain.dto.UserFormDTO;
import com.zhixing.user.domain.po.User;
import com.zhixing.user.domain.po.UserDetail;
import com.zhixing.user.domain.vo.UserVO;
import com.zhixing.user.mapper.UserDetailMapper;
import com.zhixing.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户服务
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final UserDetailMapper userDetailMapper;

    /** 管理员重置密码的默认值，由环境变量 ZX_USER_DEFAULT_PASSWORD 注入，不硬编码 */
    @Value("${ZX_USER_DEFAULT_PASSWORD:}")
    private String defaultPassword;

    /**
     * 登录校验（供认证服务调用）
     */
    public UserDTO queryUserDetail(LoginFormDTO loginFormDTO, boolean isStaff) {
        String cellPhone = loginFormDTO.getCellPhone();
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getCellPhone, cellPhone));
        if (user == null) {
            throw new UnauthorizedException("用户名或密码错误");
        }
        if (!BCrypt.checkpw(loginFormDTO.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("用户名或密码错误");
        }
        if (isStaff && user.getType() != 1) {
            throw new UnauthorizedException("非管理员账号");
        }
        return BeanUtils.copyBean(user, UserDTO.class);
    }

    public List<UserDTO> queryUserByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<User> users = userMapper.selectBatchIds(ids);
        return BeanUtils.copyList(users, UserDTO.class);
    }

    public Integer queryUserType(Long id) {
        User user = userMapper.selectById(id);
        return user == null ? null : user.getType();
    }

    public Map<String, Long> exchangeUserId(String phone) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getCellPhone, phone));
        return user == null ? Map.of() : Map.of("userId", user.getId());
    }

    public UserVO queryMe() {
        Long userId = UserContext.getUserId();
        if (userId == 0) {
            throw new UnauthorizedException("未登录");
        }
        return queryById(userId);
    }

    public UserVO queryById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BadRequestException("用户不存在");
        }
        UserDetail detail = userDetailMapper.selectOne(
                new LambdaQueryWrapper<UserDetail>().eq(UserDetail::getUserId, id));
        return UserVO.of(user, detail);
    }

    public void saveUser(UserFormDTO form) {
        checkCellPhone(form.getCellPhone(), null);
        User user = BeanUtils.copyBean(form, User.class);
        if (StringUtils.isNotBlank(form.getPassword())) {
            user.setPassword(BCrypt.hashpw(form.getPassword()));
        }
        if (user.getStatus() == null) {
            user.setStatus(1);
        }
        if (user.getType() == null) {
            user.setType(2);
        }
        userMapper.insert(user);
    }

    public void updateUser(Long id, UserFormDTO form) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BadRequestException("用户不存在");
        }
        if (StringUtils.isNotBlank(form.getPassword())) {
            user.setPassword(BCrypt.hashpw(form.getPassword()));
        }
        if (StringUtils.isNotBlank(form.getName())) {
            user.setName(form.getName());
        }
        if (form.getStatus() != null) {
            user.setStatus(form.getStatus());
        }
        if (StringUtils.isNotBlank(form.getIcon())) {
            user.setIcon(form.getIcon());
        }
        if (StringUtils.isNotBlank(form.getEmail())) {
            user.setEmail(form.getEmail());
        }
        userMapper.updateById(user);
    }

    public void resetPassword(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BadRequestException("用户不存在");
        }
        if (StringUtils.isBlank(defaultPassword)) {
            throw new BizIllegalException("未配置重置密码，请设置环境变量 ZX_USER_DEFAULT_PASSWORD");
        }
        user.setPassword(BCrypt.hashpw(defaultPassword));
        userMapper.updateById(user);
    }

    public void updateStatus(Long id, Integer status) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BadRequestException("用户不存在");
        }
        user.setStatus(status);
        userMapper.updateById(user);
    }

    public void checkCellPhone(String cellPhone, Long excludeId) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getCellPhone, cellPhone);
        if (excludeId != null) {
            wrapper.ne(User::getId, excludeId);
        }
        Long count = userMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BizIllegalException("手机号已存在");
        }
    }

    public void changePassword(String oldPassword, String newPassword) {
        Long userId = UserContext.getUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new UnauthorizedException("未登录");
        }
        if (!BCrypt.checkpw(oldPassword, user.getPassword())) {
            throw new BadRequestException("原密码错误");
        }
        user.setPassword(BCrypt.hashpw(newPassword));
        userMapper.updateById(user);
    }

    public List<UserVO> pageQueryUsers(Integer type) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (type != null) {
            wrapper.eq(User::getType, type);
        }
        wrapper.orderByDesc(User::getCreateTime);
        return userMapper.selectList(wrapper).stream()
                .map(u -> UserVO.of(u, null))
                .collect(Collectors.toList());
    }

    // ==================== 首个管理员引导 ====================

    /**
     * 是否存在员工/管理员账号（type=1），供 zx-auth 启动引导判断
     */
    public boolean adminExists() {
        return userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getType, 1)) > 0;
    }

    /**
     * 引导创建首个管理员：仅在不存在任何管理员时插入，密码经 BCrypt 加密后落库。
     * 并发/重复调用做幂等处理，已存在则返回现有管理员。
     */
    public UserDTO createBootstrapAdmin(String username, String cellPhone, String rawPassword) {
        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getType, 1).last("limit 1"));
        if (existing != null) {
            return BeanUtils.copyBean(existing, UserDTO.class);
        }
        User user = new User();
        user.setUsername(username);
        user.setCellPhone(cellPhone);
        user.setName("管理员");
        user.setType(1);
        user.setStatus(1);
        // 与登录校验一致的 BCrypt 加密（hutool BCrypt）
        user.setPassword(BCrypt.hashpw(rawPassword));
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发创建时手机号/用户名撞主键唯一约束，按幂等处理
            User again = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getType, 1).last("limit 1"));
            if (again != null) {
                return BeanUtils.copyBean(again, UserDTO.class);
            }
            throw e;
        }
        return BeanUtils.copyBean(user, UserDTO.class);
    }

    /**
     * 首次改密：校验原密码（BCrypt）通过后写入新密码
     */
    public void changeBootstrapPassword(String cellPhone, String oldRawPassword, String newRawPassword) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getCellPhone, cellPhone));
        if (user == null) {
            throw new UnauthorizedException("账号不存在");
        }
        if (!BCrypt.checkpw(oldRawPassword, user.getPassword())) {
            throw new BadRequestException("原密码错误");
        }
        user.setPassword(BCrypt.hashpw(newRawPassword));
        userMapper.updateById(user);
    }
}
