package com.zhixing.course.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhixing.api.dto.trade.QuotaMsg;
import com.zhixing.common.exceptions.BizIllegalException;
import com.zhixing.course.domain.po.Course;
import com.zhixing.course.domain.po.CourseQuota;
import com.zhixing.course.domain.po.CourseQuotaRecord;
import com.zhixing.course.mapper.CourseMapper;
import com.zhixing.course.mapper.CourseQuotaMapper;
import com.zhixing.course.mapper.CourseQuotaRecordMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 课程名额状态机单测：LOCK 锁定（防超卖）/ CONFIRM 确认（转销量）/ RELEASE 释放，含幂等路径。
 */
@ExtendWith(MockitoExtension.class)
class CourseQuotaServiceTest {

    @Mock
    private CourseQuotaMapper courseQuotaMapper;
    @Mock
    private CourseQuotaRecordMapper quotaRecordMapper;
    @Mock
    private CourseMapper courseMapper;
    @Mock
    private IdempotencyGuard idempotencyGuard;

    @InjectMocks
    private CourseQuotaService service;

    @BeforeEach
    void setUp() {
        // 纯 Mock 单测手动初始化 MyBatis-Plus 元数据，保证 LambdaWrapper 可用
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), CourseQuota.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), CourseQuotaRecord.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Course.class);
    }

    private QuotaMsg msg() {
        QuotaMsg m = new QuotaMsg();
        m.setOrderId(100L);
        m.setCourseId(5L);
        m.setUserId(1L);
        return m;
    }

    private CourseQuotaRecord record(int status) {
        CourseQuotaRecord r = new CourseQuotaRecord();
        r.setId(1L);
        r.setOrderId(100L);
        r.setCourseId(5L);
        r.setUserId(1L);
        r.setStatus(status);
        return r;
    }

    // ==================== LOCK ====================

    @Test
    void lockSucceedsWhenQuotaAvailable() {
        when(quotaRecordMapper.selectOne(any())).thenReturn(null);
        when(idempotencyGuard.tryConsume(any(), any(), any())).thenReturn(true);
        when(courseQuotaMapper.selectOne(any())).thenReturn(new CourseQuota());
        when(courseQuotaMapper.update(any(), any())).thenReturn(1);

        assertDoesNotThrow(() -> service.lock(msg()));

        verify(quotaRecordMapper).insert(any(CourseQuotaRecord.class));
        verify(courseQuotaMapper).update(any(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void lockThrowsWhenQuotaFull() {
        when(quotaRecordMapper.selectOne(any())).thenReturn(null);
        when(idempotencyGuard.tryConsume(any(), any(), any())).thenReturn(true);
        when(courseQuotaMapper.selectOne(any())).thenReturn(new CourseQuota());
        // 条件更新命中 0 行 = 名额已满
        when(courseQuotaMapper.update(any(), any())).thenReturn(0);

        // 抛出异常 → MQ RECONSUME_LATER 重投，最终死信人工介入
        assertThrows(BizIllegalException.class, () -> service.lock(msg()));
    }

    @Test
    void lockIdempotentWhenRecordExists() {
        when(quotaRecordMapper.selectOne(any())).thenReturn(record(CourseQuotaService.STATUS_LOCKED));

        service.lock(msg());

        verify(idempotencyGuard, never()).tryConsume(any(), any(), any());
        verify(courseQuotaMapper, never()).update(any(), any());
    }

    // ==================== CONFIRM ====================

    @Test
    void confirmTransitionsLockedToConfirmed() {
        when(quotaRecordMapper.selectOne(any())).thenReturn(record(CourseQuotaService.STATUS_LOCKED));
        when(idempotencyGuard.tryConsume(any(), any(), any())).thenReturn(true);
        when(quotaRecordMapper.update(any(), any())).thenReturn(1);

        service.confirm(msg());

        // 锁定释放 + 销量 +1
        verify(courseQuotaMapper).update(any(), any(LambdaUpdateWrapper.class));
        verify(courseMapper).update(any(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void confirmIdempotentWhenAlreadyConfirmed() {
        when(quotaRecordMapper.selectOne(any())).thenReturn(record(CourseQuotaService.STATUS_CONFIRMED));

        service.confirm(msg());

        verify(quotaRecordMapper, never()).update(any(), any());
        verify(courseMapper, never()).update(any(), any());
    }

    @Test
    void confirmRecoversWhenLockMessageLost() {
        // 锁定消息丢失（死信）容错路径：无流水时补锁定并直接确认
        when(quotaRecordMapper.selectOne(any())).thenReturn(null);
        when(idempotencyGuard.tryConsume(any(), any(), any())).thenReturn(true);
        when(courseQuotaMapper.selectOne(any())).thenReturn(new CourseQuota());
        when(courseQuotaMapper.update(any(), any())).thenReturn(1);

        service.confirm(msg());

        verify(quotaRecordMapper).insert(any(CourseQuotaRecord.class));
        verify(courseMapper).update(any(), any(LambdaUpdateWrapper.class));
    }

    // ==================== RELEASE ====================

    @Test
    void releaseTransitionsLockedToReleased() {
        when(quotaRecordMapper.selectOne(any())).thenReturn(record(CourseQuotaService.STATUS_LOCKED));
        when(idempotencyGuard.tryConsume(any(), any(), any())).thenReturn(true);
        when(quotaRecordMapper.update(any(), any())).thenReturn(1);

        service.release(msg());

        // 释放锁定计数；已确认订单不销量的更新不应发生
        verify(courseQuotaMapper).update(any(), any(LambdaUpdateWrapper.class));
        verify(courseMapper, never()).update(any(), any());
    }

    @Test
    void releaseIdempotentWhenAlreadyReleased() {
        when(quotaRecordMapper.selectOne(any())).thenReturn(record(CourseQuotaService.STATUS_RELEASED));

        service.release(msg());

        verify(quotaRecordMapper, never()).update(any(), any());
        verify(courseQuotaMapper, never()).update(any(), any());
    }
}
