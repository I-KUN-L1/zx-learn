package com.zhixing.course.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.api.dto.trade.QuotaMsg;
import com.zhixing.common.mq.MqHandler;
import com.zhixing.common.mq.MqTopics;
import com.zhixing.course.service.CourseQuotaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 课程名额事件处理器：LOCK 锁定 / CONFIRM 确认(转销量) / RELEASE 释放。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuotaHandler implements MqHandler {

    private final CourseQuotaService courseQuotaService;
    private final ObjectMapper objectMapper;

    @Override
    public Set<String> subscribeTopics() {
        return Set.of(MqTopics.TOPIC_COURSE_QUOTA);
    }

    @Override
    public boolean supports(String topic, String tag) {
        return MqTopics.TOPIC_COURSE_QUOTA.equals(topic);
    }

    @Override
    public void handle(MessageExt message) throws Exception {
        QuotaMsg msg = objectMapper.readValue(
                new String(message.getBody(), StandardCharsets.UTF_8), QuotaMsg.class);
        switch (message.getTags()) {
            case MqTopics.Tags.QUOTA_LOCK -> courseQuotaService.lock(msg);
            case MqTopics.Tags.QUOTA_CONFIRM -> courseQuotaService.confirm(msg);
            case MqTopics.Tags.QUOTA_RELEASE -> courseQuotaService.release(msg);
            default -> log.warn("未知名额消息 tag：{}", message.getTags());
        }
    }
}
