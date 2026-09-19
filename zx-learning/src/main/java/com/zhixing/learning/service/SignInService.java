package com.zhixing.learning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixing.common.exceptions.BizIllegalException;
import com.zhixing.learning.domain.po.SignIn;
import com.zhixing.learning.mapper.SignInMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 签到服务
 */
@Service
@RequiredArgsConstructor
public class SignInService {

    /** 每日签到基础积分 */
    private static final int BASE_POINTS = 5;

    private final SignInMapper signInMapper;
    private final PointsService pointsService;

    /**
     * 签到：当天只能签一次，连续签到天数递增。
     * <p>
     * 签到积分在落库后同步写入积分明细（幂等键 = signDate），
     * 因此"签到得分"与"积分明细/排行榜"始终对得上账。
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
        int points = calcPoints(streak);
        record.setPoints(points);
        signInMapper.insert(record);
        // 签到积分计入积分明细（幂等：同一用户同一天只入账一次）
        pointsService.award(userId, PointsService.SOURCE_SIGN, points,
                "每日签到（连续 " + streak + " 天）", "SIGN:" + today);
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

    /**
     * 当前连续签到天数：今日未签从昨日起算，向前逐日回溯断档即止。
     */
    public int currentStreak(Long userId) {
        List<SignIn> records = list(userId);
        if (records.isEmpty()) {
            return 0;
        }
        Set<LocalDate> dates = records.stream()
                .map(SignIn::getSignDate).collect(Collectors.toSet());
        LocalDate cursor = LocalDate.now();
        if (!dates.contains(cursor)) {
            cursor = cursor.minusDays(1);
        }
        int streak = 0;
        while (dates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }
}