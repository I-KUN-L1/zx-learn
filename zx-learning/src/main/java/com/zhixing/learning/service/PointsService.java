package com.zhixing.learning.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixing.api.client.user.UserClient;
import com.zhixing.api.constants.PointsSource;
import com.zhixing.api.dto.user.UserDTO;
import com.zhixing.common.domain.PageDTO;
import com.zhixing.common.domain.PageQuery;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.learning.domain.po.PointsRecord;
import com.zhixing.learning.mapper.PointsRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 积分服务 —— 个人中心「我的数据 / 学习积分排行榜 / 积分明细」的数据源。
 * <p>
 * <b>积分口径</b>：不维护汇总表，用户积分恒等于积分明细的 SUM(points)。
 * 好处是明细与总量天然一致，不会出现"汇总刷新失败导致对不上账"的双写问题；
 * 代价是排行榜为聚合查询，本项目数据量下完全可接受。
 * <p>
 * <b>自动加分触发点</b>（全部服务端触发，前端无法伪造）：
 * <ul>
 *   <li>完成小节学习 —— {@code LearningRecordService} 进度首次达到 100% 时 +{@value #POINTS_LESSON_FINISH}</li>
 *   <li>完成整门课程 —— 课程全部小节学完时 +{@value #POINTS_COURSE_FINISH}</li>
 *   <li>完成测验 —— zx-exam 服务端判分答对一题 +{@value #POINTS_QUIZ_CORRECT}</li>
 *   <li>每日签到 —— 按连续天数阶梯奖励</li>
 *   <li>参与讨论 —— 发布话题 +{@value #POINTS_TOPIC_CREATE}，回复 +{@value #POINTS_REPLY_CREATE}</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointsService {

    /* ==================== 积分来源（权威值见 zx-api PointsSource） ==================== */

    public static final String SOURCE_LESSON = PointsSource.LESSON;
    public static final String SOURCE_COURSE = PointsSource.COURSE;
    public static final String SOURCE_QUIZ = PointsSource.QUIZ;
    public static final String SOURCE_SIGN = PointsSource.SIGN;
    public static final String SOURCE_DISCUSSION = PointsSource.DISCUSSION;
    public static final String SOURCE_REPLY = PointsSource.REPLY;

    /* ==================== 积分规则 ==================== */

    /** 完成一个小节 */
    public static final int POINTS_LESSON_FINISH = 10;
    /** 完成整门课程（额外奖励） */
    public static final int POINTS_COURSE_FINISH = 50;
    /** 测验答对一题 */
    public static final int POINTS_QUIZ_CORRECT = PointsSource.POINTS_QUIZ_CORRECT;
    /** 发布一个讨论话题 */
    public static final int POINTS_TOPIC_CREATE = 5;
    /** 回复一次讨论 */
    public static final int POINTS_REPLY_CREATE = 2;

    /** 来源 → 中文文案（积分明细展示） */
    private static final Map<String, String> SOURCE_TEXT = Map.of(
            SOURCE_LESSON, "课程学习",
            SOURCE_COURSE, "课程完成",
            SOURCE_QUIZ, "测验答题",
            SOURCE_SIGN, "每日签到",
            SOURCE_DISCUSSION, "参与讨论",
            SOURCE_REPLY, "讨论回复");

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PointsRecordMapper pointsRecordMapper;
    private final UserClient userClient;

    /* ==================== 加分 ==================== */

    /**
     * 授予积分（幂等）。
     * <p>
     * 依赖 {@code uk_user_source_ref} 唯一索引实现幂等：并发/重复投递时后到的插入抛
     * {@link DuplicateKeyException}，此处吞掉并返回 {@code false}，因此调用方无需先查后写，
     * 且失败可安全重试。
     *
     * @return 本次是否真正入账
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean award(Long userId, String source, int points, String description, String refId) {
        if (userId == null || userId <= 0 || points <= 0 || source == null || source.isBlank()) {
            return false;
        }
        PointsRecord record = new PointsRecord();
        record.setUserId(userId);
        record.setSource(source);
        record.setPoints(points);
        record.setDescription(description);
        // ref_id 建表为 NOT NULL，空值统一收敛为空串，避免 NULL 在唯一索引中的"可重复"语义
        record.setRefId(refId == null ? "" : refId);
        try {
            pointsRecordMapper.insert(record);
            log.info("积分入账：userId={}, source={}, points={}, ref={}", userId, source, points, record.getRefId());
            return true;
        } catch (DuplicateKeyException e) {
            // 幂等命中：同一业务动作已加过分，直接跳过
            log.debug("积分幂等跳过：userId={}, source={}, ref={}", userId, source, record.getRefId());
            return false;
        } catch (Exception e) {
            // 加分属于附属动作，绝不能反向影响主业务（看课/交卷必须成功）
            log.warn("积分入账失败（已降级，不影响主流程）：userId={}, source={}, err={}",
                    userId, source, e.getMessage());
            return false;
        }
    }

    /**
     * 查询用户积分总额（无记录返回 0）
     */
    public Long total(Long userId) {
        if (userId == null || userId <= 0) {
            return 0L;
        }
        Long total = pointsRecordMapper.sumByUser(userId);
        return total == null ? 0L : total;
    }

    /* ==================== 个人中心：我的数据 ==================== */

    /**
     * 我的积分概况：总额 + 排名 + 参与者总数 + 今日/近 7 日增量。
     * 一次性返回，避免个人中心首屏多次往返。
     */
    public Map<String, Object> summary(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BadRequestException("未登录");
        }
        long total = total(userId);
        long totalUsers = nz(pointsRecordMapper.countRankedUsers());
        Long higher = pointsRecordMapper.countUsersAbove(total);
        // 排名 = 积分严格高于我的人数 + 1；并列时同名次（符合"显示本人排名"的直觉）
        long rank = nz(higher) + 1;

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime weekStart = LocalDate.now().minusDays(6).atStartOfDay();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("points", total);
        m.put("rank", total > 0 ? rank : 0);
        m.put("totalUsers", totalUsers);
        m.put("todayPoints", sumSince(userId, todayStart));
        m.put("weekPoints", sumSince(userId, weekStart));
        // 超越比例（0~100）：总人数 1 或积分为 0 时按 0 处理，避免除零
        m.put("beatRate", totalUsers <= 1 || total <= 0
                ? 0 : Math.round((totalUsers - rank) * 1000.0 / (totalUsers - 1)) / 10.0);
        m.put("recordCount", pointsRecordMapper.selectCount(
                new LambdaQueryWrapper<PointsRecord>().eq(PointsRecord::getUserId, userId)));
        return m;
    }

    /** 指定时间点之后的积分增量 */
    private long sumSince(Long userId, LocalDateTime start) {
        return pointsRecordMapper.selectList(new LambdaQueryWrapper<PointsRecord>()
                        .eq(PointsRecord::getUserId, userId)
                        .ge(PointsRecord::getCreateTime, start))
                .stream()
                .mapToLong(r -> r.getPoints() == null ? 0L : r.getPoints())
                .sum();
    }

    /* ==================== 个人中心：排行榜 ==================== */

    /**
     * 学习积分排行榜（前 {@code top} 名）。
     * <p>
     * 昵称经 zx-user 批量补全；用户服务不可用时降级为"学员 #id"，不影响榜单排序。
     */
    public List<Map<String, Object>> rank(int top, Long currentUserId) {
        int limit = Math.min(Math.max(top, 1), 50);
        List<Map<String, Object>> rows = pointsRecordMapper.selectRanking();
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> head = rows.size() > limit ? rows.subList(0, limit) : rows;

        List<Long> ids = head.stream()
                .map(r -> longOf(r.get("userId")))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        Map<Long, String> names = safeQueryNames(ids);

        List<Map<String, Object>> list = new ArrayList<>(head.size());
        int index = 0;
        for (Map<String, Object> row : head) {
            index++;
            Long uid = longOf(row.get("userId"));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rank", index);
            m.put("userId", uid);
            m.put("name", names.getOrDefault(uid, uid == null ? "未知学员" : "学员 #" + uid));
            m.put("points", longOf(row.get("total")) == null ? 0L : longOf(row.get("total")));
            m.put("me", uid != null && uid.equals(currentUserId));
            list.add(m);
        }
        return list;
    }

    /**
     * 我的排名条目（若已在前 N 名内，则直接复用榜单中的那一条，保证"本人排名"与榜单一致）。
     */
    public Map<String, Object> myRankEntry(Long userId, List<Map<String, Object>> topList) {
        for (Map<String, Object> item : topList) {
            if (Boolean.TRUE.equals(item.get("me"))) {
                return item;
            }
        }
        long total = total(userId);
        Long higher = pointsRecordMapper.countUsersAbove(total);
        Map<Long, String> names = safeQueryNames(Collections.singletonList(userId));
        Map<String, Object> me = new LinkedHashMap<>();
        me.put("rank", total > 0 ? nz(higher) + 1 : 0);
        me.put("userId", userId);
        me.put("name", names.getOrDefault(userId, "我"));
        me.put("points", total);
        me.put("me", true);
        return me;
    }

    /* ==================== 个人中心：积分明细 ==================== */

    /**
     * 积分明细分页（按时间倒序）。
     */
    public PageDTO<Map<String, Object>> records(Long userId, PageQuery query) {
        Page<PointsRecord> page = pointsRecordMapper.selectPage(
                query.toMpPage("create_time", false),
                new LambdaQueryWrapper<PointsRecord>().eq(PointsRecord::getUserId, userId));
        List<Map<String, Object>> list = page.getRecords().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("points", r.getPoints());
            m.put("source", r.getSource());
            m.put("sourceText", SOURCE_TEXT.getOrDefault(r.getSource(), "积分变动"));
            m.put("description", r.getDescription());
            m.put("refId", r.getRefId());
            m.put("createTime", r.getCreateTime() == null ? null : TIME_FMT.format(r.getCreateTime()));
            return m;
        }).collect(Collectors.toList());
        return PageDTO.of(page, list);
    }

    /* ==================== 私有 ==================== */

    /** 批量补全用户昵称：优先真实姓名，其次登录名；用户服务异常时降级为空映射 */
    private Map<Long, String> safeQueryNames(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new HashMap<>();
        }
        try {
            List<UserDTO> users = userClient.queryUserByIds(userIds.stream().distinct().toList());
            if (users == null) {
                return new HashMap<>();
            }
            return users.stream()
                    .filter(u -> u.getId() != null)
                    .collect(Collectors.toMap(UserDTO::getId,
                            u -> u.getName() != null && !u.getName().isBlank()
                                    ? u.getName()
                                    : (u.getUsername() == null ? "" : u.getUsername()),
                            (a, b) -> a, HashMap::new));
        } catch (Exception e) {
            log.warn("排行榜昵称补全失败（用户服务不可用，降级为占位名称）: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private static long nz(Long v) {
        return v == null ? 0L : v;
    }

    private static Long longOf(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v instanceof String s && !s.isBlank()) {
            try {
                return Long.valueOf(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
