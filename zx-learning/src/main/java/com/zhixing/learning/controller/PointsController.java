package com.zhixing.learning.controller;

import com.zhixing.api.dto.learning.PointsAwardDTO;
import com.zhixing.common.annotation.NoWrapper;
import com.zhixing.common.annotation.RequireRole;
import com.zhixing.common.constants.UserRole;
import com.zhixing.common.domain.PageDTO;
import com.zhixing.common.domain.PageQuery;
import com.zhixing.common.domain.R;
import com.zhixing.common.utils.InternalOnlyGuard;
import com.zhixing.common.utils.UserContext;
import com.zhixing.learning.service.PointsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 积分中心（个人中心「我的数据」）。
 * <p>
 * 权限：
 * <ul>
 *   <li>概览 / 排行榜 / 明细 —— 学员端个人数据，仅学员(2)可访问；</li>
 *   <li>加分 / 总额查询 —— 标注 {@code @NoWrapper} 的服务间内部接口，
 *       经网关的外部请求（含管理员）一律 403。</li>
 * </ul>
 * 所有加分均由服务端触发（完成学习、签到、测验、讨论），前端无写积分入口，杜绝刷分。
 */
@RestController
@RequestMapping("/points")
@RequiredArgsConstructor
public class PointsController {

    private final PointsService pointsService;

    // ============ 学员端（个人中心） ============

    /**
     * 我的积分概况：总额 / 本人排名 / 参与人数 / 今日与近 7 日增量。
     */
    @GetMapping("/summary")
    @RequireRole(UserRole.STUDENT)
    public R<Map<String, Object>> summary() {
        return R.ok(pointsService.summary(UserContext.getUserId()));
    }

    /**
     * 学习积分排行榜：前 N 名 + 本人排名条目。
     * <p>
     * 本人已在前 N 名内时 {@code me} 与榜单中的对应条目一致（同样的名次与积分）；
     * 未进前 N 名时单独给出本人名次，前端可"钉"在榜尾展示。
     */
    @GetMapping("/rank")
    @RequireRole(UserRole.STUDENT)
    public R<Map<String, Object>> rank(@RequestParam(value = "top", defaultValue = "10") Integer top) {
        Long userId = UserContext.getUserId();
        List<Map<String, Object>> list = pointsService.rank(top == null ? 10 : top, userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("top", list);
        result.put("me", pointsService.myRankEntry(userId, list));
        return R.ok(result);
    }

    /**
     * 积分明细分页（按时间倒序）。
     */
    @GetMapping("/records/page")
    @RequireRole(UserRole.STUDENT)
    public R<PageDTO<Map<String, Object>>> records(PageQuery query) {
        return R.ok(pointsService.records(UserContext.getUserId(), query));
    }

    // ============ 服务间内部接口（Feign 直连，外部经网关一律 403） ============

    /**
     * 授予积分（幂等）。供 zx-exam 等服务在服务端判分后调用。
     *
     * @return 本次是否真正入账
     */
    @PostMapping("/award")
    @NoWrapper
    public Boolean award(@RequestBody PointsAwardDTO award) {
        InternalOnlyGuard.checkInternal();
        if (award == null) {
            return false;
        }
        return pointsService.award(award.getUserId(), award.getSource(),
                award.getPoints() == null ? 0 : award.getPoints(),
                award.getDescription(), award.getRefId());
    }

    /**
     * 查询指定用户积分总额（内部 Feign 接口）
     */
    @GetMapping("/users/{userId}/total")
    @NoWrapper
    public Long totalOf(@PathVariable("userId") Long userId) {
        InternalOnlyGuard.checkInternal();
        return pointsService.total(userId);
    }
}
