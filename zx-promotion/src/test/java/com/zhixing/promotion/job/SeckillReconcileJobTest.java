package com.zhixing.promotion.job;

import com.zhixing.promotion.service.SeckillService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 秒杀对账任务单测：开关关闭短路、全活动逐个对账、脏键容错。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SeckillReconcileJobTest {

    @Mock
    private SeckillService seckillService;

    @InjectMocks
    private SeckillReconcileJob job;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(job, "reconcileEnabled", true);
    }

    @Test
    void skipsWhenDisabled() {
        ReflectionTestUtils.setField(job, "reconcileEnabled", false);

        job.reconcileAll();

        verifyNoInteractions(seckillService);
    }

    @Test
    void reconcilesEveryActivityAndLogsCompensation() {
        when(seckillService.scanActivityCouponIds()).thenReturn(Set.of(
                SeckillService.USERS_KEY_PREFIX + "1",
                SeckillService.USERS_KEY_PREFIX + "2"));
        when(seckillService.reconcile(1L)).thenReturn(2);
        when(seckillService.reconcile(2L)).thenReturn(0);

        job.reconcileAll();

        verify(seckillService).reconcile(1L);
        verify(seckillService).reconcile(2L);
    }

    @Test
    void toleratesMalformedKey() {
        when(seckillService.scanActivityCouponIds()).thenReturn(Set.of(
                SeckillService.USERS_KEY_PREFIX + "abc",
                SeckillService.USERS_KEY_PREFIX + "3"));

        assertDoesNotThrow(() -> job.reconcileAll());

        // 脏键 "abc" 被跳过（NumberFormatException 捕获），正常键 "3" 仍完成对账
        verify(seckillService).reconcile(3L);
        verify(seckillService, never()).reconcile(2L);
    }
}
