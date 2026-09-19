package com.zhixing.message.controller;

import com.zhixing.common.annotation.RequireRole;
import com.zhixing.common.constants.UserRole;
import com.zhixing.common.domain.R;
import com.zhixing.common.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 短信与收件箱
 * <p>
 * 权限：短信发送与站内信下发为管理端操作，仅员工(1)；收件箱读写为用户接口，仅要求登录。
 * <p>
 * <b>存储边界（务必知悉）</b>：本服务是<b>进程内存储的演示级实现</b>——无表、无实体、无 Mapper，
 * 数据仅存活于当前 JVM 堆内。重启即清空，多实例部署时各实例互不可见。
 * 如需作为生产级站内信，须补 {@code inbox} 表 + 实体 + Mapper（见 docs/GO-LIVE-AUDIT-2026-09-16.md §3.10）。
 * <p>
 * 收件人语义：请求体带 {@code userId} = 定向投递；不带 = 全员广播。
 * 读取时按当前登录用户过滤，员工(1)可见全部（用于管理端核对下发结果）。
 */
@Slf4j
@RestController
public class SmsController {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 站内信存储：id -> 消息体（含 id/userId/title/content/read/createTime） */
    private final Map<Long, Map<String, Object>> inboxes = new ConcurrentHashMap<>();

    private final AtomicLong idGen = new AtomicLong(1);

    /**
     * 发送短信。
     * <p>
     * ⚠ 不能标 {@code @Async}：Spring 的异步拦截器只支持 void / Future 返回值，
     * 方法返回 R 时会在 {@code AsyncExecutionAspectSupport.doSubmit} 抛
     * IllegalArgumentException（"Invalid return type for async method"），
     * 导致该接口**必然 500「系统繁忙」**。且 Controller 的返回值需要同步回给
     * 调用方，异步也无意义。
     */
    @PostMapping("/sms/message")
    @RequireRole(UserRole.STAFF)
    public R<Void> sendSms(@RequestBody Map<String, Object> message) {
        log.info("发送短信：{}", message);
        return R.ok();
    }

    /**
     * 下发站内信（管理端）。
     *
     * @param message 含 content，可选 title / userId（缺省为全员广播）
     * @return 站内信 id
     */
    @PostMapping("/inboxes")
    @RequireRole(UserRole.STAFF)
    public R<Long> sendInbox(@RequestBody Map<String, Object> message) {
        Long id = idGen.getAndIncrement();
        message.put("id", id);
        // 新消息一律未读：显式落 read=false，避免前端 unreadCount 依赖 undefined 的隐式语义
        message.put("read", Boolean.FALSE);
        message.putIfAbsent("createTime", LocalDateTime.now().format(TS));
        inboxes.put(id, message);
        log.info("发送站内信：id={} 收件人={}", id, message.get("userId") == null ? "全员" : message.get("userId"));
        return R.ok(id);
    }

    /**
     * 当前用户的收件箱（按 id 倒序，新消息在前）。
     * <p>
     * 历史缺陷：该接口曾无差别返回 {@code inboxes.values()}，即<b>任何登录用户都能读到全部用户的消息</b>。
     * 站内信虽非敏感数据，但收件箱按用户隔离是基本语义，且定向投递的运营内容不应串号。
     */
    @GetMapping("/inboxes")
    public R<List<Map<String, Object>>> inboxes() {
        Long uid = UserContext.getUserId();
        boolean staff = UserContext.hasRole(UserRole.STAFF.getCode());
        List<Map<String, Object>> list = inboxes.values().stream()
                .filter(m -> staff || visibleTo(m, uid))
                .sorted((a, b) -> Long.compare(idOrZero(b), idOrZero(a)))
                .collect(Collectors.toList());
        return R.ok(list);
    }

    /**
     * 标记单条已读。
     * <p>
     * 该端点此前<b>根本不存在</b>（前端 api/message.ts 已调用、mock 也已实现，真实后端却缺），
     * 表现为消息页点「标记已读」静默无反应——前端 catch 块空实现吞掉了 404，用户看不到任何反馈。
     */
    @PostMapping("/inboxes/read")
    public R<Void> readInbox(@RequestBody(required = false) Map<String, Object> body) {
        Long id = body == null ? null : asLong(body.get("id") != null ? body.get("id") : body.get("inboxId"));
        if (id == null) {
            log.warn("标记已读缺少 id：uid={}", UserContext.getUserId());
            return R.ok();
        }
        Map<String, Object> m = inboxes.get(id);
        if (m == null) {
            log.warn("标记已读：站内信不存在 id={} uid={}", id, UserContext.getUserId());
            return R.ok();
        }
        // 越权防护：只能标记自己可见的消息，否则静默不生效（不泄露消息是否存在）
        if (!UserContext.hasRole(UserRole.STAFF.getCode()) && !visibleTo(m, UserContext.getUserId())) {
            log.warn("标记已读被拒：消息 id={} 不属于用户 uid={}", id, UserContext.getUserId());
            return R.ok();
        }
        m.put("read", Boolean.TRUE);
        return R.ok();
    }

    /** 全部标已读（仅作用于当前用户可见的消息）。 */
    @PostMapping("/inboxes/read-all")
    public R<Void> readAllInbox() {
        Long uid = UserContext.getUserId();
        boolean staff = UserContext.hasRole(UserRole.STAFF.getCode());
        int n = 0;
        for (Map<String, Object> m : inboxes.values()) {
            if (staff || visibleTo(m, uid)) {
                m.put("read", Boolean.TRUE);
                n++;
            }
        }
        log.info("站内信全部已读：uid={} 影响条数={}", uid, n);
        return R.ok();
    }

    /** 消息对指定用户是否可见：定向投递看 userId，无 userId 视为全员广播 */
    private static boolean visibleTo(Map<String, Object> m, Long uid) {
        Long owner = asLong(m.get("userId"));
        return owner == null || owner.equals(uid);
    }

    /** 排序用：缺 id 视为 0（排最后） */
    private static long idOrZero(Map<String, Object> m) {
        Long id = asLong(m.get("id"));
        return id == null ? 0L : id;
    }

    /**
     * 宽松数字解析：请求体来自 JSON，可能是 Integer/Long/String。
     * 解析失败返回 null（而非 0），避免"用户 0"这种不存在的身份误判。
     */
    private static Long asLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
