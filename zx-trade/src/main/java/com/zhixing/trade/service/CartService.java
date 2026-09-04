package com.zhixing.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.utils.UserContext;
import com.zhixing.trade.domain.po.Cart;
import com.zhixing.trade.mapper.CartMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 购物车服务
 */
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartMapper cartMapper;

    /**
     * 加入购物车：同一用户同一课程只保留一条（更新快照）
     */
    public void add(Cart item) {
        if (item == null || item.getCourseId() == null) {
            throw new BadRequestException("课程 id 不能为空");
        }
        Long userId = UserContext.getUserId();
        Cart exist = cartMapper.selectOne(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getCourseId, item.getCourseId()));
        if (exist != null) {
            exist.setCourseName(item.getCourseName());
            exist.setCoursePrice(item.getCoursePrice());
            cartMapper.updateById(exist);
            return;
        }
        item.setUserId(userId);
        cartMapper.insert(item);
    }

    public List<Cart> list() {
        return cartMapper.selectList(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, UserContext.getUserId())
                .orderByDesc(Cart::getCreateTime));
    }

    public void delete(Long id) {
        cartMapper.deleteById(id);
    }

    public void deleteBatch(List<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            cartMapper.deleteBatchIds(ids);
        }
    }
}