package com.zhixing.exam.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhixing.api.client.user.UserClient;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.utils.UserContext;
import com.zhixing.exam.domain.po.Question;
import com.zhixing.exam.domain.po.QuestionResult;
import com.zhixing.exam.mapper.QuestionResultMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 答题记录服务单测：服务端判分 / 批量交卷 / 统计口径 / 参数校验。
 * <p>
 * 关键回归点：判分必须由后端完成（忽略前端传入的 correct、score），
 * 否则学员可直接伪造满分。
 */
@ExtendWith(MockitoExtension.class)
class QuestionResultServiceTest {

    @Mock
    private QuestionResultMapper resultMapper;
    @Mock
    private QuestionService questionService;
    @Mock
    private UserClient userClient;

    @InjectMocks
    private QuestionResultService service;

    @BeforeEach
    void setUp() {
        UserContext.setUser(2001L);
        // 纯 Mock 单测无 Spring 上下文，手动初始化 MyBatis-Plus 元数据
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), QuestionResult.class);
    }

    @AfterEach
    void tearDown() {
        UserContext.remove();
    }

    private Question question(Long id, String answer, int score) {
        Question q = new Question();
        q.setId(id);
        q.setName("题目" + id);
        q.setAnswer(answer);
        q.setScore(score);
        q.setCourseId(3001L);
        return q;
    }

    @Test
    void submitBatchGradesOnServerSide() {
        when(questionService.listByIdsOrAll(anyList(), anyBoolean()))
                .thenReturn(List.of(question(1L, "A", 10), question(2L, "B", 5)));

        // 客户端谎报全对：correct=true, score=100
        QuestionResult fake1 = new QuestionResult();
        fake1.setQuestionId(1L);
        fake1.setUserAnswer("A");
        fake1.setCorrect(true);

        QuestionResult fake2 = new QuestionResult();
        fake2.setQuestionId(2L);
        fake2.setUserAnswer("A");
        fake2.setCorrect(true);

        var graded = service.submitBatch(List.of(fake1, fake2));

        assertEquals(2, graded.size());
        ArgumentCaptor<QuestionResult> captor = ArgumentCaptor.forClass(QuestionResult.class);
        verify(resultMapper, atLeastOnce()).insert(captor.capture());
        List<QuestionResult> inserted = captor.getAllValues();

        QuestionResult first = inserted.get(0);
        assertEquals(2001L, first.getUserId());
        assertTrue(first.getCorrect());
        assertEquals(10, first.getScore());
        assertEquals("题目1", first.getQuestionName());
        assertEquals(3001L, first.getCourseId());

        QuestionResult second = inserted.get(1);
        assertFalse(second.getCorrect(), "答错必须由服务端判为 false，不被客户端 correct 影响");
        assertEquals(0, second.getScore());

        // 回传逐题判分结果，供前端即时展示
        assertTrue(graded.get(0).getCorrect());
        assertEquals("A", graded.get(0).getCorrectAnswer());
        assertFalse(graded.get(1).getCorrect());
        assertEquals("B", graded.get(1).getCorrectAnswer());
    }

    @Test
    void multiChoiceIgnoresLetterOrder() {
        when(questionService.listByIdsOrAll(anyList(), anyBoolean()))
                .thenReturn(List.of(question(7L, "AB", 8)));

        QuestionResult item = new QuestionResult();
        item.setQuestionId(7L);
        item.setUserAnswer("BA");

        var graded = service.submitBatch(List.of(item));

        ArgumentCaptor<QuestionResult> captor = ArgumentCaptor.forClass(QuestionResult.class);
        verify(resultMapper).insert(captor.capture());
        assertTrue(captor.getValue().getCorrect(), "多选答案字母顺序不同应判为正确");
        assertEquals(8, captor.getValue().getScore());
        assertEquals(8, graded.get(0).getScore());
    }

    @Test
    void unknownQuestionIsSkipped() {
        when(questionService.listByIdsOrAll(anyList(), anyBoolean())).thenReturn(List.of());

        QuestionResult item = new QuestionResult();
        item.setQuestionId(999L);
        item.setUserAnswer("A");

        assertEquals(0, service.submitBatch(List.of(item)).size());
    }

    @Test
    void emptySubmissionRejected() {
        assertThrows(BadRequestException.class, () -> service.submitBatch(List.of()));
        assertThrows(BadRequestException.class, () -> service.submitBatch(null));
    }

    @Test
    void missingQuestionIdRejected() {
        QuestionResult item = new QuestionResult();
        item.setUserAnswer("A");
        assertThrows(BadRequestException.class, () -> service.submitBatch(List.of(item)));
    }

    @Test
    void statsByUserComputesAccuracy() {
        when(resultMapper.selectList(any())).thenReturn(List.of(
                record(true), record(true), record(true), record(false)));

        Map<String, Object> stats = service.statsByUser(2001L);

        assertEquals(4L, stats.get("count"));
        assertEquals(3L, stats.get("correct"));
        assertEquals(75.0, stats.get("accuracy"));
    }

    @Test
    void statsByUserHandlesEmpty() {
        when(resultMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> stats = service.statsByUser(2001L);

        assertEquals(0L, stats.get("count"));
        assertEquals(0.0, stats.get("accuracy"));
    }

    @Test
    void wrongBookCarriesCorrectAnswerAndAnalysis() {
        QuestionResult wrong = record(false);
        wrong.setQuestionId(3L);
        wrong.setUserAnswer("A");
        when(resultMapper.selectList(any())).thenReturn(List.of(wrong));

        Question q = question(3L, "C", 5);
        q.setAnalysis("因为 C 才是正确选项");
        q.setOptions(List.of("A", "B", "C", "D"));
        q.setType(1);
        when(questionService.listByIdsOrAll(anyList(), anyBoolean())).thenReturn(List.of(q));

        var list = service.wrongBook(2001L);

        assertEquals(1, list.size());
        assertEquals("C", list.get(0).getCorrectAnswer());
        assertEquals("A", list.get(0).getMyAnswer());
        assertEquals("因为 C 才是正确选项", list.get(0).getAnalysis());
        assertEquals(1L, list.get(0).getWrongCount());
    }

    private QuestionResult record(boolean correct) {
        QuestionResult r = new QuestionResult();
        r.setUserId(2001L);
        r.setQuestionId(1L);
        r.setQuestionName("题目1");
        r.setUserAnswer("A");
        r.setCorrect(correct);
        return r;
    }
}
