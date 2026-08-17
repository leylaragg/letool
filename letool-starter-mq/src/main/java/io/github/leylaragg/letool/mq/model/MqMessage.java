package io.github.leylaragg.letool.mq.model;

import io.github.leylaragg.letool.mq.exception.MqErrorCode;
import io.github.leylaragg.letool.mq.exception.MqException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provider 中立的不可变 MQ 消息信封。
 *
 * <p>消息正文保留原始 Java 类型，序列化和内容协商由 Spring Cloud Stream 及具体 Binder 负责。
 * Header 在构造时完成防御性复制，并禁止覆盖 Spring Messaging 的只读标识字段。</p>
 *
 * @param payload 非空消息正文
 * @param headers 业务 Header；传 {@code null} 表示没有 Header
 * @param contentType 可选 MIME 类型；不使用时传 {@code null}
 * @param <T> 消息正文类型
 * @author leyland
 * @since 2.0.0
 */
public record MqMessage<T>(T payload, Map<String, Object> headers, String contentType) {

    /** Spring Messaging 自动生成的消息标识 Header。 */
    private static final String ID_HEADER = "id";

    /** Spring Messaging 自动生成的消息时间戳 Header。 */
    private static final String TIMESTAMP_HEADER = "timestamp";

    /**
     * 校验消息并冻结 Header。
     *
     * @param payload 非空消息正文
     * @param headers 业务 Header；传 {@code null} 表示没有 Header
     * @param contentType 可选 MIME 类型；不使用时传 {@code null}
     */
    public MqMessage {
        if (payload == null) {
            throw MqException.of(MqErrorCode.MESSAGE_INVALID, "payload 不能为空");
        }
        if (contentType != null) {
            contentType = contentType.trim();
            if (contentType.isEmpty()) {
                throw MqException.of(MqErrorCode.MESSAGE_INVALID, "contentType 不能为空白字符串");
            }
        }
        headers = immutableHeaders(headers);
    }

    /**
     * 复制并校验业务 Header。
     *
     * @param sourceHeaders 调用方提供的 Header
     * @return 保持插入顺序的不可变 Header
     */
    private static Map<String, Object> immutableHeaders(Map<String, Object> sourceHeaders) {
        if (sourceHeaders == null || sourceHeaders.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copiedHeaders = new LinkedHashMap<>();
        sourceHeaders.forEach((name, value) -> {
            if (name == null || name.isBlank()) {
                throw MqException.of(MqErrorCode.HEADER_INVALID, "Header 名称不能为空");
            }
            String normalizedName = name.trim();
            if (ID_HEADER.equalsIgnoreCase(normalizedName)
                    || TIMESTAMP_HEADER.equalsIgnoreCase(normalizedName)) {
                throw MqException.of(MqErrorCode.HEADER_INVALID,
                        normalizedName + " 是框架只读 Header");
            }
            if (value == null) {
                throw MqException.of(MqErrorCode.HEADER_INVALID,
                        normalizedName + " 的值不能为空");
            }
            copiedHeaders.put(normalizedName, value);
        });
        return Collections.unmodifiableMap(copiedHeaders);
    }

    /**
     * 返回不包含消息正文和 Header 值的诊断信息。
     *
     * @return 安全的消息摘要
     */
    @Override
    public String toString() {
        return "MqMessage{" +
                "payloadType=" + payload.getClass().getName() +
                ", headerNames=" + headers.keySet() +
                ", contentType='" + contentType + '\'' +
                '}';
    }
}
