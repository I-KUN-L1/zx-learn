package com.zhixing.api.client.trade;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 购物车客户端
 */
@FeignClient(value = "trade-service", contextId = "cartClient")
public interface CartClient {

    @DeleteMapping("/carts")
    void deleteCartByIds(@RequestParam("ids") List<Long> ids);
}
