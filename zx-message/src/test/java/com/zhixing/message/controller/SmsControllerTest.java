package com.zhixing.message.controller;

import com.zhixing.common.constants.UserRole;
import com.zhixing.common.domain.R;
import com.zhixing.common.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SmsController 单元测试。
 * <p>
 * 覆盖本轮修复的两个真实缺陷：
 * <ol>
 *   <li><b>端点缺失</b>：{@code POST /inboxes/read} 与 {@code /inboxes/read-all} 此前在真实后端不存在
 *       （前端 api/message.ts 已调用、mock 也已实现），导致消息页「标记已读 / 一键已读」静默无反应。</li>
 *   <li><b>收件箱未按用户隔离</b>：{@code GET /inboxes} 曾无差别返回全部用户的消息，
 *       任何登录用户都能读到定向投递给别人的内容。</li>
 * </ol>
 * 断言均带对照组：既能通过"应当可见"，也能在隔离被破坏时失败。
 */
class SmsControllerTest {

    private static final long STUDENT_A = 1001L;
    private static final long STUDENT_B = 1002L;
    private static final long ADMIN = 1L;

    private SmsController controller;

    @BeforeEach
    void setUp() {
        controller = new SmsController();
        clear();
    }

    @AfterEach
    void tearDown() {
        clear();
    }

    private void clear() {
        UserContext.remove();
    }

    private void login(Long uid, UserRole role) {
        UserContext.setUser(uid);
        UserContext.setRole(role.getCode());
    }

