package com.zhixing.aigc.tools;

import com.zhixing.api.client.trade.TradeClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 订单工具：供 Agent 调用查询购买信息
 */
@Component
@RequiredArgsConstructor
public class OrderTools {

    private final TradeClient tradeClient;

    public Boolean checkCourseBought(Long courseId) {
        return tradeClient.checkCourseBought(courseId);
    }

    public Integer countEnrollNum(Long courseId) {
        return tradeClient.countEnrollNum(courseId);
    }
}
