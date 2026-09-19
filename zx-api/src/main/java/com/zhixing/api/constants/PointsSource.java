package com.zhixing.api.constants;

/**
 * 积分来源标识（跨服务共享契约）。
 * <p>
 * 积分明细落在 zx-learning，但加分动作分散在多个服务（学习、签到、测验、讨论）。
 * 来源字符串一旦不一致，积分明细的"来源"分类就会碎片化，
 * 因此把权威值集中在此，zx-learning 的 PointsService 与调用方共同引用。
 */
public final class PointsSource {

    private PointsSource() {
    }

    /** 完成小节学习 */
    public static final String LESSON = "LESSON";

    /** 完成整门课程 */
    public static final String COURSE = "COURSE";

    /** 测验答对 */
    public static final String QUIZ = "QUIZ";

    /** 每日签到 */
    public static final String SIGN = "SIGN";

    /** 发布讨论话题 */
    public static final String DISCUSSION = "DISCUSSION";

    /** 回复讨论 */
    public static final String REPLY = "REPLY";

    /** 测验答对一题的积分（与 zx-learning PointsService 的规则保持一致） */
    public static final int POINTS_QUIZ_CORRECT = 5;
}
