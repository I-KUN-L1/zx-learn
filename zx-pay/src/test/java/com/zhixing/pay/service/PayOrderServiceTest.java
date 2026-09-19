package com.zhixing.pay.service;

import com.zhixing.pay.domain.po.PayNotifyLog;
import com.zhixing.pay.domain.po.PayOrder;
import com.zhixing.pay.mapper.PayNotifyLogMapper;
import com.zhixing.pay.mapper.PayOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * PayOrderService 单元测试：支付单持久化的幂等语义与查询健壮性。
 * <p>
 * 对应全量测试发现的 BUG-007（支付单内存态）/ BUG-008（支付状态误报）：
 * 这里锁定"同业务单只允许一张支付单""查不到就是查不到（不谎报已支付）"
 * "同一笔渠道回调只受理一次"三条不变量。
 */
class PayOrderServiceTest {

    @Mock
    private PayOrderMapper payOrderMapper;

    @Mock
    private PayNotifyLogMapper payNotifyLogMapper;

    private PayOrderService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new PayOrderService(payOrderMapper, payNotifyLogMapper);
    }

    private PayOrder order(Long id, String bizOrderNo, int status) {
        PayOrder o = new PayOrder();
        o.setId(id);
        o.setBizOrderNo(bizOrderNo);
        o.setStatus(status);
        o.setAmount(19900L);
        o.setChannel(PayOrderService.CHANNEL_ALIPAY);
        return o;
    }

    // ------------------------------------------------------------------
    // 申请支付单：幂等（复用而非新建）
    // ------------------------------------------------------------------

    @Test
    void applyOrReuseUpsertsThenReadsBack() {
        PayOrder existing = order(1001L, "BIZ-1", PayOrderService.STATUS_PENDING);
        when(payOrderMapper.selectOne(any())).thenReturn(existing);

        PayOrder result = service.applyOrReuse("BIZ-1", 19900L, PayOrderService.CHANNEL_ALIPAY);

        // 走的是「原子 upsert」，而不是"先查后插"（后者在并发下会撞唯一键）
        verify(payOrderMapper).upsertOnBizOrderNo(any(PayOrder.class));
        assertEquals(1001L, result.getId());
        assertEquals("BIZ-1", result.getBizOrderNo());
    }

    @Test
    void applyOrReuseFillsDefaultsForNullAmountAndChannel() {
        when(payOrderMapper.selectOne(any())).thenReturn(order(1L, "BIZ-2", PayOrderService.STATUS_PENDING));
        service.applyOrReuse("BIZ-2", null, null);

        ArgumentCaptor<PayOrder> captor = ArgumentCaptor.forClass(PayOrder.class);
        verify(payOrderMapper).upsertOnBizOrderNo(captor.capture());
        assertEquals(0L, captor.getValue().getAmount());
        assertEquals(PayOrderService.CHANNEL_ALIPAY, captor.getValue().getChannel());
        assertEquals(PayOrderService.STATUS_PENDING, captor.getValue().getStatus());
        assertNotNull(captor.getValue().getId(), "主键必须显式生成（真实 SQL 不受 MyBatis-Plus 自动填充影响）");
    }

    // ------------------------------------------------------------------
    // 查询：真实状态 / 不谎报 / 健壮
    // ------------------------------------------------------------------

    @Test
    void findStatusPrefersBizOrderNo() {
        PayOrder byBizNo = order(2001L, "BIZ-9", PayOrderService.STATUS_PAID);
        when(payOrderMapper.selectOne(any())).thenReturn(byBizNo);

        PayOrder result = service.findStatus("BIZ-9");

        assertEquals(PayOrderService.STATUS_PAID, result.getStatus());
        // 命中业务单号后不应再按主键查一次
        verify(payOrderMapper, never()).selectById(any());
    }

    @Test
    void findStatusFallsBackToPrimaryIdWhenBizOrderNoMisses() {
        when(payOrderMapper.selectOne(any())).thenReturn(null);
        when(payOrderMapper.selectById(2002L)).thenReturn(order(2002L, "BIZ-10", PayOrderService.STATUS_CLOSED));

        PayOrder result = service.findStatus("2002");

        assertEquals(PayOrderService.STATUS_CLOSED, result.getStatus());
    }

    @Test
    void findStatusReturnsNullForUnknownId() {
        when(payOrderMapper.selectOne(any())).thenReturn(null);

        // 查不到 → null（由 Controller 转 404 业务码）。修复前这里一律返回"支付成功"，是资金类接口的严重误导。
        assertNull(service.findStatus("999999999999"));
    }

    @Test
    void findStatusRejectsOverLongNumericIdWithout500() {
        when(payOrderMapper.selectOne(any())).thenReturn(null);

        // 26 位数字超出 Long 范围：必须安全返回 null，而不是抛 NumberFormatException 变成 500
        assertNull(service.findStatus("99999999999999999999999999"));
        verify(payOrderMapper, never()).selectById(any());
    }

    @Test
    void findStatusRejectsNonNumericId() {
        when(payOrderMapper.selectOne(any())).thenReturn(null);

        assertNull(service.findStatus("not-a-number"));
        verify(payOrderMapper, never()).selectById(any());
    }

    @Test
    void findStatusReturnsNullForBlankId() {
        assertNull(service.findStatus(null));
        assertNull(service.findStatus("   "));
    }

    // ------------------------------------------------------------------
    // 渠道回调：幂等 + 改单
    // ------------------------------------------------------------------

    @Test
    void acceptNotifyFirstTimeMarksOrderPaid() {
        when(payNotifyLogMapper.selectOne(any())).thenReturn(null);   // 未受理过
        when(payOrderMapper.selectOne(any()))
                .thenReturn(order(3001L, "BIZ-20", PayOrderService.STATUS_PENDING));

        boolean first = service.acceptNotify("alipay", "BIZ-20", "PAY-NO-1", "{\"raw\":1}");

        assertTrue(first);
        verify(payNotifyLogMapper).insert(any(PayNotifyLog.class));
        ArgumentCaptor<PayOrder> captor = ArgumentCaptor.forClass(PayOrder.class);
        verify(payOrderMapper).updateById(captor.capture());
        assertEquals(PayOrderService.STATUS_PAID, captor.getValue().getStatus());
        assertEquals("PAY-NO-1", captor.getValue().getPayNo());
        assertNotNull(captor.getValue().getNotifyTime());
    }

    @Test
    void acceptNotifyDuplicateDeliveryIsIdempotent() {
        when(payNotifyLogMapper.selectOne(any())).thenReturn(new PayNotifyLog());  // 已受理过

        boolean first = service.acceptNotify("alipay", "BIZ-20", "PAY-NO-1", "{}");

        assertFalse(first);
        verify(payNotifyLogMapper, never()).insert(any(PayNotifyLog.class));
        verify(payOrderMapper, never()).updateById(any(PayOrder.class));
    }

    @Test
    void acceptNotifyConcurrentDuplicateIsIdempotent() {
        when(payNotifyLogMapper.selectOne(any())).thenReturn(null);
        // 并发下另一个线程先插入了同一笔回调 → 唯一键 uk_channel_pay_no 抛异常
        when(payNotifyLogMapper.insert(any(PayNotifyLog.class)))
                .thenThrow(new DuplicateKeyException("uk_channel_pay_no"));

        boolean first = service.acceptNotify("alipay", "BIZ-20", "PAY-NO-1", "{}");

        assertFalse(first, "并发重复投递必须被唯一键拦下并安全返回，不应抛异常给渠道（会触发渠道无限重推）");
        verify(payOrderMapper, never()).updateById(any(PayOrder.class));
    }

    @Test
    void acceptNotifyWithoutMatchingOrderStillLogsFlow() {
        when(payNotifyLogMapper.selectOne(any())).thenReturn(null);
        when(payOrderMapper.selectOne(any())).thenReturn(null);   // 找不到对应支付单

        // 仍应登记流水并 ack（渠道不该因为我们对不上账而无限重推）
        assertTrue(service.acceptNotify("wxpay", "BIZ-UNKNOWN", "PAY-NO-9", "{}"));
        verify(payNotifyLogMapper).insert(any(PayNotifyLog.class));
        verify(payOrderMapper, never()).updateById(any(PayOrder.class));
    }

    @Test
    void acceptNotifyTruncatesOversizedRawPayload() {
        when(payNotifyLogMapper.selectOne(any())).thenReturn(null);
        when(payOrderMapper.selectOne(any())).thenReturn(order(3002L, "BIZ-30", PayOrderService.STATUS_PENDING));

        service.acceptNotify("alipay", "BIZ-30", "PAY-NO-2", "x".repeat(5000));

        ArgumentCaptor<PayNotifyLog> captor = ArgumentCaptor.forClass(PayNotifyLog.class);
        verify(payNotifyLogMapper).insert(captor.capture());
        // 列定义是 VARCHAR(1024)，超长会抛 Data too long → 必须截断
        assertEquals(1024, captor.getValue().getRaw().length());
    }

    @Test
    void acceptNotifyDoesNotRewriteAlreadyPaidOrder() {
        when(payNotifyLogMapper.selectOne(any())).thenReturn(null);
        when(payOrderMapper.selectOne(any())).thenReturn(order(3003L, "BIZ-40", PayOrderService.STATUS_PAID));

        assertTrue(service.acceptNotify("alipay", "BIZ-40", "PAY-NO-3", "{}"));

        verify(payOrderMapper, never()).updateById(any(PayOrder.class));
    }

    @Test
    void acceptNotifyWithoutBizOrderNoOnlyLogs() {
        when(payNotifyLogMapper.selectOne(any())).thenReturn(null);

        assertTrue(service.acceptNotify("alipay", null, "PAY-NO-4", "{}"));

        verify(payNotifyLogMapper).insert(any(PayNotifyLog.class));
        verify(payOrderMapper, never()).selectOne(any());
    }
}
