package com.zhixing.trade.domain.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理员端订单查询条件。
 * <p>
 * 刻意不继承 {@code PageQuery}：后者的 {@code sortBy} 会被直接拼进 SQL 的 ORDER BY，
 * 而管理端筛选条件来自 URL，继承将引入 SQL 注入面。此处分页参数独立声明，
 * 排序固定为 create_time desc。
 */
@Data
public class AdminOrderQueryDTO implements Serializable {

    private Integer pageNo = 1;

    private Integer pageSize = 10;

    /** 订单号（精确 or 前缀匹配） */
    private String orderNo;

    /** 下单用户 id */
    private Long userId;

    /** 关键词：用户名 / 手机号 / 课程名 模糊匹配 */
    private String keyword;

    /** 订单状态：0 待支付 1 已支付 2 已关闭 3 退款中 4 已退款 */
    private Integer status;

    private Long courseId;

    /** 下单时间起（含），ISO-8601，如 2026-09-01T00:00:00 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime beginTime;

    /** 下单时间止（含），ISO-8601，如 2026-09-11T23:59:59 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;

    /** 实付金额下限（分） */
    private Long minAmount;

    /** 实付金额上限（分） */
    private Long maxAmount;

    /** 归一化分页参数，防止负值/超大页导致全表扫描 */
    public int safePageNo() {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    public int safePageSize() {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 200);
    }
}
