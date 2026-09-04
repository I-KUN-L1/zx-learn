package com.zhixing.gateway.ratelimit;

import com.zhixing.gateway.filter.AuthGlobalFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 秒杀接口限流 key 解析器：IP + 用户 双维度组合 key。
 * <p>
 * 网关限流使用 Redis 令牌算法（RequestRateLimiter）：
 * <ul>
 *   <li>未登录（无 user-info 头）按 IP 限流，防脚本刷接口；</li>
 *   <li>同一用户换 IP（或同一 IP 多用户）均被拆分为独立 token 桶，维度互补；</li>
 * </ul>
 * 配置于 seckill-claim 路由，仅在秒杀领取（POST）生效，轮询接口不受影响。
 */
@Component
public class SeckillKeyResolver implements KeyResolver {

    public static final String KEY_PREFIX = "rate:seckill:";

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        String userId = exchange.getRequest().getHeaders().getFirst(AuthGlobalFilter.USER_INFO_HEADER);
        String ip = exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
        return Mono.just(KEY_PREFIX + ip + ":" + (userId == null ? "anon" : userId));
    }
}
