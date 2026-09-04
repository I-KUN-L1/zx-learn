package com.zhixing.common.mq;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * RocketMQ 报文体 JSON 序列化/反序列化工具（统一编解码，供生产者与消费者复用）。
 */
public class MessageCodec {

    private final ObjectMapper objectMapper;

    public MessageCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 对象转 JSON 字符串；失败抛 {@link IllegalArgumentException} */
    public String toJson(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalArgumentException("消息序列化失败", e);
        }
    }

    /** JSON 字符串转对象；失败抛 {@link IllegalArgumentException} */
    public <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalArgumentException("消息反序列化失败", e);
        }
    }
}