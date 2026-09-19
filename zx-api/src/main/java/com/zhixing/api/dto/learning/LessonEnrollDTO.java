package com.zhixing.api.dto.learning;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 开课（写入我的课表）请求体。
 * <p>
 * 由交易服务在「订单支付成功 / 免费课直接到账」后<b>同步</b>调用学习服务，
 * 让课表立即出现该课程——这是「课程界面」与「我的课表」两端状态实时联动的前提：
 * 两端都以课表（lesson）为「已拥有」的权威口径，课表不同步就会出现
 * 「课程界面已拥有、我的课表里却没有」的割裂。
 * <p>
 * 幂等：学习服务侧由 lesson.uk_user_course 唯一索引兜底，重复调用不会产生重复课表项；
 * MQ 事件（订单支付成功）仍然保留，作为同步调用失败时的最终一致兜底。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LessonEnrollDTO implements Serializable {

    private Long userId;

    private Long courseId;

    private String courseName;
}
