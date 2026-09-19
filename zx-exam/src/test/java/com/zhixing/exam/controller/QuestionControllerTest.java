package com.zhixing.exam.controller;

import com.zhixing.common.domain.PageDTO;
import com.zhixing.common.domain.PageQuery;
import com.zhixing.common.domain.R;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.exam.domain.po.Question;
import com.zhixing.exam.domain.vo.AnswerOverviewVO;
import com.zhixing.exam.domain.vo.WrongQuestionVO;
import com.zhixing.exam.service.QuestionResultService;
import com.zhixing.exam.service.QuestionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 题库控制器单测：验证教师端 CRUD 委派、学员端列表不强制 ids、教师端答题情况透出。
 * <p>
 * 背景回归：原实现 {@code /questions/list} 强制要求 ids 参数（学员端 400），
 * 且题目存内存 Map 重启即丢；此处锁定修复后的契约。
 */
@ExtendWith(MockitoExtension.class)
class QuestionControllerTest {

    @Mock
    private QuestionService questionService;
    @Mock
    private QuestionResultService resultService;

    @InjectMocks
    private QuestionController controller;

    @Test
    void addDelegatesToService() {
        Question q = new Question();
        q.setName("Java 基础题");
        q.setScore(5);
        when(questionService.create(any(Question.class))).thenReturn(100L);

        R<Long> add = controller.add(q);

        assertEquals(100L, add.getData());
        verify(questionService).create(q);
    }

    @Test
    void updateDelegatesToService() {
        Question q = new Question();
        q.setName("新题");
        q.setScore(10);

        controller.update(9L, q);

        verify(questionService).update(9L, q);
    }

    @Test
    void deleteDelegatesToService() {
        controller.delete(9L);
        verify(questionService).delete(9L);
    }

    @Test
    void publishTogglesVisibility() {
        controller.publish(9L, false);
        verify(questionService).publish(9L, false);
    }

    @Test
    void listWithoutIdsDoesNotRequireParam() {
        // 回归：不传 ids 时必须正常返回，而不是抛 400
        List<Question> published = List.of(new Question());
        when(questionService.listByIdsOrAll(isNull(), anyBoolean())).thenReturn(published);

        R<List<Question>> res = controller.list(null);

        assertEquals(1, res.getData().size());
        assertSame(published, res.getData());
    }

    @Test
    void pageDelegatesWithFilters() {
        PageDTO<Question> dto = PageDTO.empty(0L, 0L);
        when(questionService.page(any(PageQuery.class), any(), any(), any(), isNull(), anyBoolean()))
                .thenReturn(dto);

        PageQuery query = new PageQuery();
        R<PageDTO<Question>> res = controller.page(query, "Java", 1, 3001L);

        assertSame(dto, res.getData());
    }

    @Test
    void allReturnsTeacherLibrary() {
        when(questionService.listByIdsOrAll(isNull(), anyBoolean())).thenReturn(List.of());

        R<List<Question>> res = controller.all();

        assertEquals(0, res.getData().size());
    }

    @Test
    void scoresDelegates() {
        when(questionService.scores(any())).thenReturn(Map.of(1L, 5));

        R<Map<Long, Integer>> res = controller.scores(List.of(1L));

        assertEquals(5, res.getData().get(1L));
    }

    @Test
    void numOfTeacherDelegates() {
        when(questionService.countByTeacher(anyLong())).thenReturn(12L);

        assertEquals(12L, controller.numOfTeacher(2002L).getData());
    }

    @Test
    void wrongBookExposesExplanation() {
        WrongQuestionVO vo = new WrongQuestionVO();
        vo.setQuestionName("JVM 内存模型");
        vo.setCorrectAnswer("B");
        vo.setMyAnswer("A");
        when(resultService.wrongBook(anyLong())).thenReturn(List.of(vo));

        List<WrongQuestionVO> data = new QuestionResultController(resultService).myWrongBook().getData();

        assertEquals("B", data.get(0).getCorrectAnswer());
        assertEquals("A", data.get(0).getMyAnswer());
    }

    @Test
    void teacherOverviewDelegates() {
        AnswerOverviewVO overview = new AnswerOverviewVO();
        overview.setTotalRecords(30);
        when(resultService.teacherOverview()).thenReturn(overview);

        R<AnswerOverviewVO> res = new QuestionResultController(resultService).teacherOverview();

        assertEquals(30, res.getData().getTotalRecords());
    }

    @Test
    void badRequestPropagates() {
        when(questionService.create(any())).thenThrow(new BadRequestException("题干不能为空"));
        org.junit.jupiter.api.Assertions.assertThrows(BadRequestException.class,
                () -> controller.add(new Question()));
    }
}
