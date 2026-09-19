package com.zhixing.exam.controller;

import com.zhixing.common.annotation.NoWrapper;
import com.zhixing.common.annotation.RequireRole;
import com.zhixing.common.constants.UserRole;
import com.zhixing.common.domain.R;
import com.zhixing.common.utils.OwnerAccessGuard;
import com.zhixing.common.utils.UserContext;
import com.zhixing.exam.domain.po.QuestionResult;
import com.zhixing.exam.domain.vo.AnswerOverviewVO;
import com.zhixing.exam.domain.vo.SubmitResultVO;
import com.zhixing.exam.domain.vo.WrongQuestionVO;
import com.zhixing.exam.service.QuestionResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 答题记录 / 错题本 / 教师端答题情况。
 * <p>
 * 权限：
 * <ul>
 *   <li>提交答题、我的记录、错题本 —— 学员端行为，仅学员(2)，教师/管理员一律 403；</li>
 *   <li>教师端答题情况总览 —— 教师(3) 与 管理员(1) 可查；</li>
 *   <li>按用户查询记录/统计 —— 内部 Feign 接口，由 OwnerAccessGuard 防水平越权。</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/question-results")
@RequiredArgsConstructor
public class QuestionResultController {

    private final QuestionResultService resultService;

    /* ==================== 学员端：答题与错题本 ==================== */

    /**
     * 批量提交答题结果（交卷）。
     * <p>
     * 修复要点：原签名为单个 {@code QuestionResult}，而前端交卷提交的是数组，
     * 反序列化失败导致交卷 400。现改为接收数组，并改为<b>服务端判分</b>，
     * 返回逐题判分结果（对错 / 正确答案 / 解析）供前端即时反馈。
     */
    @PostMapping
    @RequireRole(UserRole.STUDENT)
    public R<List<SubmitResultVO>> submit(@RequestBody List<QuestionResult> results) {
        return R.ok(resultService.submitBatch(results));
    }

    /** 单题提交（供错题重做 / 单题闯关复用） */
    @PostMapping("/single")
    @RequireRole(UserRole.STUDENT)
    public R<SubmitResultVO> submitSingle(@RequestBody QuestionResult result) {
        return R.ok(resultService.submitOne(result));
    }

    /** 我的答题记录 */
    @GetMapping("/mine")
    @RequireRole(UserRole.STUDENT)
    public R<List<QuestionResult>> myRecords() {
        return R.ok(resultService.myRecords());
    }

    /** 我的答题统计：{count, correct, accuracy} */
    @GetMapping("/mine/stats")
    @RequireRole(UserRole.STUDENT)
    public R<Map<String, Object>> myStats() {
        return R.ok(resultService.statsByUser(UserContext.getUserId()));
    }

    /** 我的错题本（题干 + 我的作答 + 正确答案 + 解析） */
    @GetMapping("/mine/wrong")
    @RequireRole(UserRole.STUDENT)
    public R<List<WrongQuestionVO>> myWrongBook() {
        return R.ok(resultService.wrongBook(UserContext.getUserId()));
    }

    /* ==================== 教师端：答题情况 ==================== */

    /**
     * 教师端答题情况总览：整体正确率 + 每题正确率 + 每位学员正确率。
     * 这是"教师发布题目 → 学员作答 → 教师回看正确率"闭环的最后一环。
     */
    @GetMapping("/teacher/overview")
    @RequireRole({UserRole.TEACHER, UserRole.STAFF})
    public R<AnswerOverviewVO> teacherOverview() {
        return R.ok(resultService.teacherOverview());
    }

    /** 某题的作答明细（教师端下钻查看单题作答记录） */
    @GetMapping("/teacher/question/{questionId}")
    @RequireRole({UserRole.TEACHER, UserRole.STAFF})
    public R<List<QuestionResult>> byQuestion(@PathVariable Long questionId) {
        return R.ok(resultService.listByQuestion(questionId));
    }

    /* ==================== 内部 Feign ==================== */

    /**
     * 查询指定用户答题记录（内部，供学情分析聚合）。
     * 防水平越权：外部用户仅可查询本人，STAFF 可查询任意用户，内部服务调用放行。
     */
    @GetMapping("/users/{userId}/all")
    @NoWrapper
    public List<QuestionResult> listByUser(@PathVariable Long userId) {
        OwnerAccessGuard.checkOwnerOrInternal(userId);
        return resultService.listByUser(userId);
    }

    /**
     * 查询指定用户答题统计（内部，供学情分析聚合）。
     * 防水平越权规则同上。
     */
    @GetMapping("/users/{userId}/stats")
    @NoWrapper
    public Map<String, Object> statsByUser(@PathVariable Long userId) {
        OwnerAccessGuard.checkOwnerOrInternal(userId);
        return resultService.statsByUser(userId);
    }
}
