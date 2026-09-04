package com.zhixing.trade.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhixing.trade.domain.po.CouponUseRecord;
import com.zhixing.trade.mapper.CouponUseRecordMapper;
import com.zhixing.trade.mq.CouponMsg;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 优惠券核销/退回异步落库单测（消费幂等）：
 * 消费流水 + order_id 唯一双重保证，重复投递/并发只会落库一次。
 */
@ExtendWith(MockitoExtension.class)
class CouponUseRecordServiceTest {

    @Mock
    private CouponUseRecordMapper couponUseRecordMapper;
    @Mock
    private IdempotencyGuard idempotencyGuard;

    @InjectMocks
    private CouponUseRecordService service;

    @BeforeEach
    void setUp() {
        // 纯 Mock 单测手动初始化 MyBatis-Plus 元数据，保证 LambdaUpdateWrapper 可用
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), CouponUseRecord.class);
    }

    private CouponMsg useMsg() {
        CouponMsg m = new CouponMsg();
        m.setAction("USE");
        m.setUserId(1L);
        m.setCouponId(9L);
        m.setUserCouponId(88L);
        m.setOrderId(1L);
        m.setAmount(30L);
        return m;
    }

    @Test
    void submitUseInsertsRecordOnFirstConsume() {
        when(couponUseRecordMapper.selectCount(any())).thenReturn(0L);
        when(idempotencyGuard.tryConsume(anyString(), anyString(), anyString())).thenReturn(true);

        service.submitUse(useMsg());

        verify(couponUseRecordMapper).insert(any(CouponUseRecord.class));
    }

    @Test
    void submitUseSkipsWhenConsumeAlreadyDone() {
        when(couponUseRecordMapper.selectCount(any())).thenReturn(0L);
        when(idempotencyGuard.tryConsume(anyString(), anyString(), anyString())).thenReturn(false);

        service.submitUse(useMsg());

        verify(couponUseRecordMapper, never()).insert(any(CouponUseRecord.class));
    }

    @Test
    void submitUseSkipsWhenOrderAlreadyWritten() {
        when(couponUseRecordMapper.selectCount(any())).thenReturn(1L);

        service.submitUse(useMsg());

        verify(idempotencyGuard, never()).tryConsume(anyString(), anyString(), anyString());
        verify(couponUseRecordMapper, never()).insert(any(CouponUseRecord.class));
    }

    @Test
    void submitRefundUpdatesRecordOnFirstConsume() {
        when(idempotencyGuard.tryConsume(anyString(), anyString(), anyString())).thenReturn(true);
        CouponMsg refund = useMsg();
        refund.setAction("REFUND");

        service.submitRefund(refund);

        verify(couponUseRecordMapper).update(any(), any());
    }

    @Test
    void submitRefundSkipsWhenConsumeAlreadyDone() {
        // 重复投递的退回消息：幂等守卫拦截，不产生重复更新
        when(idempotencyGuard.tryConsume(anyString(), anyString(), anyString())).thenReturn(false);
        CouponMsg refund = useMsg();
        refund.setAction("REFUND");

        service.submitRefund(refund);

        verify(couponUseRecordMapper, never()).update(any(), any());
    }

    @Test
    void submitRefundToleratesMissingRecord() {
        // 容错：核销流水缺失（如消息乱序）时退回更新 0 行，不抛异常、不阻塞消费
        when(idempotencyGuard.tryConsume(anyString(), anyString(), anyString())).thenReturn(true);
        when(couponUseRecordMapper.update(any(), any())).thenReturn(0);
        CouponMsg refund = useMsg();
        refund.setAction("REFUND");

        assertDoesNotThrow(() -> service.submitRefund(refund));
    }
}