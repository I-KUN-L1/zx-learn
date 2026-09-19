package com.zhixing.pay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixing.pay.domain.po.PayOrder;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface PayOrderMapper extends BaseMapper<PayOrder> {

    /**
     * 按业务订单号「插入或复用」（原子，依赖唯一键 {@code uk_biz_order_no}）。
     * <p>
     * 用 {@code ON DUPLICATE KEY UPDATE} 而非"先查后插"：并发重复申请时不会抛
     * {@code DuplicateKeyException}，也就不会污染外层事务的 rollback-only 标记，
     * 一条 SQL 内完成"要么新建、要么复用"。
     * <ul>
     *   <li>{@code status = IF(status = 2, 0, status)}：已关闭的单再次申请 → 复活为待支付；</li>
     *   <li>{@code deleted = 0}：逻辑删除过的单被重新申请 → 一并复活；</li>
     *   <li>不覆盖 {@code id}/{@code create_time}，复用原有支付单主键与创建时间。</li>
     * </ul>
     * <p>
     * 注意：MyBatis-Plus 不重写原生 {@code @Insert}，故 {@code deleted} 必须显式写出（不受 {@code @TableLogic} 影响）。
     */
    @Insert("INSERT INTO pay_order (id, biz_order_no, amount, channel, status, pay_url, create_time, update_time, deleted) "
            + "VALUES (#{o.id}, #{o.bizOrderNo}, #{o.amount}, #{o.channel}, #{o.status}, #{o.payUrl}, NOW(), NOW(), 0) "
            + "ON DUPLICATE KEY UPDATE "
            + "amount = VALUES(amount), "
            + "channel = VALUES(channel), "
            + "pay_url = VALUES(pay_url), "
            + "status = IF(status = 2, 0, status), "
            + "deleted = 0, "
            + "update_time = NOW()")
    int upsertOnBizOrderNo(@Param("o") PayOrder order);
}
