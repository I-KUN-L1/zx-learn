package com.zhixing.common.utils;

import com.zhixing.common.exceptions.ForbiddenException;

/**
 * 内部接口访问守卫：仅允许服务间 Feign 直连调用，拒绝一切来自网关的外部请求。
 *
 * <p>判定依据与 {@link OwnerAccessGuard} 一致：网关认证后必定注入 user-info 头，
 * 而 Feign 内部调用仅透传 requestId、不携带该头。因此：
 * <ul>
 *   <li>请求无 user-info 头（UserContext.getUser() == null）→ 服务间 Feign 直连，放行；</li>
 *   <li>请求带 user-info 头（任何已登录角色，含员工）→ 抛 ForbiddenException(403)。</li>
 * </ul>
 *
 * <p>适用场景：统计聚合等仅供其他服务消费的内部端点（如订单统计、日活统计），
 * 即便被网关路由对外暴露，也不允许任何外部用户（包括管理员）直接访问。
 *
 * <p>注意：被保护路径严禁加入网关 excludePaths 白名单，否则未登录请求会被误判为内部调用而放行。
 */
public final class InternalOnlyGuard {

    private InternalOnlyGuard() {
    }

    /** 校验当前请求为服务间内部调用，外部用户（含员工）访问抛 ForbiddenException(403) */
    public static void checkInternal() {
        if (UserContext.getUser() != null) {
            throw new ForbiddenException("该接口仅限内部服务调用");
        }
    }
}
