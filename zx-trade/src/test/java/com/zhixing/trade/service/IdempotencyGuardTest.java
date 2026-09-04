package com.zhixing.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixing.trade.domain.po.ConsumeRecord;
import com.zhixing.trade.mapper.ConsumeRecordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 消费幂等守卫单测（幂等第二层）：首次消费放行、已消费/并发重复拒绝。
 */
@ExtendWith(MockitoExtension.class)
class IdempotencyGuardTest {

    @Mock
    private ConsumeRecordMapper consumeRecordMapper;

    @InjectMocks
    private IdempotencyGuard guard;

    @Test
    void tryConsumeReturnsTrueOnFirstConsume() {
        when(consumeRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(consumeRecordMapper.insert(any(ConsumeRecord.class))).thenReturn(1);

        assertTrue(guard.tryConsume("coupon:use:1", "zx_coupon_use", "USE"));
        verify(consumeRecordMapper).insert(any(ConsumeRecord.class));
    }

    @Test
    void tryConsumeReturnsFalseWhenAlreadyConsumed() {
        when(consumeRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertFalse(guard.tryConsume("coupon:use:1", "zx_coupon_use", "USE"));
        verify(consumeRecordMapper, never()).insert(any(ConsumeRecord.class));
    }

    @Test
    void tryConsumeReturnsFalseOnConcurrentDuplicateInsert() {
        when(consumeRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(consumeRecordMapper.insert(any(ConsumeRecord.class)))
                .thenThrow(new DuplicateKeyException("uk_consume_key"));

        // 并发场景：先查没有，插入撞唯一索引 → 视为已消费
        assertFalse(guard.tryConsume("coupon:use:1", "zx_coupon_use", "USE"));
    }

    @Test
    void tryConsumeSkipsBlankKey() {
        assertTrue(guard.tryConsume(" ", "zx_coupon_use", "USE"));
        verify(consumeRecordMapper, never()).selectCount(any());
        verify(consumeRecordMapper, never()).insert(any(ConsumeRecord.class));
    }
}
