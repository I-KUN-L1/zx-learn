package com.zhixing.exam.domain.po;

import lombok.Data;

/**
 * 题目
 */
@Data
public class Question {

    private Long id;
    private String name;
    private Integer type;
    private Integer difficulty;
    private Integer score;
    private String content;
    private String options;
    private String answer;
    private String analysis;
}
