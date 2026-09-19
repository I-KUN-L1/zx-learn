package com.zhixing.search.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.api.client.course.CourseClient;
import com.zhixing.api.dto.course.CourseSimpleInfoDTO;
import com.zhixing.common.annotation.RequireRole;
import com.zhixing.common.constants.UserRole;
import com.zhixing.common.domain.R;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.exceptions.BizIllegalException;
import com.zhixing.common.utils.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 课程搜索 + 推荐。
 *
 * <p><b>数据来源</b>：本服务不再自造数据，全部经 {@link CourseClient} 从 course-service 取
 * <b>真实的已上架课程</b>（内部端点 {@code GET /course/portal}、{@code GET /course/name}）。
 * 历史上这里恒定返回 {@code Map.of("id", 1, "name", "Java 从入门到精通")} 这类示例课程，
 * 与数据库、与上下架状态完全无关 —— 排查「下架后搜索里还能看到课程」时会被严重误导，
 * 因为看到的根本不是真实课程。现已彻底改为真实数据。
 *
 * <p><b>上下架一致性</b>：搜索与推荐位都是面向用户的展示位，只返回 {@code status = 1}（已上架）课程；
 * 课程一旦下架，立即从搜索结果与推荐位消失。过滤由 course-service 的
 * {@code CourseService#portalQuery} 在 SQL 层完成，本服务不重复实现状态判断。
 *
 * <p><b>存储</b>：本地没有 Elasticsearch 索引（生产可替换为 ES），
 * 因此当前实现是对 course-service 的薄代理；日后接入 ES 时，只需替换 controller 的取数方式，
 * 保持「只召回已上架课程」这条不变量即可。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class CourseSearchController {

    /** 门户搜索返回条数上限 */
    private static final int PORTAL_LIMIT = 20;
    /** 推荐位返回条数上限 */
    private static final int RECOMMEND_LIMIT = 8;

    /** 用户学习兴趣在 Redis 中的 key 前缀（按 userId 隔离，避免互相覆盖） */
    private static final String INTEREST_KEY_PREFIX = "search:interest:";
    /** 单用户最多保留的兴趣条目数，超出丢弃最旧的（防无限增长） */
    private static final long MAX_INTERESTS = 20;

    /** 兴趣条目 JSON 序列化器（与 Spring 容器的 ObjectMapper 解耦，避免受全局配置影响） */
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> INTEREST_TYPE = new TypeReference<>() {
    };

    private final CourseClient courseClient;
    private final StringRedisTemplate redisTemplate;

    /**
     * 门户课程搜索（按名称关键字）。
     * 只返回已上架课程；关键字为空时返回最新上架的若干课程。
     */
    @GetMapping("/courses/portal")
    public R<List<CourseSimpleInfoDTO>> portal(@RequestParam(required = false) String keyword) {
        List<CourseSimpleInfoDTO> rows = safe(courseClient.queryPortalCourses(keyword, null, PORTAL_LIMIT));
        log.debug("门户课程检索 keyword={} 命中 {} 条", keyword, rows.size());
        return R.ok(rows);
    }

    /**
     * 按关键字检索课程 id 列表（供其他服务做二次聚合）。
     * 只返回已上架课程的 id，已下架课程不可被检索到。
     */
    @GetMapping("/courses/name")
    public R<List<Long>> name(@RequestParam String keyword) {
        return R.ok(safe(courseClient.queryCourseIdByName(keyword)));
    }

    /** 精品好课：已上架课程按销量倒序 */
    @GetMapping("/recommend/best")
    public R<List<CourseSimpleInfoDTO>> best() {
        return R.ok(safe(courseClient.queryPortalCourses(null, "best", RECOMMEND_LIMIT)));
    }

    /** 新课推荐：已上架课程按最新倒序 */
    @GetMapping("/recommend/new")
    public R<List<CourseSimpleInfoDTO>> newest() {
        return R.ok(safe(courseClient.queryPortalCourses(null, "new", RECOMMEND_LIMIT)));
    }

    /** 免费公开课：已上架且标记免费的课程 */
    @GetMapping("/recommend/free")
    public R<List<CourseSimpleInfoDTO>> free() {
        return R.ok(safe(courseClient.queryPortalCourses(null, "free", RECOMMEND_LIMIT)));
    }

    /**
     * 保存学员的学习兴趣（按用户维度持久化到 Redis）。
     * <p>
     * 原实现只打一行日志、不落任何存储，导致 {@link #interests()} 永远返回空列表 ——
     * 「设置了兴趣但下次打开就没了」。现改为真实存储：同一份兴趣重复提交会去重并置为最新，
     * 最多保留 {@value #MAX_INTERESTS} 条。
     */
    @PostMapping("/interests")
    @RequireRole(UserRole.STUDENT)
    public R<Void> saveInterest(@RequestBody Map<String, Object> interest) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            throw new BadRequestException("未登录，无法保存学习兴趣");
        }
        if (interest == null || interest.isEmpty()) {
            throw new BadRequestException("学习兴趣内容不能为空");
        }
        String key = INTEREST_KEY_PREFIX + userId;
        try {
            String json = JSON.writeValueAsString(interest);
            // 去重：同一份兴趣重复提交不产生重复条目
            redisTemplate.opsForList().remove(key, 0, json);
            redisTemplate.opsForList().rightPush(key, json);
            redisTemplate.opsForList().trim(key, -MAX_INTERESTS, -1);
        } catch (Exception e) {
            log.error("保存学习兴趣失败 userId={}", userId, e);
            throw new BizIllegalException("保存学习兴趣失败，请稍后重试");
        }
        return R.ok();
    }

    /**
     * 读取当前用户已保存的学习兴趣（按保存时间正序）。
     * 未登录或从未保存过时返回空列表。
     */
    @GetMapping("/interests")
    public R<List<Map<String, Object>>> interests() {
        Long userId = UserContext.getUser();
        if (userId == null) {
            return R.ok(List.of());
        }
        List<String> rows = redisTemplate.opsForList().range(INTEREST_KEY_PREFIX + userId, 0, -1);
        if (rows == null || rows.isEmpty()) {
            return R.ok(List.of());
        }
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (String row : rows) {
            try {
                result.add(JSON.readValue(row, INTEREST_TYPE));
            } catch (Exception e) {
                // 单条脏数据不影响其余兴趣条目回显
                log.warn("学习兴趣数据解析失败，已跳过：{}", row);
            }
        }
        return R.ok(result);
    }

    /** Feign 降级会返回空列表，这里统一兜底，避免上游拿到 null 再抛 NPE */
    private static <T> List<T> safe(List<T> rows) {
        return rows == null ? List.of() : rows;
    }
}
