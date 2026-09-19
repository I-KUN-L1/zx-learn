package com.zhixing.trade.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixing.api.client.user.UserClient;
import com.zhixing.api.dto.user.UserDTO;
import com.zhixing.common.domain.PageDTO;
import com.zhixing.common.exceptions.BizIllegalException;
import com.zhixing.trade.domain.dto.AdminOrderQueryDTO;
import com.zhixing.trade.domain.po.Order;
import com.zhixing.trade.domain.po.RefundApply;
import com.zhixing.trade.domain.vo.AdminOrderStatsVO;
import com.zhixing.trade.domain.vo.AdminOrderVO;
import com.zhixing.trade.mapper.OrderMapper;
import com.zhixing.trade.mapper.RefundApplyMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理员端订单服务单测：多条件筛选分页 / 状态流转校验 / 统计口径 / CSV 导出 / 退款审核幂等。
 */
@ExtendWith(MockitoExtension.class)
class AdminOrderServiceTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private RefundApplyMapper refundApplyMapper;
    @Mock
    private UserClient userClient;
    @Mock
    private OrderService orderService;

    @InjectMocks
    private AdminOrderService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Order.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), RefundApply.class);
    }

    private Order order(Long id, Long userId, int status, long fee) {
        Order o = new Order();
        o.setId(id);
        o.setOrderNo("T" + id);
        o.setUserId(userId);
        o.setCourseId(3001L);
        o.setCourseName("Java 21 核心技术");
        o.setCoursePrice(fee);
        o.setTotalFee(fee);
        o.setDeduction(0L);
        o.setStatus(status);
        o.setPayType(1);
        return o;
    }

    @Test
    void pageEnrichesUsername() {
        Page<Order> mpPage = new Page<>(1, 10);
        mpPage.setRecords(List.of(order(1L, 2001L, 1, 19900L)));
        mpPage.setTotal(1);
        when(orderMapper.selectPage(any(), any())).thenReturn(mpPage);
        UserDTO u = new UserDTO();
        u.setId(2001L);
        u.setName("知行学员");
        u.setCellPhone("13900000001");
        when(userClient.queryUserByIds(anyList())).thenReturn(List.of(u));

        AdminOrderQueryDTO query = new AdminOrderQueryDTO();
        PageDTO<AdminOrderVO> res = service.page(query);

        assertEquals(1L, res.getTotal());
        assertEquals("知行学员", res.getList().get(0).getUsername());
        assertEquals("13900000001", res.getList().get(0).getCellPhone());
    }

    @Test
    void pageDegradesWhenUserServiceFails() {
        Page<Order> mpPage = new Page<>(1, 10);
        mpPage.setRecords(List.of(order(1L, 2001L, 1, 19900L)));
        mpPage.setTotal(1);
        when(orderMapper.selectPage(any(), any())).thenReturn(mpPage);
        when(userClient.queryUserByIds(anyList())).thenThrow(new RuntimeException("user-service down"));

        PageDTO<AdminOrderVO> res = service.page(new AdminOrderQueryDTO());

        assertEquals("用户 #2001", res.getList().get(0).getUsername());
    }

    @Test
    void updateStatusRejectsIllegalValue() {
        assertThrows(BizIllegalException.class, () -> service.updateStatus(1L, 9));
        assertThrows(BizIllegalException.class, () -> service.updateStatus(1L, null));
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    @Test
    void updateStatusToPaidFillsPayTime() {
        when(orderMapper.selectById(1L)).thenReturn(order(1L, 2001L, 0, 100L));

        service.updateStatus(1L, 1);

        verify(orderMapper).updateById(any(Order.class));
    }

    @Test
    void updateStatusNoopWhenUnchanged() {
        when(orderMapper.selectById(1L)).thenReturn(order(1L, 2001L, 2, 100L));

        service.updateStatus(1L, 2);

        verify(orderMapper, never()).updateById(any(Order.class));
    }

    @Test
    void statsCountsEveryStatus() {
        when(orderMapper.selectList(any())).thenReturn(List.of(
                order(1L, 2001L, 0, 100L),
                order(2L, 2001L, 1, 200L),
                order(3L, 2002L, 1, 300L),
                order(4L, 2002L, 2, 0L),
                order(5L, 2003L, 3, 400L),
                order(6L, 2003L, 4, 500L)));

        AdminOrderStatsVO stats = service.stats();

        assertEquals(6L, stats.getTotalCount());
        assertEquals(1L, stats.getUnpaidCount());
        assertEquals(2L, stats.getPaidCount());
        assertEquals(1L, stats.getClosedCount());
        assertEquals(1L, stats.getRefundingCount());
        assertEquals(1L, stats.getRefundedCount());
        assertEquals(500L, stats.getTotalSales(), "销售额只统计已支付订单");
    }

    @Test
    void exportCsvHasBomAndEscapesComma() {
        Page<Order> mpPage = new Page<>(1, 5000);
        Order o = order(1L, 2001L, 1, 19900L);
        o.setCourseName("Java,高级特性");
        mpPage.setRecords(List.of(o));
        mpPage.setTotal(1);
        when(orderMapper.selectPage(any(), any())).thenReturn(mpPage);
        when(userClient.queryUserByIds(anyList())).thenReturn(List.of());

        byte[] csv = service.exportCsv(new AdminOrderQueryDTO());
        String text = new String(csv, StandardCharsets.UTF_8);

        assertTrue(text.startsWith("\uFEFF"), "CSV 必须带 UTF-8 BOM，Excel 才不会乱码");
        assertTrue(text.contains("订单号,用户ID,用户名"));
        assertTrue(text.contains("\"Java,高级特性\""), "含逗号字段必须被引号包裹");
        assertTrue(text.contains("已支付"));
        assertTrue(text.contains("199.00"));
    }

    @Test
    void auditRefundUpdatesOrderAndRecord() {
        RefundApply apply = new RefundApply();
        apply.setId(9L);
        apply.setOrderId(1L);
        apply.setStatus(0);
        when(refundApplyMapper.selectById(9L)).thenReturn(apply);

        service.auditRefund(9L, true, "同意退款");

        verify(orderService).confirmRefund(1L, true);
        verify(refundApplyMapper).updateById(any(RefundApply.class));
    }

    @Test
    void auditRefundRejectsDuplicateAudit() {
        RefundApply apply = new RefundApply();
        apply.setId(9L);
        apply.setOrderId(1L);
        apply.setStatus(1);
        when(refundApplyMapper.selectById(9L)).thenReturn(apply);

        assertThrows(BizIllegalException.class, () -> service.auditRefund(9L, true, "重复审核"));
        verify(orderService, never()).confirmRefund(any(), anyBoolean());
    }

    @Test
    void auditRefundRejectsMissingApply() {
        when(refundApplyMapper.selectById(eq(404L))).thenReturn(null);
        assertThrows(com.zhixing.common.exceptions.BadRequestException.class,
                () -> service.auditRefund(404L, true, null));
    }
}
