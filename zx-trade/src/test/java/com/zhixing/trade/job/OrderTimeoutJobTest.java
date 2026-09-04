package com.zhixing.trade.job;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhixing.trade.domain.po.Order;
import com.zhixing.trade.mapper.OrderMapper;
import com.zhixing.trade.service.OrderService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 超时关单兜底扫描单测（MQ 延迟消息丢失时的自愈路径）：
 * 命中即关单、单笔失败不阻塞其余、无超时订单零动作、开关关闭直接跳过。
 */
@ExtendWith(MockitoExtension.class)
class OrderTimeoutJobTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderTimeoutJob job;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(job, "expireMinutes", 15);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Order.class);
    }

    private Order order(long id) {
        Order o = new Order();
        o.setId(id);
        o.setStatus(0);
        return o;
    }

    @Test
    void closesEveryExpiredUnpaidOrder() {
        when(orderMapper.selectList(any())).thenReturn(List.of(order(1L), order(2L)));

        job.closeExpiredOrders();

        verify(orderService).closeExpired(1L);
        verify(orderService).closeExpired(2L);
    }

    @Test
    void continuesOnSingleCloseFailure() {
        // 第一笔关单抛异常：兜底任务捕获并继续处理后续订单，不能被单笔脏数据卡死
        when(orderMapper.selectList(any())).thenReturn(List.of(order(1L), order(2L)));
        doThrow(new RuntimeException("db down")).when(orderService).closeExpired(1L);

        job.closeExpiredOrders();

        verify(orderService).closeExpired(2L);
    }

    @Test
    void doesNothingWhenNoExpiredOrders() {
        when(orderMapper.selectList(any())).thenReturn(List.of());

        job.closeExpiredOrders();

        verify(orderService, never()).closeExpired(anyLong());
    }

    @Test
    void skipsScanWhenDisabled() {
        // expire-minutes < 0 视为关闭兜底扫描（如灰度只依赖延迟消息）
        ReflectionTestUtils.setField(job, "expireMinutes", -1);

        job.closeExpiredOrders();

        verify(orderMapper, never()).selectList(any());
        verify(orderService, never()).closeExpired(anyLong());
    }
}
