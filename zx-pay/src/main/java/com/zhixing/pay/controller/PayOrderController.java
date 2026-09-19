package com.zhixing.pay.controller;

import com.zhixing.common.domain.R;
import com.zhixing.common.exceptions.BadRequestException;
import com.zhixing.common.utils.InternalOnlyGuard;
import com.zhixing.pay.domain.po.PayOrder;
import com.zhixing.pay.service.PayOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 支付单 / 支付渠道 / 渠道回调。
 * <p>
 * 全量测试（2026-09-15）在支付域发现并修复的问题，本类与 {@link PayOrderService} 共同承载：
 * <ol>
 *   <li><b>支付单内存态</b>（BUG-007）：原为进程内 {@code LinkedHashMap}（访问序 LRU，上限 10000）。
 *       有界 LRU 只是不再 OOM，但重启即丢、多实例不一致、淘汰即静默消失 —— 资金数据不可接受。
 *       现已落库 {@code zx_pay.pay_order}（唯一键 {@code uk_biz_order_no} 保证一单一支付单）。</li>
 *   <li><b>支付状态误报</b>（BUG-008）：原 {@code GET /pay-orders/{id}/status} 对**任意** id 都返回
 *       {@code status=1 支付成功}，调用方无法区分"支付单不存在"与"已支付"。现查库返回真实状态，
 *       不存在则 404 业务码。</li>
 *   <li><b>回调无验签且不可达</b>（BUG-010）：{@code /notify/*} 原先任何人构造请求即被视为支付成功，
 *       同时这两个路径未进网关白名单 —— 真实渠道回调（无 JWT）根本到不了本服务。
 *       现在：网关白名单已放行 {@code /notify/alipay}、{@code /notify/wxpay}（见 JwtProperties），
 *       本类用 HMAC-SHA256 验签（{@code pay.notify.secret}）做真实性校验，未配置密钥 fail-closed(501)，
 *       验签失败 401，同一笔回调按 {@code (channel, payNo)} 唯一键幂等。</li>
 * </ol>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class PayOrderController {

    /** 回调报文参与签名的字段之外的元数据（不参与签名计算） */
    private static final String SIGN_FIELD = "sign";

    private final PayOrderService payOrderService;

    /** 回调验签密钥（未配置 → 回调一律拒绝；与 zx-trade 的 pay.callback-secret 同源 PAY_CALLBACK_SECRET） */
    @Value("${pay.notify.secret:}")
    private String notifySecret;

    @GetMapping("/pay-channels/list")
    public R<Object> channels() {
        return R.ok(List.of(
                Map.of("id", 1, "name", "支付宝"),
                Map.of("id", 2, "name", "微信支付")));
    }

    /**
     * 申请支付单（仅服务间内部调用）。
     * 幂等：同一 {@code bizOrderNo} 重复申请复用同一张支付单，不会重复生成。
     */
    @PostMapping("/pay-orders")
    public R<Map<String, Object>> apply(@RequestBody Map<String, Object> request) {
        InternalOnlyGuard.checkInternal();
        if (request == null || request.get("bizOrderNo") == null
                || String.valueOf(request.get("bizOrderNo")).isBlank()) {
            throw new BadRequestException("支付单参数不完整");
        }
        String bizOrderNo = String.valueOf(request.get("bizOrderNo"));
        PayOrder order = payOrderService.applyOrReuse(
                bizOrderNo, toLong(request.get("amount")), toInt(request.get("channel")));
        return R.ok(toView(order));
    }

    /**
     * 查询支付状态：返回库中真实状态（0待支付/1已支付/2已关闭）；查不到 → 404 业务码。
     */
    @GetMapping("/pay-orders/{bizOrderId}/status")
    public R<Map<String, Object>> status(@PathVariable String bizOrderId) {
        PayOrder order = payOrderService.findStatus(bizOrderId);
        if (order == null) {
            return R.error(404, "支付单不存在");
        }
        return R.ok(toView(order));
    }

    @PostMapping("/notify/alipay")
    public R<String> alipayNotify(@RequestBody Map<String, Object> body) {
        return handleNotify("alipay", body);
    }

    @PostMapping("/notify/wxpay")
    public R<String> wxpayNotify(@RequestBody Map<String, Object> body) {
        return handleNotify("wxpay", body);
    }

    /**
     * 统一回调处理：验签通过才受理。
     * <ul>
     *   <li>未配置密钥 → {@code 501}（fail-closed，绝不"裸奔放行"）；</li>
     *   <li>缺签名 / 验签不通过 → {@code 401}；</li>
     *   <li>缺 {@code payNo} → {@code 400}（无流水号无法做幂等，必须拒绝）；</li>
     *   <li>重复投递 → 幂等返回 success（渠道超时重试是标准行为）。</li>
     * </ul>
     */
    private R<String> handleNotify(String channel, Map<String, Object> body) {
        if (notifySecret == null || notifySecret.isBlank()) {
            log.error("{} 回调被拒：未配置 pay.notify.secret（fail-closed）", channel);
            return R.error(501, "支付回调未配置验签密钥，已拒绝");
        }
        Object signObj = body == null ? null : body.get(SIGN_FIELD);
        if (signObj == null) {
            log.warn("{} 回调被拒：缺少 sign 字段", channel);
            return R.error(401, "回调验签失败");
        }
        if (!hmacSha256(buildSignPayload(body), notifySecret).equalsIgnoreCase(String.valueOf(signObj))) {
            log.warn("{} 回调被拒：验签不通过", channel);
            return R.error(401, "回调验签失败");
        }
        String payNo = str(body.get("payNo"));
        if (payNo == null || payNo.isBlank()) {
            throw new BadRequestException("回调缺少 payNo，无法幂等受理");
        }
        boolean first = payOrderService.acceptNotify(channel, str(body.get("bizOrderNo")), payNo, String.valueOf(body));
        log.info("{} 回调验签通过并受理：payNo={}, firstTime={}", channel, payNo, first);
        return R.ok("success");
    }

    /** 参与签名的内容：剔除 sign 后按 key 字典序拼接 k=v&... */
    private String buildSignPayload(Map<String, Object> body) {
        StringBuilder sb = new StringBuilder();
        body.entrySet().stream()
                .filter(e -> !SIGN_FIELD.equals(e.getKey()) && e.getValue() != null)
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> sb.append(e.getKey()).append('=').append(e.getValue()).append('&'));
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    private String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            log.error("回调验签计算失败", e);
            return "";
        }
    }

    private Map<String, Object> toView(PayOrder order) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", order.getId());
        view.put("bizOrderId", order.getId());
        view.put("bizOrderNo", order.getBizOrderNo());
        view.put("amount", order.getAmount());
        view.put("channel", order.getChannel());
        view.put("status", order.getStatus());
        view.put("msg", statusMsg(order.getStatus()));
        view.put("payUrl", order.getPayUrl());
        if (order.getPayNo() != null) {
            view.put("payNo", order.getPayNo());
        }
        if (order.getNotifyTime() != null) {
            view.put("notifyTime", order.getNotifyTime().toString());
        }
        return view;
    }

    private String statusMsg(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case PayOrderService.STATUS_PENDING -> "待支付";
            case PayOrderService.STATUS_PAID -> "支付成功";
            case PayOrderService.STATUS_CLOSED -> "已关闭";
            default -> "未知";
        };
    }

    private static Long toLong(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer toInt(Object v) {
        Long l = toLong(v);
        return l == null ? null : l.intValue();
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
