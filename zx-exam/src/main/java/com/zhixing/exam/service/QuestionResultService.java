package com.zhixing.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixing.api.client.learning.PointsClient;
import com.zhixing.api.client.user.UserClient;
import com.zhixing.api.constants.PointsSource;
import com.zhixing.api.dto.learning.PointsAwardDTO;
import com.zhixing.api.dto.user.UserDTO;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.utils.UserContext;
import com.zhixing.exam.domain.po.Question;
import com.zhixing.exam.domain.po.QuestionResult;
import com.zhixing.exam.domain.vo.AnswerOverviewVO;
import com.zhixing.exam.domain.vo.QuestionStatVO;
import com.zhixing.exam.domain.vo.StudentAnswerStatVO;
import com.zhixing.exam.domain.vo.SubmitResultVO;
import com.zhixing.exam.domain.vo.WrongQuestionVO;
import com.zhixing.exam.mapper.QuestionResultMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 答题记录服务。
 * <p>
 * 关键设计：
 * <ul>
 *   <li><b>服务端判分</b> —— 学员只提交作答，正误与得分由后端比对题库答案计算，
 *       客户端提交的 correct/score 一律忽略，防止前端篡改成绩；</li>
 *   <li><b>批量提交</b> —— 交卷一次提交整张卷子（原接口签名只接收单个对象，
 *       与前端数组入参不匹配，导致交卷 400）；</li>
 *   <li><b>题目快照</b> —— 落库时冗余 questionName / courseId，学情分析无需回表题库。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionResultService {

    private final QuestionResultMapper resultMapper;
    private final QuestionService questionService;
    private final UserClient userClient;
    private final PointsClient pointsClient;

    /**
     * 批量提交答题结果（交卷）。
     * <p>
     * 忽略客户端传入的 correct / score，按题库标准答案重新判分；
     * 返回逐题判分结果，前端据此即时展示对错与解析。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<SubmitResultVO> submitBatch(List<QuestionResult> results) {
        if (results == null || results.isEmpty()) {
            throw new BadRequestException("答题结果不能为空");
        }
        Long userId = UserContext.getUserId();
        List<Long> questionIds = results.stream()
                .map(QuestionResult::getQuestionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (questionIds.isEmpty()) {
            throw new BadRequestException("题目 id 不能为空");
        }
        Map<Long, Question> questionMap = questionService.listByIdsOrAll(questionIds, false).stream()
                .filter(q -> q.getId() != null)
                .collect(Collectors.toMap(Question::getId, Function.identity(), (a, b) -> a));

        List<SubmitResultVO> graded = new ArrayList<>(results.size());
        // 与 graded 一一对应的答题记录主键，作为积分幂等键（同一次交卷只加一次分）
        List<Long> resultIds = new ArrayList<>(results.size());
        int saved = 0;
        for (QuestionResult item : results) {
            if (item == null || item.getQuestionId() == null) {
                continue;
            }
            Question question = questionMap.get(item.getQuestionId());
            if (question == null) {
                log.warn("题目不存在，跳过：questionId={}, userId={}", item.getQuestionId(), userId);
                continue;
            }
            String userAnswer = normalizeAnswer(item.getUserAnswer());
            // 服务端判分：多选答案字母顺序无关
            boolean correct = isCorrect(question, item.getUserAnswer());
            int score = correct ? (question.getScore() == null ? 0 : question.getScore()) : 0;

            QuestionResult record = new QuestionResult();
            record.setUserId(userId);
            record.setQuestionId(question.getId());
            record.setQuestionName(question.getName());
            record.setCourseId(question.getCourseId());
            record.setUserAnswer(userAnswer);
            record.setCorrect(correct);
            record.setScore(score);
            resultMapper.insert(record);
            resultIds.add(record.getId());
            saved++;

            SubmitResultVO vo = new SubmitResultVO();
            vo.setQuestionId(question.getId());
            vo.setQuestionName(question.getName());
            vo.setUserAnswer(userAnswer);
            vo.setCorrectAnswer(normalizeAnswer(question.getAnswer()));
            vo.setCorrect(correct);
            vo.setScore(score);
            vo.setAnalysis(question.getAnalysis());
            graded.add(vo);
        }
        log.info("答题提交完成：userId={}, 提交 {} 题，入库 {} 条，答对 {} 题",
                userId, results.size(), saved, graded.stream().filter(SubmitResultVO::getCorrect).count());
        // 完成测验自动加分：仅答对计分，判分结果由服务端产出（前端无法伪造）
        awardQuizPoints(userId, resultIds, graded);
        return graded;
    }

    /**
     * 测验答对加分（幂等键 = 答题记录主键，同一次交卷只入账一次）。
     * <p>
     * 积分接口为跨服务调用，异常一律吞掉并告警 —— 加分失败绝不能让交卷失败，
     * 否则用户会因为积分服务抖动而丢答卷。
     */
    private void awardQuizPoints(Long userId, List<Long> resultIds, List<SubmitResultVO> graded) {
        if (graded.isEmpty()) {
            return;
        }
        for (int i = 0; i < graded.size() && i < resultIds.size(); i++) {
            SubmitResultVO vo = graded.get(i);
            Long resultId = resultIds.get(i);
            if (!Boolean.TRUE.equals(vo.getCorrect()) || resultId == null) {
                continue;
            }
            try {
                pointsClient.award(new PointsAwardDTO(userId, PointsSource.QUIZ,
                        PointsSource.POINTS_QUIZ_CORRECT,
                        "测验答对《" + abbreviate(vo.getQuestionName()) + "》",
                        String.valueOf(resultId)));
            } catch (Exception e) {
                log.warn("测验积分发放失败（已降级，不影响交卷）：userId={}, resultId={}, err={}",
                        userId, resultId, e.getMessage());
            }
        }
    }

    private static String abbreviate(String s) {
        if (s == null || s.isBlank()) {
            return "题目";
        }
        return s.length() <= 30 ? s : s.substring(0, 30) + "…";
    }

    /** 单条提交（单题闯关/练习模式）：返回该题判分结果 */
    @Transactional(rollbackFor = Exception.class)
    public SubmitResultVO submitOne(QuestionResult result) {
        List<SubmitResultVO> list = submitBatch(List.of(result));
        if (list.isEmpty()) {
            throw new BadRequestException("题目不存在");
        }
        return list.get(0);
    }

    /** 查询指定用户全部答题记录（内部，供学情分析聚合） */
    public List<QuestionResult> listByUser(Long userId) {
        return resultMapper.selectList(new LambdaQueryWrapper<QuestionResult>()
                .eq(QuestionResult::getUserId, userId)
                .orderByDesc(QuestionResult::getCreateTime));
    }

    /**
     * 统计指定用户答题情况：{count, correct, accuracy}。
     * <p>
     * 保持原契约不变 —— zx-insight 经 Feign 消费该结构。
     */
    public Map<String, Object> statsByUser(Long userId) {
        List<QuestionResult> list = listByUser(userId);
        long count = list.size();
        long correct = list.stream().filter(r -> Boolean.TRUE.equals(r.getCorrect())).count();
        double accuracy = count == 0 ? 0 : Math.round(correct * 1000.0 / count) / 10.0;
        return Map.of("count", count, "correct", correct, "accuracy", accuracy);
    }

    /** 我的答题记录（学员端） */
    public List<QuestionResult> myRecords() {
        return listByUser(UserContext.getUserId());
    }

    /**
     * 错题本：按题目去重，取最近一次作答，附带题干 / 选项 / 正确答案 / 解析。
     */
    public List<WrongQuestionVO> wrongBook(Long userId) {
        List<QuestionResult> wrongs = resultMapper.selectList(new LambdaQueryWrapper<QuestionResult>()
                .eq(QuestionResult::getUserId, userId)
                .eq(QuestionResult::getCorrect, false)
                .orderByDesc(QuestionResult::getCreateTime));
        if (wrongs.isEmpty()) {
            return List.of();
        }
        Map<Long, Question> questionMap = questionService
                .listByIdsOrAll(wrongs.stream().map(QuestionResult::getQuestionId).distinct().toList(), false)
                .stream()
                .collect(Collectors.toMap(Question::getId, Function.identity(), (a, b) -> a));

        // questionId -> 最近一次错答记录（列表已按时间倒序，首次出现即最近）
        Map<Long, QuestionResult> latestWrong = new LinkedHashMap<>();
        Map<Long, Long> wrongCount = new LinkedHashMap<>();
        for (QuestionResult r : wrongs) {
            latestWrong.putIfAbsent(r.getQuestionId(), r);
            wrongCount.merge(r.getQuestionId(), 1L, Long::sum);
        }

        List<WrongQuestionVO> list = new ArrayList<>(latestWrong.size());
        for (Map.Entry<Long, QuestionResult> entry : latestWrong.entrySet()) {
            Question q = questionMap.get(entry.getKey());
            if (q == null) {
                continue;
            }
            WrongQuestionVO vo = new WrongQuestionVO();
            vo.setQuestionId(q.getId());
            vo.setQuestionName(q.getName());
            vo.setType(q.getType());
            vo.setDifficulty(q.getDifficulty());
            vo.setOptions(q.getOptions());
            vo.setCorrectAnswer(q.getAnswer());
            vo.setAnalysis(q.getAnalysis());
            vo.setCourseId(q.getCourseId());
            vo.setCourseName(q.getCourseName());
            vo.setMyAnswer(entry.getValue().getUserAnswer());
            vo.setLastWrongTime(entry.getValue().getCreateTime());
            vo.setWrongCount(wrongCount.getOrDefault(q.getId(), 0L));
            list.add(vo);
        }
        log.info("错题本查询：userId={}, 错题 {} 道", userId, list.size());
        return list;
    }

    /**
     * 教师端答题情况总览：整体正确率 + 每题正确率 + 每位学员正确率。
     * 仅统计学员（user.type=2）的答题记录，保证教师看到的是学习者画像。
     */
    public AnswerOverviewVO teacherOverview() {
        List<QuestionResult> all = resultMapper.selectList(new LambdaQueryWrapper<QuestionResult>()
                .orderByDesc(QuestionResult::getCreateTime));
        AnswerOverviewVO vo = new AnswerOverviewVO();
        if (all.isEmpty()) {
            vo.setQuestionStats(List.of());
            vo.setStudentStats(List.of());
            return vo;
        }
        long correctTotal = all.stream().filter(r -> Boolean.TRUE.equals(r.getCorrect())).count();
        vo.setTotalRecords(all.size());
        vo.setAccuracy(round1(correctTotal * 100.0 / all.size()));

        // 题目维度
        Map<Long, Question> questionMap = questionService
                .listByIdsOrAll(all.stream().map(QuestionResult::getQuestionId).distinct().toList(), false)
                .stream()
                .collect(Collectors.toMap(Question::getId, Function.identity(), (a, b) -> a));
        Map<Long, List<QuestionResult>> byQuestion = all.stream()
                .filter(r -> r.getQuestionId() != null)
                .collect(Collectors.groupingBy(QuestionResult::getQuestionId, LinkedHashMap::new, Collectors.toList()));
        List<QuestionStatVO> questionStats = new ArrayList<>(byQuestion.size());
        for (Map.Entry<Long, List<QuestionResult>> e : byQuestion.entrySet()) {
            QuestionStatVO s = new QuestionStatVO();
            s.setQuestionId(e.getKey());
            Question q = questionMap.get(e.getKey());
            s.setQuestionName(q == null ? "题目 #" + e.getKey() : q.getName());
            s.setCourseId(q == null ? null : q.getCourseId());
            s.setCourseName(q == null ? null : q.getCourseName());
            long total = e.getValue().size();
            long correct = e.getValue().stream().filter(r -> Boolean.TRUE.equals(r.getCorrect())).count();
            s.setTotalCount(total);
            s.setCorrectCount(correct);
            s.setAccuracy(round1(correct * 100.0 / total));
            questionStats.add(s);
        }
        // 正确率升序：最需要关注的薄弱题目排前面
        questionStats.sort(Comparator.comparingDouble(QuestionStatVO::getAccuracy));
        vo.setQuestionStats(questionStats);
        vo.setTotalQuestions(questionStats.size());

        // 学员维度
        Map<Long, List<QuestionResult>> byStudent = all.stream()
                .filter(r -> r.getUserId() != null)
                .collect(Collectors.groupingBy(QuestionResult::getUserId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, UserDTO> userMap = safeQueryUsers(new ArrayList<>(byStudent.keySet()));
        List<StudentAnswerStatVO> studentStats = new ArrayList<>(byStudent.size());
        for (Map.Entry<Long, List<QuestionResult>> e : byStudent.entrySet()) {
            List<QuestionResult> records = e.getValue();
            StudentAnswerStatVO s = new StudentAnswerStatVO();
            s.setUserId(e.getKey());
            UserDTO user = userMap.get(e.getKey());
            s.setUsername(user == null ? ("学员 #" + e.getKey()) : (user.getName() != null ? user.getName() : user.getUsername()));
            s.setCellPhone(user == null ? "" : user.getCellPhone());
            long correct = records.stream().filter(r -> Boolean.TRUE.equals(r.getCorrect())).count();
            s.setTotalCount(records.size());
            s.setCorrectCount(correct);
            s.setAccuracy(round1(correct * 100.0 / records.size()));
            s.setLastAnswerTime(records.stream()
                    .map(QuestionResult::getCreateTime)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(null));
            studentStats.add(s);
        }
        // 正确率降序：表现最好的学员排前面
        studentStats.sort(Comparator.comparingDouble(StudentAnswerStatVO::getAccuracy).reversed());
        vo.setStudentStats(studentStats);
        vo.setTotalStudents(studentStats.size());
        return vo;
    }

    /** 某题目的所有作答记录（教师端查看单题作答明细） */
    public List<QuestionResult> listByQuestion(Long questionId) {
        return resultMapper.selectList(new LambdaQueryWrapper<QuestionResult>()
                .eq(QuestionResult::getQuestionId, questionId)
                .orderByDesc(QuestionResult::getCreateTime));
    }

    // ============ 私有方法 ============

    /** 判分：忽略选项字母顺序与大小写（AB 与 BA 等价），多选需完全一致 */
    private boolean isCorrect(Question question, String userAnswer) {
        String expected = normalizeAnswer(question.getAnswer());
        String actual = normalizeAnswer(userAnswer);
        if (expected.isEmpty() || actual.isEmpty()) {
            return false;
        }
        return sortChars(expected).equals(sortChars(actual));
    }

    private String sortChars(String s) {
        char[] chars = s.toCharArray();
        java.util.Arrays.sort(chars);
        return new String(chars);
    }

    private String normalizeAnswer(String answer) {
        return answer == null ? "" : answer.replaceAll("[^A-Za-z]", "").toUpperCase();
    }

    private double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    /** 批量拉取用户信息（失败降级为空映射，不阻断统计） */
    private Map<Long, UserDTO> safeQueryUsers(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        try {
            List<UserDTO> users = userClient.queryUserByIds(userIds);
            if (users == null) {
                return Map.of();
            }
            return users.stream()
                    .filter(u -> u.getId() != null)
                    .collect(Collectors.toMap(UserDTO::getId, Function.identity(), (a, b) -> a));
        } catch (Exception e) {
            log.warn("拉取用户信息失败，降级为占位名称：{}", e.getMessage());
            return Map.of();
        }
    }
}
