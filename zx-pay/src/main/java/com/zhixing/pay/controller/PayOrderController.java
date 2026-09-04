package com.zhixing.pay.controller;

import com.zhixing.common.domain.R;
import com.zhixing.common.exceptions.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 支付单
 */
@Slf4j
@RestController
public class PayOrderController {

    private final Map<Long, Map<String, Object>> payOrders = new ConcurrentHashMap<>();
    private final AtomicLong idGen = new AtomicLong(1);

    @GetMapping("/pay-channels/list")
    public R<Object> channels() {
        return R.ok(java.util.List.of(
                Map.of("id", 1, "name", "支付宝"),
                Map.of("id", 2, "name", "微信支付")));
    }

    @PostMapping("/pay-orders")
    public R<Map<String, Object>> apply(@RequestBody Map<String, Object> request) {
        if (request == null || request.get("bizOrderNo") == null) {
            throw new BadRequestException("支付单参数不完整");
        }
        Long id = idGen.getAndIncrement();
        request.put("id", id);
        request.put("payUrl", "weixin://wxpay/bizpayurl?pr=xxxx");
        payOrders.put(id, request);
        log.info("创建支付单：id={}, bizOrderNo={}", id, request.get("bizOrderNo"));
        return R.ok(request);
    }

    @GetMapping("/pay-orders/{bizOrderId}/status")
    public R<Map<String, Object>> status(@PathVariable Long bizOrderId) {
        return R.ok(Map.of("bizOrderId", bizOrderId, "status", 1, "msg", "支付成功"));
    }

    @PostMapping("/notify/alipay")
    public R<String> alipayNotify(@RequestBody Map<String, Object> body) {
        log.info("收到支付宝回调：{}", body);
        return R.ok("success");
    }

    @PostMapping("/notify/wxpay")
    public R<String> wxpayNotify(@RequestBody Map<String, Object> body) {
        log.info("收到微信回调：{}", body);
        return R.ok("success");
    }
}
