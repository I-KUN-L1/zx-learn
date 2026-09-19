package com.zhixing.trade.service;

import com.zhixing.api.dto.course.CourseSimpleInfoDTO;
import com.zhixing.common.constants.Constant;
import com.zhixing.common.exceptions.BadRequestException;

/**
 * 课程「可购买性」校验（交易侧新增购买的唯一收口）。
 *
 * <h3>为什么需要它</h3>
 * 课程下架（{@code course.status = 0}）后，展示侧靠查询过滤让它在列表/搜索/详情消失，
 * 但交易侧是<b>写入口</b>：如果下单与加购不做状态校验，下架课程仍可能被塞进购物车、
 * 甚至成交，形成"前台看不见、后台还能买"的漏洞。
 *
 * <h3>校验口径</h3>
 * <ul>
 *   <li>{@code course == null} → 400「课程不存在」；</li>
 *   <li>{@code status != 1}（已下架 / 未上架）→ 400「课程已下架」；</li>
 *   <li>{@code status == null} → 放行（历史数据可能没有该字段，宁可放行也不误伤正常下单）。</li>
 * </ul>
 * <b>调用时机的硬约束</b>：必须在"是否已拥有"的幂等判定<b>之后</b>调用。
 * 否则已购学员对一门后来下架的免费课点「加入学习」时，会被这里拦成"课程已下架"，
 * 而他本应拿到幂等的成功响应继续学习。
 *
 * <p>覆盖的三处入口：{@code CartService#add}（加购）、
 * {@code OrderService#placeOrder}（付费下单）、{@code OrderService#freeCourse}（0 元开课）。
 * 已存在的订单、支付与退款流程<b>不受影响</b>，已购学员的学习资产不会被追溯阻断。
 */
public final class CoursePurchaseGuard {

    private CoursePurchaseGuard() {
    }

    /**
     * 校验课程当前可购买，不可购买直接抛 400。
     *
     * @param course 课程简信息（来自 course-service 的内部接口，含 status）
     */
    public static void requirePurchasable(CourseSimpleInfoDTO course) {
        if (course == null) {
            throw new BadRequestException("课程不存在");
        }
        if (course.getStatus() != null && course.getStatus() != Constant.COURSE_STATUS_ON_SHELF) {
            throw new BadRequestException("课程已下架");
        }
    }
}
