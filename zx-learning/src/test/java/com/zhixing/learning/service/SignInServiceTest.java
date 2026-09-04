package com.zhixing.learning.service;

import com.zhixing.common.exceptions.BizIllegalException;
import com.zhixing.learning.domain.po.SignIn;
import com.zhixing.learning.mapper.SignInMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 签到服务单测：首次签到 / 连续签到 / 重复签到拦截
 */
@ExtendWith(MockitoExtension.class)
class SignInServiceTest {

    @Mock
    private SignInMapper signInMapper;

    @InjectMocks
    private SignInService service;

    @Test
    void firstCheckInCreatesStreakOne() {
        // 今日无记录、昨日无记录 -> 首次签到 streak=1
        when(signInMapper.selectOne(any())).thenReturn(null);

        SignIn sign = service.checkIn(1L);

        ArgumentCaptor<SignIn> captor = ArgumentCaptor.forClass(SignIn.class);
        verify(signInMapper).insert(captor.capture());
        SignIn saved = captor.getValue();
        assertEquals(1L, saved.getUserId());
        assertEquals(1, saved.getStreak());
        assertEquals(5, saved.getPoints());
        assertNotNull(saved.getSignDate());
    }

    @Test
    void consecutiveCheckInIncrementsStreak() {
        // today 无记录，yesterday 有记录(streak=3) -> 本次 streak=4
        SignIn yesterday = new SignIn();
        yesterday.setStreak(3);
        when(signInMapper.selectOne(any())).thenReturn(null, yesterday);

        SignIn sign = service.checkIn(1L);

        assertEquals(4, sign.getStreak());
        assertEquals(20, sign.getPoints());
    }

    @Test
    void repeatedCheckInTodayRejected() {
        SignIn today = new SignIn();
        today.setUserId(1L);
        when(signInMapper.selectOne(any())).thenReturn(today);

        assertThrows(BizIllegalException.class, () -> service.checkIn(1L));
    }
}