package com.zhixing.exam.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 错题本条目 VO：题目完整信息 + 我的作答 + 正确答案 + 解析。
 * 学员端"错题本"页面的数据契约。
 */
@Data
public class WrongQuestionVO implements Serializable {

    private Long questionId;

    private String questionName;

    /** 1单选 2多选 3判断 */
    private Integer type;

    private Integer difficulty;

    private List<String> options;

    /** 正确答案（选项字母） */
    private String correctAnswer;

    /** 我的作答（选项字母，最近一次） */
    private String myAnswer;

    /** 答案解析 */
    private String analysis;

    private Long courseId;

    private String courseName;

    /** 该题累计答错次数 */
    private long wrongCount;

    /** 最近一次答错时间 */
    private LocalDateTime lastWrongTime;
}
