package com.zhixing.exam.domain.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 交卷后的单题判分结果 VO。
 * <p>
 * 由<b>服务端</b>判定正误并回传给前端，前端据此即时展示对错与解析，
 * 避免把判分权交给客户端（可伪造成绩）。
 */
@Data
public class SubmitResultVO implements Serializable {

    private Long questionId;

    private String questionName;

    /** 我的作答 */
    private String userAnswer;

    /** 正确答案 */
    private String correctAnswer;

    /** 是否答对 */
    private Boolean correct;

    /** 得分 */
    private Integer score;

    /** 答案解析 */
    private String analysis;
}
