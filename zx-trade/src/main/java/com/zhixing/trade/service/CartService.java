package com.zhixing.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixing.api.client.course.CourseClient;
import com.zhixing.api.dto.course.CourseSimpleInfoDTO;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.utils.UserContext;
import com.zhixing.trade.domain.po.Cart;
import com.zhixing.trade.mapper.CartMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 购物车服务
 * <p>
 * 快照策略：课程名称/价格由后端通过 CourseClient 实时补全落库，
 * 前端仅需传 courseId（防止前端伪造价格与快照缺失导致的列表空白）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartMapper cartMapper;
    private final CourseClient courseClient;

    /**
     * 加入购物车：同一用户同一课程只保留一条；快照由后端查课程服务补全
     */
    public void add(Cart item) {
        if (item == null || item.getCourseId() == null) {
            throw new BadRequestException("课程 id 不能为空");
        }
        CourseSimpleInfoDTO course = courseClient.queryCourseInfoById(item.getCourseId());
        // 下架课程不允许再被加购：购物车是用户可见的"可购买"入口，
        // 必须与课程上下架状态实时一致（校验口径与调用时机见 CoursePurchaseGuard）
        CoursePurchaseGuard.requirePurchasable(course);
        Long userId = UserContext.getUserId();
        String courseName = course.getName() == null ? "" : course.getName();
        Long coursePrice = course.getPrice() == null ? 0L : course.getPrice();
        Cart exist = cartMapper.selectOne(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getCourseId, item.getCourseId()));
        if (exist != null) {
            exist.setCourseName(courseName);
            exist.setCoursePrice(coursePrice);
            cartMapper.updateById(exist);
            return;
        }
        item.setUserId(userId);
        item.setCourseName(courseName);
        item.setCoursePrice(coursePrice);
        cartMapper.insert(item);
    }

    public List<Cart> list() {
        return cartMapper.selectList(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, UserContext.getUserId())
                .orderByDesc(Cart::getCreateTime));
    }

    /** 按条目 id 删除（仅限本人条目） */
    public void delete(Long id) {
        cartMapper.delete(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getId, id)
                .eq(Cart::getUserId, UserContext.getUserId()));
    }

    /**
     * 按课程 id 删除（对齐前端 removeFromCart(courseId) 契约）
     */
    public void deleteByCourseId(Long courseId) {
        if (courseId == null) {
            throw new BadRequestException("课程 id 不能为空");
        }
        cartMapper.delete(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, UserContext.getUserId())
                .eq(Cart::getCourseId, courseId));
    }

    /** 清空当前用户购物车 */
    public void clearMine() {
        cartMapper.delete(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, UserContext.getUserId()));
    }

    public void deleteBatch(List<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            cartMapper.deleteBatchIds(ids);
        }
    }
}
