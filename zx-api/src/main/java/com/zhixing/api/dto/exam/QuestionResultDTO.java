package com.zhixing.api.dto.exam;

import lombok.Data;

import java.io.Serializable;

/**
 * 答题记录 DTO（跨服务）
 */
@Data
public class QuestionResultDTO implements Serializable {

    private Long id;
    private Long userId;
    private Long questionId;
    private String questionName;
    private Boolean correct;
    private Integer score;
}
