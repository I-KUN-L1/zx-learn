package com.zhixing.trade.controller;

import com.zhixing.common.domain.R;
import com.zhixing.trade.domain.po.Cart;
import com.zhixing.trade.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 购物车
 */
@RestController
@RequestMapping("/carts")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @PostMapping
    public R<Void> add(@RequestBody Cart item) {
        cartService.add(item);
        return R.ok();
    }

    @GetMapping
    public R<List<Cart>> list() {
        return R.ok(cartService.list());
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        cartService.delete(id);
        return R.ok();
    }

    @DeleteMapping
    public R<Void> deleteBatch(@RequestBody List<Long> ids) {
        cartService.deleteBatch(ids);
        return R.ok();
    }
}