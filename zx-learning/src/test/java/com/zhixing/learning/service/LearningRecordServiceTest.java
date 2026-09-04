package com.zhixing.learning.service;

import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.exceptions.BizIllegalException;
import com.zhixing.common.utils.UserContext;
import com.zhixing.learning.domain.dto.LearningProgressDTO;
import com.zhixing.learning.domain.po.LearningRecord;
import com.zhixing.learning.mapper.LearningRecordMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 学习记录服务单测：重点覆盖"学习进度不能倒退"校验
 */
@ExtendWith(MockitoExtension.class)
class LearningRecordServiceTest {

    @Mock
    private LearningRecordMapper learningRecordMapper;

    @InjectMocks
    private LearningRecordService service;

    @BeforeEach
    void setUp() {
        UserContext.setUser(1L);
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    private LearningProgressDTO form(int progress) {
        LearningProgressDTO form = new LearningProgressDTO();
        form.setCourseId(100L);
        form.setLessonId(888L);
        form.setProgress(progress);
        form.setLearnDuration(60);
        return form;
    }

    @Test
    void createNewRecordInserts() {
        when(learningRecordMapper.selectOne(any())).thenReturn(null);

        service.submitProgress(form(40));

        ArgumentCaptor<LearningRecord> captor = ArgumentCaptor.forClass(LearningRecord.class);
        verify(learningRecordMapper).insert(captor.capture());
        LearningRecord saved = captor.getValue();
        assertEquals(1L, saved.getUserId());
        assertEquals(100L, saved.getCourseId());
        assertEquals(40, saved.getProgress());
        assertEquals(60, saved.getLearnDuration());
        assertFalse(saved.getFinished());
    }

    @Test
    void higherProgressUpdatesIncrementally() {
        LearningRecord exist = new LearningRecord();
        exist.setId(9L);
        exist.setProgress(30);
        exist.setLearnDuration(120);
        when(learningRecordMapper.selectOne(any())).thenReturn(exist);

        service.submitProgress(form(60));

        verify(learningRecordMapper).updateById(exist);
        assertEquals(60, exist.getProgress());
        assertEquals(180, exist.getLearnDuration());
        assertFalse(exist.getFinished());
    }

    @Test
    void progress100MarksFinished() {
        LearningRecord exist = new LearningRecord();
        exist.setId(9L);
        exist.setProgress(30);
        when(learningRecordMapper.selectOne(any())).thenReturn(exist);

        service.submitProgress(form(100));

        assertTrue(exist.getFinished());
    }

    @Test
    void progressCannotRegress() {
        LearningRecord exist = new LearningRecord();
        exist.setId(9L);
        exist.setProgress(70);
        when(learningRecordMapper.selectOne(any())).thenReturn(exist);

        assertThrows(BizIllegalException.class, () -> service.submitProgress(form(40)));
        verify(learningRecordMapper, never()).updateById(any(LearningRecord.class));
    }

    @Test
    void invalidProgressRejected() {
        LearningProgressDTO bad = form(150);
        assertThrows(BadRequestException.class, () -> service.submitProgress(bad));
        verify(learningRecordMapper, never()).insert(any(LearningRecord.class));
    }
}