    private Map<String, Object> body(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    // ---------------- 端点存在性（回归：修复前这几个方法根本不存在） ----------------

    @Test
    void readEndpointsExistAndReturnOk() {
        login(STUDENT_A, UserRole.STUDENT);
        assertTrue(controller.readInbox(body("id", 1)).success(), "/inboxes/read 必须存在且返回 code=200");
        assertTrue(controller.readAllInbox().success(), "/inboxes/read-all 必须存在且返回 code=200");
    }

    @Test
    void readInboxToleratesMissingOrUnknownId() {
        login(STUDENT_A, UserRole.STUDENT);
        // 缺 id / body 为 null / id 不存在：一律幂等成功，不抛异常
        assertTrue(controller.readInbox(null).success());
        assertTrue(controller.readInbox(new HashMap<>()).success());
        assertTrue(controller.readInbox(body("id", 999999)).success());
    }

    @Test
    void readInboxAcceptsStringId() {
        login(ADMIN, UserRole.STAFF);
        Long id = controller.sendInbox(body("content", "x", "userId", STUDENT_A)).getData();
        login(STUDENT_A, UserRole.STUDENT);
        // 前端/JSON 可能把 id 传成字符串，必须同样能命中
        assertTrue(controller.readInbox(body("id", String.valueOf(id))).success());
        assertFalse(inboxOf(STUDENT_A).get(0).get("read").equals(Boolean.FALSE), "字符串 id 也应标记成功");
    }

    // ---------------- 收件箱按用户隔离（修复前：所有人看到全部消息） ----------------

    @Test
    void directedMessageIsInvisibleToOtherUsers() {
        login(ADMIN, UserRole.STAFF);
        controller.sendInbox(body("content", "给 A 的定向消息", "userId", STUDENT_A));

        login(STUDENT_A, UserRole.STUDENT);
        assertEquals(1, inboxOf(STUDENT_A).size(), "A 应看到自己的定向消息");

        login(STUDENT_B, UserRole.STUDENT);
        assertTrue(inboxOf(STUDENT_B).isEmpty(), "B 不得看到投递给 A 的定向消息（隔离被破坏时此断言失败）");
    }

    @Test
    void broadcastMessageIsVisibleToEveryone() {
        login(ADMIN, UserRole.STAFF);
        controller.sendInbox(body("content", "全员公告")); // 不带 userId = 广播

        login(STUDENT_A, UserRole.STUDENT);
        assertEquals(1, inboxOf(STUDENT_A).size(), "广播消息 A 可见");
        login(STUDENT_B, UserRole.STUDENT);
        assertEquals(1, inboxOf(STUDENT_B).size(), "广播消息 B 可见");
    }

    @Test
    void staffSeesAllMessages() {
        login(ADMIN, UserRole.STAFF);
        controller.sendInbox(body("content", "给 A", "userId", STUDENT_A));
        controller.sendInbox(body("content", "给 B", "userId", STUDENT_B));
        controller.sendInbox(body("content", "广播"));

        assertEquals(3, inboxOf(ADMIN).size(), "员工需看到全部消息以核对下发结果");
    }

    // ---------------- 已读语义 ----------------

    @Test
    void sendInboxDefaultsToUnreadAndAssignsId() {
        login(ADMIN, UserRole.STAFF);
        R<Long> r = controller.sendInbox(body("content", "hi", "userId", STUDENT_A));
        assertTrue(r.success());
        assertNotNull(r.getData(), "必须返回站内信 id");

        login(STUDENT_A, UserRole.STUDENT);
        Map<String, Object> m = inboxOf(STUDENT_A).get(0);
        assertInstanceOf(Boolean.class, m.get("read"), "read 必须是布尔（前端 unreadCount 依赖它）");
        assertEquals(Boolean.FALSE, m.get("read"), "新消息必须显式落 read=false");
        assertNotNull(m.get("createTime"), "必须落 createTime");
    }

    @Test
    void readInboxMarksOnlyTargetMessage() {
        login(ADMIN, UserRole.STAFF);
        Long id1 = controller.sendInbox(body("content", "m1")).getData();
        controller.sendInbox(body("content", "m2"));

        login(STUDENT_A, UserRole.STUDENT);
        controller.readInbox(body("id", id1));

        List<Map<String, Object>> list = inboxOf(STUDENT_A);
        Map<String, Object> read1 = list.stream().filter(m -> id1.equals(((Number) m.get("id")).longValue())).findFirst().orElseThrow();
        assertEquals(Boolean.TRUE, read1.get("read"), "指定消息应已读");
        long stillUnread = list.stream().filter(m -> !Boolean.TRUE.equals(m.get("read"))).count();
        assertEquals(1, stillUnread, "另一条必须仍未读");
    }

    @Test
    void cannotMarkAnotherUsersMessageAsRead() {
        login(ADMIN, UserRole.STAFF);
        Long idForA = controller.sendInbox(body("content", "给 A", "userId", STUDENT_A)).getData();

        login(STUDENT_B, UserRole.STUDENT);
        controller.readInbox(body("id", idForA)); // B 尝试标记 A 的消息

        login(STUDENT_A, UserRole.STUDENT);
        assertEquals(Boolean.FALSE, inboxOf(STUDENT_A).get(0).get("read"), "他人不得把自己的消息标记为已读");
    }

    @Test
    void readAllMarksOnlyVisibleMessages() {
        login(ADMIN, UserRole.STAFF);
        controller.sendInbox(body("content", "给 A", "userId", STUDENT_A));
        controller.sendInbox(body("content", "给 B", "userId", STUDENT_B));
        controller.sendInbox(body("content", "广播"));

        // 学生 A 一键已读：自己的定向消息 + 广播 → 2 条
        login(STUDENT_A, UserRole.STUDENT);
        controller.readAllInbox();
        assertTrue(inboxOf(STUDENT_A).stream().allMatch(m -> Boolean.TRUE.equals(m.get("read"))), "A 可见消息应全部已读");

        // 对照组：B 的定向消息不受 A 操作影响
        login(STUDENT_B, UserRole.STUDENT);
        long unread = inboxOf(STUDENT_B).stream().filter(m -> !Boolean.TRUE.equals(m.get("read"))).count();
        assertEquals(1, unread, "A 的「一键已读」不得影响 B 的定向消息");
    }

    // ---------------- 排序 ----------------

    @Test
    void inboxIsNewestFirst() {
        login(ADMIN, UserRole.STAFF);
        controller.sendInbox(body("content", "第一条"));
        controller.sendInbox(body("content", "第二条"));
        controller.sendInbox(body("content", "第三条"));

        login(STUDENT_A, UserRole.STUDENT);
        List<Map<String, Object>> list = inboxOf(STUDENT_A);
        assertEquals(3, list.size());
        assertEquals("第三条", list.get(0).get("content"), "新消息应排在最前");
        assertEquals("第一条", list.get(2).get("content"));
    }

    // ---------------- 辅助 ----------------

    /** 以当前 UserContext 身份读收件箱 */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> inboxOf(Long expectedUid) {
        assertEquals(expectedUid, UserContext.getUserId(), "前置条件：当前上下文用户应为 " + expectedUid);
        return controller.inboxes().getData();
    }
}
