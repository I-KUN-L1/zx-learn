package com.zhixing.common.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * 精度安全的 Long 序列化器。
 * <p>
 * 背景：本项目的订单 id / 用户 id / 优惠券 id 由雪花算法生成（约 19 位十进制），
 * 已超出 JavaScript {@code Number.MAX_SAFE_INTEGER}（2^53-1 = 9007199254740991）。
 * 若按 JSON number 输出，浏览器 {@code JSON.parse} 会做二进制浮点舍入，
 * 末几位被改写，再用该 id 回请求后端就会命中"记录不存在"。
 *
 * <p>已由此修复的真实缺陷：
 * <ul>
 *   <li>下单后订单进入待支付，但"去支付"返回"订单不存在"（订单 id 被舍入）；</li>
 *   <li>领取"联调满减券"等雪花 id 优惠券时报"优惠券不存在"（优惠券 id 被舍入）。</li>
 * </ul>
 *
 * <p>策略：仅在超出 JS 安全整数范围时输出字符串，其余（自增 id、金额、分页条数等）
 * 仍输出 JSON number，保持既有前后端契约与数值比较/分页组件不受影响。
 */
public class SafeLongSerializer extends JsonSerializer<Long> {

    /** JavaScript Number.MAX_SAFE_INTEGER */
    public static final long JS_MAX_SAFE_INTEGER = 9007199254740991L;

    @Override
    public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        if (value > JS_MAX_SAFE_INTEGER || value < -JS_MAX_SAFE_INTEGER) {
            gen.writeString(value.toString());
        } else {
            gen.writeNumber(value.longValue());
        }
    }
}
