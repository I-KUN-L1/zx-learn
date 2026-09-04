package com.zhixing.user.service;

import cn.hutool.crypto.digest.BCrypt;
import com.zhixing.api.dto.user.UserDTO;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.user.domain.po.User;
import com.zhixing.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 首个管理员引导落库测试：验证密码经 BCrypt 加密入库，且校验算法与登录校验一致
 */
@ExtendWith(MockitoExtension.class)
class AdminBootstrapServiceTest {

    @Mock
    private UserMapper userMapper;

    private UserService buildService() {
        return new UserService(userMapper, null);
    }

    @Test
    void createBootstrapAdminStoresBcryptHashThatValidatesAgainstLoginAlgorithm() {
        // 不存在任何管理员时 selectOne 返回 null
        when(userMapper.selectOne(any())).thenReturn(null);

        String raw = "A1bcdef#GhiJk2#$";
        UserService svc = buildService();
        UserDTO created = svc.createBootstrapAdmin("admin", "13800000000", raw);

        assertNotNull(created);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper, times(1)).insert(captor.capture());
        User saved = captor.getValue();
        assertEquals(1, saved.getType());
        // 落库的是 BCrypt 密文而非明文，且能用登录校验同款算法校验通过/拒绝错误密码
        assertNotEquals(raw, saved.getPassword());
        assertTrue(BCrypt.checkpw(raw, saved.getPassword()));
        assertFalse(BCrypt.checkpw(raw + "wrong", saved.getPassword()));
    }

    @Test
    void changeBootstrapPasswordVerifiesOldThenEncryptsNew() {
        String oldRaw = "Old@12345";
        User user = new User();
        user.setCellPhone("13800000000");
        user.setPassword(BCrypt.hashpw(oldRaw));
        when(userMapper.selectOne(any())).thenReturn(user);

        UserService svc = buildService();
        svc.changeBootstrapPassword("13800000000", oldRaw, "New#67890");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper, times(1)).updateById(captor.capture());
        assertTrue(BCrypt.checkpw("New#67890", captor.getValue().getPassword()));
    }

    @Test
    void changeBootstrapPasswordRejectsWrongOldPassword() {
        String oldRaw = "Old@12345";
        User user = new User();
        user.setCellPhone("13800000000");
        user.setPassword(BCrypt.hashpw(oldRaw));
        when(userMapper.selectOne(any())).thenReturn(user);

        UserService svc = buildService();
        assertThrows(BadRequestException.class,
                () -> svc.changeBootstrapPassword("13800000000", "wrong", "New#67890"));
        verify(userMapper, never()).updateById(any(User.class));
    }
}