package com.zhixing.trade.controller;

import com.zhixing.common.annotation.RequireRole;
import com.zhixing.common.constants.UserRole;
import com.zhixing.common.domain.R;
import com.zhixing.trade.domain.po.Cart;
import com.zhixing.trade.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 购物车。
 * 权限：购物车为学员端行为，仅学员(2)可操作，管理员/教师一律 403。
 */
@RestController
@RequestMapping("/carts")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @PostMapping
    @RequireRole(UserRole.STUDENT)
    public R<Void> add(@RequestBody Cart item) {
        cartService.add(item);
        return R.ok();
    }

    @GetMapping
    @RequireRole(UserRole.STUDENT)
    public R<List<Cart>> list() {
        return R.ok(cartService.list());
    }

    @DeleteMapping("/{id}")
    @RequireRole(UserRole.STUDENT)
    public R<Void> delete(@PathVariable Long id) {
        cartService.delete(id);
        return R.ok();
    }

    /**
     * 按课程 id 移除购物车条目（对齐前端 removeFromCart(courseId) 契约）
     */
    @DeleteMapping("/course/{courseId}")
    @RequireRole(UserRole.STUDENT)
    public R<Void> deleteByCourseId(@PathVariable Long courseId) {
        cartService.deleteByCourseId(courseId);
        return R.ok();
    }

    /**
     * 清空当前用户购物车。兼容空请求体（前端清空按钮不传参）与批量 id 两种调用。
     */
    @DeleteMapping
    @RequireRole(UserRole.STUDENT)
    public R<Void> clear(@RequestBody(required = false) List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            cartService.clearMine();
        } else {
            cartService.deleteBatch(ids);
        }
        return R.ok();
    }
}