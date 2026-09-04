package com.zhixing.learning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixing.common.exceptions.BizIllegalException;
import com.zhixing.learning.domain.po.SignIn;
import com.zhixing.learning.mapper.SignInMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 签到服务
 */
@Service
@RequiredArgsConstructor
public class SignInService {

    /** 每日签到基础积分 */
    private static final int BASE_POINTS = 5;

    private final SignInMapper signInMapper;

    /**
     * 签到：当天只能签一次，连续签到天数递增。
     */
    public SignIn checkIn(Long userId) {
        LocalDate today = LocalDate.now();
        SignIn exist = signInMapper.selectOne(new LambdaQueryWrapper<SignIn>()
                .eq(SignIn::getUserId, userId)
                .eq(SignIn::getSignDate, today));
        if (exist != null) {
            throw new BizIllegalException("今天已签到，请明天再来");
        }
        SignIn yesterday = signInMapper.selectOne(new LambdaQueryWrapper<SignIn>()
                .eq(SignIn::getUserId, userId)
                .eq(SignIn::getSignDate, today.minusDays(1)));
        int streak = yesterday == null ? 1 : yesterday.getStreak() + 1;
        SignIn record = new SignIn();
        record.setUserId(userId);
        record.setSignDate(today);
        record.setStreak(streak);
        record.setPoints(calcPoints(streak));
        signInMapper.insert(record);
        return record;
    }

    /**
     * 连续签到 N 天可获得的积分
     */
    private int calcPoints(int streak) {
        return BASE_POINTS * Math.min(streak, 10);
    }

    /**
     * 今日是否已签到
     */
    public SignIn today(Long userId) {
        return signInMapper.selectOne(new LambdaQueryWrapper<SignIn>()
                .eq(SignIn::getUserId, userId)
                .eq(SignIn::getSignDate, LocalDate.now()));
    }

    /**
     * 查询用户签到记录
     */
    public List<SignIn> list(Long userId) {
        return signInMapper.selectList(new LambdaQueryWrapper<SignIn>()
                .eq(SignIn::getUserId, userId)
                .orderByDesc(SignIn::getSignDate));
    }
}