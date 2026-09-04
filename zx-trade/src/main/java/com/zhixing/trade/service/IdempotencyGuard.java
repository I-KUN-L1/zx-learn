package com.zhixing.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixing.trade.domain.po.ConsumeRecord;
import com.zhixing.trade.mapper.ConsumeRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * MQ 消费幂等守卫（幂等第二层）：通过消费流水表唯一键去重。
 * <p>
 * 需在事务内调用：消费流水与业务操作同事务提交，失败一并回滚，重投时可重新处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyGuard {

    private final ConsumeRecordMapper consumeRecordMapper;

    /**
     * 尝试获取消费资格
     *
     * @return true 表示首次消费（可以执行业务）；false 表示已消费（幂等跳过）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean tryConsume(String consumeKey, String topic, String tag) {
        if (consumeKey == null || consumeKey.isBlank()) {
            return true;
        }
        Long cnt = consumeRecordMapper.selectCount(new LambdaQueryWrapper<ConsumeRecord>()
                .eq(ConsumeRecord::getConsumeKey, consumeKey));
        if (cnt != null && cnt > 0) {
            return false;
        }
        ConsumeRecord record = new ConsumeRecord();
        record.setConsumeKey(consumeKey);
        record.setTopic(topic);
        record.setTag(tag);
        record.setStatus(1);
        try {
            consumeRecordMapper.insert(record);
            return true;
        } catch (DuplicateKeyException e) {
            // 并发下另一线程已消费
            return false;
        }
    }
}