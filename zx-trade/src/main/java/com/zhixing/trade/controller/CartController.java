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

    @DeleteMapping
    @RequireRole(UserRole.STUDENT)
    public R<Void> deleteBatch(@RequestBody List<Long> ids) {
        cartService.deleteBatch(ids);
        return R.ok();
    }
}