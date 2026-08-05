package com.github.leyland.letool.mq.core;

import com.github.leyland.letool.mq.exception.MqErrorCode;
import com.github.leyland.letool.mq.exception.MqException;
import com.github.leyland.letool.mq.model.MqMessage;
import com.github.leyland.letool.mq.model.MqSendRequest;
import com.github.leyland.letool.mq.model.MqSendResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Provider 中立的 MQ 发送便利门面。
 *
 * <p>门面在构造阶段完成 Provider 注册和默认项校验，避免把路由歧义推迟到首条业务消息发送时。
 * 单 Provider 可以自动成为默认项；多 Provider 必须明确配置默认项或在请求中显式选择。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public final class MqTemplate {

    private final Map<String, MqProvider> providers;
    private final String defaultProvider;

    /**
     * 创建 MQ 发送门面。
     *
     * @param providers 已注册的 Provider 列表
     * @param defaultProvider 可选默认 Provider 名称
     */
    public MqTemplate(List<MqProvider> providers, String defaultProvider) {
        this.providers = immutableProviders(providers);
        this.defaultProvider = resolveDefaultProvider(this.providers, defaultProvider);
    }

    /**
     * 使用默认 Provider 发送普通消息。
     *
     * @param bindingName Spring Cloud Stream 输出 Binding 名称
     * @param payload 非空消息正文
     * @return 结构化发送结果
     */
    public MqSendResult send(String bindingName, Object payload) {
        return send(new MqSendRequest<>(
                null,
                bindingName,
                new MqMessage<>(payload, null, null)));
    }

    /**
     * 使用显式 Provider 发送普通消息。
     *
     * @param provider Provider 名称
     * @param bindingName Spring Cloud Stream 输出 Binding 名称
     * @param payload 非空消息正文
     * @return 结构化发送结果
     */
    public MqSendResult send(String provider, String bindingName, Object payload) {
        return send(new MqSendRequest<>(
                provider,
                bindingName,
                new MqMessage<>(payload, null, null)));
    }

    /**
     * 发送完整 MQ 请求。
     *
     * @param request 非空发送请求
     * @return 结构化发送结果
     */
    public MqSendResult send(MqSendRequest<?> request) {
        if (request == null) {
            throw MqException.of(MqErrorCode.MESSAGE_INVALID, "request 不能为空");
        }
        String providerName = request.provider() == null ? defaultProvider : request.provider();
        MqProvider provider = providers.get(providerName);
        if (provider == null) {
            throw MqException.of(MqErrorCode.PROVIDER_NOT_FOUND, providerName);
        }
        try {
            MqSendResult result = provider.send(request);
            if (result == null) {
                throw MqException.of(MqErrorCode.PROVIDER_EXECUTION_FAILED, providerName);
            }
            return result;
        } catch (MqException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw MqException.causedBy(
                    MqErrorCode.PROVIDER_EXECUTION_FAILED,
                    exception,
                    providerName);
        }
    }

    /**
     * 构建不可变 Provider 映射。
     *
     * @param sourceProviders 原始 Provider 列表
     * @return 按注册顺序保存的不可变 Provider 映射
     */
    private static Map<String, MqProvider> immutableProviders(List<MqProvider> sourceProviders) {
        if (sourceProviders == null || sourceProviders.isEmpty()) {
            throw MqException.of(MqErrorCode.CONFIGURATION_INVALID, "未注册任何 MqProvider");
        }
        Map<String, MqProvider> providerMap = new LinkedHashMap<>();
        for (MqProvider provider : sourceProviders) {
            if (provider == null) {
                throw MqException.of(MqErrorCode.CONFIGURATION_INVALID, "MqProvider 不能为空");
            }
            String name = normalizeRequiredName(provider.name());
            if (providerMap.putIfAbsent(name, provider) != null) {
                throw MqException.of(MqErrorCode.DUPLICATE_PROVIDER, name);
            }
        }
        return Map.copyOf(providerMap);
    }

    /**
     * 确定最终默认 Provider。
     *
     * @param providerMap 已校验的 Provider 映射
     * @param configuredDefault 配置的默认 Provider
     * @return 可直接用于路由的默认 Provider 名称
     */
    private static String resolveDefaultProvider(
            Map<String, MqProvider> providerMap,
            String configuredDefault) {
        String normalizedDefault = normalizeOptionalName(configuredDefault);
        if (normalizedDefault != null) {
            if (!providerMap.containsKey(normalizedDefault)) {
                throw MqException.of(MqErrorCode.CONFIGURATION_INVALID,
                        "默认 Provider 未注册：" + normalizedDefault);
            }
            return normalizedDefault;
        }
        if (providerMap.size() == 1) {
            return providerMap.keySet().iterator().next();
        }
        throw MqException.of(MqErrorCode.CONFIGURATION_INVALID,
                "存在多个 MqProvider 时必须配置 default-provider");
    }

    /**
     * 规范化必填 Provider 名称。
     *
     * @param value 原始 Provider 名称
     * @return 小写 Provider 名称
     */
    private static String normalizeRequiredName(String value) {
        String normalized = normalizeOptionalName(value);
        if (normalized == null) {
            throw MqException.of(MqErrorCode.CONFIGURATION_INVALID, "Provider 名称不能为空");
        }
        return normalized;
    }

    /**
     * 规范化可选 Provider 名称。
     *
     * @param value 原始 Provider 名称
     * @return 小写 Provider 名称；空白值返回 {@code null}
     */
    private static String normalizeOptionalName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
