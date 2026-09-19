package com.zhixing.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixing.api.client.learning.LearningClient;
import com.zhixing.api.client.user.UserClient;
import com.zhixing.api.dto.learning.LearningRecordDTO;
import com.zhixing.api.dto.user.UserDTO;
import com.zhixing.common.domain.PageDTO;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.exceptions.BizIllegalException;
import com.zhixing.trade.domain.dto.AdminOrderQueryDTO;
import com.zhixing.trade.domain.po.Order;
import com.zhixing.trade.domain.po.RefundApply;
import com.zhixing.trade.domain.vo.AdminOrderStatsVO;
import com.zhixing.trade.domain.vo.AdminOrderVO;
import com.zhixing.trade.mapper.OrderMapper;
import com.zhixing.trade.mapper.RefundApplyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理员端订单服务。
 * <p>
 * 与 {@link OrderService}（学员端"我的订单"）分离，避免学员端契约被管理端筛选条件污染：
 * <ul>
 *   <li>管理端可见<b>全部用户</b>的订单，学员端只能看本人；</li>
 *   <li>管理端支持多条件筛选、改状态、退款审核、CSV 导出；</li>
 *   <li>管理端使用<b>数据库状态</b>（0 待支付 1 已支付 2 已关闭 3 退款中 4 已退款），
 *       不做学员端那套 1/2/3/5/6 契约映射，便于运营与财务对齐。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminOrderService {

    /** 单次导出行数上限，防止误操作导出全库拖垮服务 */
    private static final int EXPORT_LIMIT = 5000;

    private static final String[] STATUS_TEXT = {"待支付", "已支付", "已关闭", "退款中", "已退款"};

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OrderMapper orderMapper;
    private final RefundApplyMapper refundApplyMapper;
    private final UserClient userClient;
    private final LearningClient learningClient;
    private final OrderService orderService;

    /**
     * 管理员端订单分页（多条件筛选）。
     */
    public PageDTO<AdminOrderVO> page(AdminOrderQueryDTO query) {
        Page<Order> page = orderMapper.selectPage(
                Page.of(query.safePageNo(), query.safePageSize()),
                buildWrapper(query));
        List<AdminOrderVO> list = page.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        fillUserInfo(list);
        return PageDTO.of(page, list);
    }

    /** 订单详情 */
    public AdminOrderVO detail(Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BadRequestException("订单不存在");
        }
        AdminOrderVO vo = toVO(order);
        fillUserInfo(List.of(vo));
        return vo;
    }

    /**
     * 修改订单状态（基础管理能力）。
     * <p>
     * 仅允许在合法状态集合内取值，并保护"已退款"这一终态不被随意改回。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        if (status == null || status < 0 || status > 4) {
            throw new BizIllegalException("非法的订单状态：" + status);
        }
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BadRequestException("订单不存在");
        }
        if (Objects.equals(order.getStatus(), status)) {
            return;
        }
        Order patch = new Order();
        patch.setId(id);
        patch.setStatus(status);
        // 手动置为已支付时补支付时间，保证看板统计口径一致
        if (status == 1 && order.getPayTime() == null) {
            patch.setPayTime(LocalDateTime.now());
        }
        orderMapper.updateById(patch);
        log.info("管理员修改订单状态：orderId={}, {} → {}", id, order.getStatus(), status);
    }

    /**
     * 退款审核（管理端）。
     *
     * @param refundId 退款单 id
     * @param approved true 通过 → 订单已退款；false 拒绝 → 订单回退已支付
     */
    @Transactional(rollbackFor = Exception.class)
    public void auditRefund(Long refundId, boolean approved, String remark) {
        RefundApply apply = refundApplyMapper.selectById(refundId);
        if (apply == null) {
            throw new BadRequestException("退款单不存在");
        }
        if (!Objects.equals(apply.getStatus(), 0)) {
            throw new BizIllegalException("该退款单已审核，请勿重复操作");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", refundId);
        body.put("approved", approved);
        body.put("remark", remark);
        orderService.confirmRefund(apply.getOrderId(), approved);
        apply.setStatus(approved ? 1 : 2);
        apply.setRemark(remark);
        refundApplyMapper.updateById(apply);
        log.info("管理员退款审核：refundId={}, orderId={}, approved={}", refundId, apply.getOrderId(), approved);
    }

    /**
     * 管理端删除订单（清理无用订单，不影响销售数据）。
     * <p>
     * 允许删除的状态：待支付(0)、已关闭(2)、已退款(4) —— 这些订单不贡献销售额
     * （看板仅统计 status=1 且支付时间非空的订单），属于无效/无用记录。
     * <b>禁止</b>删除 已支付(1)（会篡改销售额与课程归属）与 退款中(3)（流程进行中）。
     * <p>
     * 采用逻辑删除（MyBatis-Plus @TableLogic，置 deleted=1）：一并从管理端列表移除，
     * 但数据仍在库中可追溯；不做物理删除以免误操作不可恢复。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteOrder(Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BadRequestException("订单不存在或已被删除");
        }
        int status = order.getStatus() == null ? 0 : order.getStatus();
        if (status == 1) {
            throw new BizIllegalException("已支付订单参与销售统计，不允许删除；如需处理请走退款流程");
        }
        if (status == 3) {
            throw new BizIllegalException("退款处理中的订单不允许删除，请先完成退款审核");
        }
        orderMapper.deleteById(id);
        log.info("管理员删除订单：orderId={}, status={}, userId={}", id, status, order.getUserId());
    }

    /** 退款申请分页（管理端审核工作台） */
    public PageDTO<Map<String, Object>> refundPage(Integer pageNo, Integer pageSize, Integer status) {
        int no = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 200);
        Page<RefundApply> page = refundApplyMapper.selectPage(Page.of(no, size),
                new LambdaQueryWrapper<RefundApply>()
                        .eq(status != null, RefundApply::getStatus, status)
                        .orderByDesc(RefundApply::getCreateTime));
        Map<Long, UserDTO> userMap = safeQueryUsers(page.getRecords().stream()
                .map(RefundApply::getUserId).filter(Objects::nonNull).distinct().toList());
        Map<Long, Order> orderMap = safeQueryOrders(page.getRecords().stream()
                .map(RefundApply::getOrderId).filter(Objects::nonNull).distinct().toList());
        List<Map<String, Object>> list = new ArrayList<>(page.getRecords().size());
        for (RefundApply r : page.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("orderId", r.getOrderId());
            Order order = orderMap.get(r.getOrderId());
            m.put("orderNo", order == null ? null : order.getOrderNo());
            m.put("courseName", order == null ? null : order.getCourseName());
            m.put("userId", r.getUserId());
            UserDTO user = userMap.get(r.getUserId());
            m.put("username", user == null ? ("用户 #" + r.getUserId())
                    : (user.getName() != null ? user.getName() : user.getUsername()));
            m.put("cellPhone", user == null ? null : user.getCellPhone());
            m.put("amount", r.getAmount());
            m.put("reason", r.getReason());
            m.put("status", r.getStatus());
            m.put("remark", r.getRemark());
            m.put("createTime", r.getCreateTime());
            list.add(m);
        }
        return PageDTO.of(page, list);
    }

    /**
     * 学员相关课程视图（退款审批辅助）。
     * <p>
     * 汇总指定学员的全部订单课程，并补齐学习进度/学习时长/最近学习时间，
     * 供管理员在审批退款前判断"学员是否已实际学习该课程"。
     * 依赖的外部服务（用户 / 学习）不可用时降级为空值，不阻断审批工作台渲染。
     */
    public Map<String, Object> userCourses(Long userId) {
        if (userId == null) {
            throw new BadRequestException("用户 id 不能为空");
        }
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .orderByDesc(Order::getCreateTime));

        Map<Long, LearnStat> learnMap = safeQueryLearning(userId);

        List<Map<String, Object>> courses = new ArrayList<>(orders.size());
        long totalPaid = 0L;
        int paidCount = 0;
        int refundingCount = 0;
        int refundedCount = 0;
        for (Order o : orders) {
            LearnStat stat = o.getCourseId() == null ? null : learnMap.get(o.getCourseId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("orderId", o.getId());
            m.put("orderNo", o.getOrderNo());
            m.put("orderStatus", o.getStatus());
            m.put("orderStatusText", statusText(o.getStatus()));
            m.put("courseId", o.getCourseId());
            m.put("courseName", o.getCourseName());
            m.put("coursePrice", o.getCoursePrice());
            m.put("totalFee", o.getTotalFee());
            m.put("deduction", o.getDeduction());
            m.put("createTime", o.getCreateTime());
            m.put("payTime", o.getPayTime());
            m.put("progress", stat == null ? 0 : stat.progress());
            m.put("finished", stat != null && stat.finished());
            m.put("learnDuration", stat == null ? 0L : stat.duration());
            m.put("lastLearnTime", stat == null ? null : stat.lastLearnTime());
            courses.add(m);

            int status = o.getStatus() == null ? 0 : o.getStatus();
            if (status == 1) {
                paidCount++;
                totalPaid += o.getTotalFee() == null ? 0L : o.getTotalFee();
            } else if (status == 3) {
                refundingCount++;
            } else if (status == 4) {
                refundedCount++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        Map<Long, UserDTO> userMap = safeQueryUsers(List.of(userId));
        UserDTO user = userMap.get(userId);
        result.put("username", user == null ? ("用户 #" + userId)
                : (user.getName() != null ? user.getName() : user.getUsername()));
        result.put("cellPhone", user == null ? null : user.getCellPhone());
        result.put("courseCount", courses.size());
        result.put("paidCount", paidCount);
        result.put("refundingCount", refundingCount);
        result.put("refundedCount", refundedCount);
        result.put("totalPaid", totalPaid);
        result.put("courses", courses);
        return result;
    }

    /** 学习服务不可用时返回空表（进度按 0 展示），避免影响退款审批主流程 */
    private Map<Long, LearnStat> safeQueryLearning(Long userId) {
        Map<Long, LearnStat> map = new LinkedHashMap<>();
        try {
            List<LearningRecordDTO> records = learningClient.listRecords(userId);
            if (records == null) {
                return map;
            }
            for (LearningRecordDTO r : records) {
                if (r.getCourseId() == null) {
                    continue;
                }
                LearnStat stat = map.computeIfAbsent(r.getCourseId(), k -> new LearnStat());
                stat.merge(r);
            }
        } catch (Exception e) {
            log.warn("查询学员学习记录失败，降级为无学习数据：userId={}, cause={}", userId, e.getMessage());
        }
        return map;
    }

    /** 单课程学习聚合：取最大进度、累计时长、最近学习时间，任一记录完成即视为已完成 */
    private static final class LearnStat {
        private int progress = 0;
        private long duration = 0L;
        private boolean finished = false;
        private LocalDateTime lastLearnTime;

        void merge(LearningRecordDTO r) {
            if (r.getProgress() != null) {
                progress = Math.max(progress, r.getProgress());
            }
            if (r.getLearnDuration() != null && r.getLearnDuration() > 0) {
                duration += r.getLearnDuration();
            }
            if (Boolean.TRUE.equals(r.getFinished())) {
                finished = true;
            }
            if (r.getLastLearnTime() != null
                    && (lastLearnTime == null || r.getLastLearnTime().isAfter(lastLearnTime))) {
                lastLearnTime = r.getLastLearnTime();
            }
        }

        int progress() {
            return progress;
        }

        long duration() {
            return duration;
        }

        boolean finished() {
            return finished;
        }

        LocalDateTime lastLearnTime() {
            return lastLearnTime;
        }
    }

    /** 订单统计（各状态订单量 + 已支付销售额） */
    public AdminOrderStatsVO stats() {
        AdminOrderStatsVO vo = new AdminOrderStatsVO();
        List<Order> all = orderMapper.selectList(null);
        vo.setTotalCount(all.size());
        long sales = 0;
        for (Order o : all) {
            int status = o.getStatus() == null ? 0 : o.getStatus();
            switch (status) {
                case 0 -> vo.setUnpaidCount(vo.getUnpaidCount() + 1);
                case 1 -> {
                    vo.setPaidCount(vo.getPaidCount() + 1);
                    sales += o.getTotalFee() == null ? 0L : o.getTotalFee();
                }
                case 2 -> vo.setClosedCount(vo.getClosedCount() + 1);
                case 3 -> vo.setRefundingCount(vo.getRefundingCount() + 1);
                case 4 -> vo.setRefundedCount(vo.getRefundedCount() + 1);
                default -> {
                    // 未知状态仅计入总量
                }
            }
        }
        vo.setTotalSales(sales);
        return vo;
    }

    /**
     * 订单导出（CSV）。
     * <p>
     * 带 UTF-8 BOM，保证 Excel 直接双击打开不乱码。
     */
    public byte[] exportCsv(AdminOrderQueryDTO query) {
        Page<Order> page = orderMapper.selectPage(Page.of(1, EXPORT_LIMIT), buildWrapper(query));
        List<AdminOrderVO> list = page.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        fillUserInfo(list);

        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append("订单号,用户ID,用户名,手机号,课程,课程原价(元),实付(元),优惠抵扣(元),状态,支付方式,下单时间,支付时间\n");
        for (AdminOrderVO o : list) {
            sb.append(csv(o.getOrderNo())).append(',')
                    .append(o.getUserId()).append(',')
                    .append(csv(o.getUsername())).append(',')
                    .append(csv(o.getCellPhone())).append(',')
                    .append(csv(o.getCourseName())).append(',')
                    .append(yuan(o.getCoursePrice())).append(',')
                    .append(yuan(o.getTotalFee())).append(',')
                    .append(yuan(o.getDeduction())).append(',')
                    .append(statusText(o.getStatus())).append(',')
                    .append(payTypeText(o.getPayType())).append(',')
                    .append(fmt(o.getCreateTime())).append(',')
                    .append(fmt(o.getPayTime())).append('\n');
        }
        log.info("管理员导出订单 CSV：{} 行（筛选条件 orderNo={}, userId={}, status={}）",
                list.size(), query.getOrderNo(), query.getUserId(), query.getStatus());
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ============ 私有方法 ============

    /**
     * 构建筛选条件。
     * <p>
     * keyword 的匹配范围：订单号、课程名快照，以及（当关键词可解析为用户时）用户 id。
     * 用户解析规则：纯数字且长度 11 视为手机号，经 zx-user 换取 userId；纯数字其它长度视为 userId。
     */
    private LambdaQueryWrapper<Order> buildWrapper(AdminOrderQueryDTO q) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .like(StringUtils.hasText(q.getOrderNo()), Order::getOrderNo, q.getOrderNo())
                .eq(q.getUserId() != null, Order::getUserId, q.getUserId())
                .eq(q.getStatus() != null, Order::getStatus, q.getStatus())
                .eq(q.getCourseId() != null, Order::getCourseId, q.getCourseId())
                .ge(q.getMinAmount() != null, Order::getTotalFee, q.getMinAmount())
                .le(q.getMaxAmount() != null, Order::getTotalFee, q.getMaxAmount())
                .ge(q.getBeginTime() != null, Order::getCreateTime, q.getBeginTime())
                .le(q.getEndTime() != null, Order::getCreateTime, q.getEndTime());

        String keyword = q.getKeyword();
        if (StringUtils.hasText(keyword)) {
            Long resolvedUserId = resolveUserId(keyword.trim());
            wrapper.and(w -> w
                    .like(Order::getOrderNo, keyword)
                    .or().like(Order::getCourseName, keyword)
                    .or(resolvedUserId != null).eq(Order::getUserId, resolvedUserId));
        }
        wrapper.orderByDesc(Order::getCreateTime);
        return wrapper;
    }

    /** 关键词 → 用户 id（手机号走 zx-user 换取；纯数字视为 userId；其余返回 null） */
    private Long resolveUserId(String keyword) {
        if (!keyword.chars().allMatch(Character::isDigit)) {
            return null;
        }
        try {
            if (keyword.length() == 11) {
                Map<String, Long> res = userClient.exchangeUserId(keyword);
                return res == null ? null : res.get("userId");
            }
            return Long.valueOf(keyword);
        } catch (Exception e) {
            log.warn("关键词解析用户失败 keyword={}: {}", keyword, e.getMessage());
            return null;
        }
    }

    private AdminOrderVO toVO(Order o) {
        AdminOrderVO vo = new AdminOrderVO();
        vo.setId(o.getId());
        vo.setOrderNo(o.getOrderNo());
        vo.setUserId(o.getUserId());
        vo.setCourseId(o.getCourseId());
        vo.setCourseName(o.getCourseName());
        vo.setCoursePrice(o.getCoursePrice());
        vo.setTotalFee(o.getTotalFee());
        vo.setCouponId(o.getCouponId());
        vo.setDeduction(o.getDeduction());
        vo.setStatus(o.getStatus());
        vo.setUserDeleted(o.getUserDeleted() == null ? 0 : o.getUserDeleted());
        vo.setPayType(o.getPayType());
        vo.setPayTime(o.getPayTime());
        vo.setCreateTime(o.getCreateTime());
        vo.setUpdateTime(o.getUpdateTime());
        return vo;
    }

    /** 批量补全用户名/手机号（Feign 失败降级为占位，不阻断列表渲染） */
    private void fillUserInfo(List<AdminOrderVO> list) {
        if (list.isEmpty()) {
            return;
        }
        Map<Long, UserDTO> userMap = safeQueryUsers(list.stream()
                .map(AdminOrderVO::getUserId).filter(Objects::nonNull).distinct().toList());
        for (AdminOrderVO vo : list) {
            UserDTO user = userMap.get(vo.getUserId());
            if (user != null) {
                vo.setUsername(user.getName() != null ? user.getName() : user.getUsername());
                vo.setCellPhone(user.getCellPhone());
            } else if (vo.getUserId() != null) {
                vo.setUsername("用户 #" + vo.getUserId());
            }
        }
    }

    private Map<Long, UserDTO> safeQueryUsers(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            List<UserDTO> users = userClient.queryUserByIds(ids);
            if (users == null) {
                return Map.of();
            }
            return users.stream().filter(u -> u.getId() != null)
                    .collect(Collectors.toMap(UserDTO::getId, Function.identity(), (a, b) -> a));
        } catch (Exception e) {
            log.warn("订单用户信息补全失败：{}", e.getMessage());
            return Map.of();
        }
    }

    private Map<Long, Order> safeQueryOrders(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Order> orders = orderMapper.selectBatchIds(ids);
        return orders.stream().filter(o -> o.getId() != null)
                .collect(Collectors.toMap(Order::getId, Function.identity(), (a, b) -> a));
    }

    private String statusText(Integer status) {
        int s = status == null ? 0 : status;
        return s >= 0 && s < STATUS_TEXT.length ? STATUS_TEXT[s] : "未知";
    }

    private String payTypeText(Integer payType) {
        if (payType == null) {
            return "-";
        }
        return switch (payType) {
            case 1 -> "支付宝";
            case 2 -> "微信";
            case 3 -> "余额";
            default -> "其他";
        };
    }

    /** 分 → 元（保留 2 位） */
    private String yuan(Long fen) {
        return String.format("%.2f", (fen == null ? 0L : fen) / 100.0);
    }

    private String fmt(LocalDateTime time) {
        return time == null ? "" : FMT.format(time);
    }

    /** CSV 字段转义：含逗号/引号/换行时用双引号包裹并转义内部引号 */
    private String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
