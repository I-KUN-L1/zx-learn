package com.zhixing.exam.controller;

import com.zhixing.common.domain.R;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.exam.domain.po.Question;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 题目管理（骨架）控制器单元测试：验证内存 CRUD 与参数校验
 */
class QuestionControllerTest {

    private QuestionController controller;

    @BeforeEach
    void setUp() {
        controller = new QuestionController();
    }

    @Test
    void addAndGetById() {
        Question q = new Question();
        q.setName("Java 基础题");
        q.setScore(5);

        R<Long> add = controller.add(q);
        assertTrue(add.success());
        assertNotNull(add.getData());

        R<Question> got = controller.getById(add.getData());
        assertEquals("Java 基础题", got.getData().getName());
        assertEquals(5, got.getData().getScore());
    }

    @Test
    void addRejectsEmptyQuestion() {
        assertThrows(BadRequestException.class, () -> controller.add(null));
        Question noScore = new Question();
        noScore.setName("缺分值");
        assertThrows(BadRequestException.class, () -> controller.add(noScore));
    }

    @Test
    void updateExistingQuestion() {
        Question q = new Question();
        q.setName("旧题");
        q.setScore(1);
        Long id = controller.add(q).getData();

        Question updated = new Question();
        updated.setName("新题");
        updated.setScore(10);
        assertTrue(controller.update(id, updated).success());
        assertEquals("新题", controller.getById(id).getData().getName());
    }

    @Test
    void updateMissingQuestionRejected() {
        Question q = new Question();
        q.setName("新");
        q.setScore(1);
        assertThrows(BadRequestException.class, () -> controller.update(999L, q));
    }

    @Test
    void deleteQuestion() {
        Question q = new Question();
        q.setName("待删");
        q.setScore(2);
        Long id = controller.add(q).getData();

        assertTrue(controller.delete(id).success());
        assertThrows(BadRequestException.class, () -> controller.getById(id));
        assertThrows(BadRequestException.class, () -> controller.delete(id));
    }

    @Test
    void listAndScores() {
        Question a = new Question();
        a.setScore(3);
        Question b = new Question();
        b.setScore(4);
        Long idA = controller.add(a).getData();
        Long idB = controller.add(b).getData();

        R<List<Question>> list = controller.list(List.of(idA, idB, 999L));
        assertEquals(2, list.getData().size());

        R<Map<Long, Integer>> scores = controller.scores(List.of(idA, idB));
        assertEquals(3, scores.getData().get(idA));
        assertEquals(4, scores.getData().get(idB));
    }
}
