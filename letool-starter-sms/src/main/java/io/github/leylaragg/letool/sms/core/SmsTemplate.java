package io.github.leylaragg.letool.sms.core;

import io.github.leylaragg.letool.sms.config.SmsProperties;
import io.github.leylaragg.letool.sms.exception.SmsErrorCode;
import io.github.leylaragg.letool.sms.exception.SmsException;
import io.github.leylaragg.letool.sms.model.SmsRequest;
import io.github.leylaragg.letool.sms.model.SmsResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 业务代码发送短信的统一便捷入口。
 *
 * <p>模板负责请求构建、Provider 路由、发送尝试限流和统一异常转换；真实网络调用由
 * {@link SmsProvider} 及厂商官方 SDK 完成。</p>
 */
public class SmsTemplate {

    private static final Logger log = LoggerFactory.getLogger(SmsTemplate.class);

    private final Map<String, SmsProvider> providers;
    private final SmsProperties properties;
    private final SmsRateLimiter rateLimiter;

    /**
     * 创建支持多个 Provider 的短信模板。
     *
     * @param providers 可用短信 Provider
     * @param properties 短信核心配置
     * @param rateLimiter 发送尝试限流器
     */
    public SmsTemplate(
            Collection<SmsProvider> providers,
            SmsProperties properties,
            SmsRateLimiter rateLimiter) {
        this.providers = immutableProviders(providers);
        this.properties = Objects.requireNonNull(properties, "properties 不能为空");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter 不能为空");
    }

    /**
     * 创建兼容单 Provider 使用方式的短信模板。
     *
     * @param provider 短信 Provider
     * @param properties 短信核心配置
     */
    public SmsTemplate(SmsProvider provider, SmsProperties properties) {
        this(
                Collections.singletonList(provider),
                properties,
                properties != null && properties.getRateLimit().isEnabled()
                        ? new LocalSmsRateLimiter(properties.getRateLimit())
                        : SmsRateLimiter.noOp());
    }

    /**
     * 发送完整短信请求。
     *
     * @param request 不可变短信请求
     * @return 结构化发送结果
     */
    public SmsResult send(SmsRequest request) {
        if (request == null) {
            throw SmsException.of(SmsErrorCode.REQUEST_INVALID, "request 不能为空");
        }
        SmsProvider provider = resolveProvider(request.getProvider());
        rateLimiter.check(request);
        try {
            SmsResult result = provider.send(request);
            if (result == null) {
                throw SmsException.of(SmsErrorCode.SEND_FAILED, safeProviderName(provider));
            }
            log.info(
                    "短信发送完成 | provider={} | recipientCount={} | templateCode={} | success={} | requestId={} | code={}",
                    safeProviderName(provider),
                    request.getPhones().size(),
                    request.getTemplateCode(),
                    result.isSuccess(),
                    result.getRequestId(),
                    result.getCode());
            return result;
        } catch (SmsException exception) {
            throw exception;
        } catch (Exception exception) {
            throw SmsException.causedBy(
                    SmsErrorCode.SEND_FAILED,
                    exception,
                    safeProviderName(provider));
        }
    }

    /**
     * 使用默认 Provider 发送单条短信。
     *
     * @param phone 目标手机号
     * @param templateCode 模板编码
     * @param params 模板参数
     * @return 发送结果
     */
    public SmsResult send(String phone, String templateCode, Map<String, String> params) {
        return send(SmsRequest.builder()
                .phone(phone)
                .templateCode(templateCode)
                .parameters(params)
                .build());
    }

    /**
     * 使用默认 Provider 批量发送相同模板短信。
     *
     * @param phones 目标手机号
     * @param templateCode 模板编码
     * @param params 模板参数
     * @return 发送结果
     */
    public SmsResult batchSend(List<String> phones, String templateCode, Map<String, String> params) {
        return send(SmsRequest.builder()
                .phones(phones)
                .templateCode(templateCode)
                .parameters(params)
                .build());
    }

    /**
     * 创建链式短信请求构建器。
     *
     * @return 新构建器
     */
    public Builder builder() {
        return new Builder(this);
    }

    /**
     * 获取已注册 Provider 名称的不可变列表。
     *
     * @return Provider 名称
     */
    public List<String> getProviderNames() {
        return List.copyOf(providers.keySet());
    }

    /**
     * 解析本次请求使用的 Provider。
     *
     * @param requestedProvider 请求指定 Provider
     * @return 已解析 Provider
     */
    private SmsProvider resolveProvider(String requestedProvider) {
        String configuredProvider = properties.getDefaultProvider();
        String selected = requestedProvider;
        if (selected == null || selected.isBlank()) {
            selected = configuredProvider;
        }
        if ((selected == null || selected.isBlank()) && providers.size() == 1) {
            return providers.values().iterator().next();
        }
        if (selected == null || selected.isBlank()) {
            throw SmsException.of(
                    SmsErrorCode.CONFIGURATION_INVALID,
                    "检测到多个 Provider，请配置 letool.sms.default-provider 或在请求中指定 provider");
        }
        SmsProvider provider = providers.get(normalizeProviderName(selected));
        if (provider == null) {
            throw SmsException.of(
                    SmsErrorCode.CONFIGURATION_INVALID,
                    "未找到短信 Provider：" + selected + "，可用 Provider：" + providers.keySet());
        }
        return provider;
    }

    /**
     * 创建 Provider 注册表并检查名称冲突。
     *
     * @param source 原始 Provider 集合
     * @return 不可变 Provider 注册表
     */
    private static Map<String, SmsProvider> immutableProviders(Collection<SmsProvider> source) {
        if (source == null || source.isEmpty()) {
            throw SmsException.of(SmsErrorCode.CONFIGURATION_INVALID, "启用短信模块后至少需要一个 SmsProvider");
        }
        Map<String, SmsProvider> registry = new LinkedHashMap<>();
        for (SmsProvider provider : source) {
            if (provider == null) {
                throw SmsException.of(SmsErrorCode.CONFIGURATION_INVALID, "SmsProvider 集合不能包含 null");
            }
            String name = normalizeProviderName(provider.getProviderName());
            SmsProvider previous = registry.putIfAbsent(name, provider);
            if (previous != null) {
                throw SmsException.of(SmsErrorCode.CONFIGURATION_INVALID, "短信 Provider 名称重复：" + name);
            }
        }
        return Collections.unmodifiableMap(registry);
    }

    /**
     * 标准化 Provider 名称。
     *
     * @param name 原始名称
     * @return 小写 Provider 名称
     */
    private static String normalizeProviderName(String name) {
        if (name == null || name.isBlank()) {
            throw SmsException.of(SmsErrorCode.CONFIGURATION_INVALID, "短信 Provider 名称不能为空");
        }
        return name.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 获取安全 Provider 名称。
     *
     * @param provider Provider 实例
     * @return Provider 名称
     */
    private static String safeProviderName(SmsProvider provider) {
        String name = provider.getProviderName();
        return name == null || name.isBlank() ? "unknown" : name;
    }

    /**
     * 链式短信请求构建器。
     */
    public static final class Builder {

        private final SmsTemplate template;
        private final List<String> phones = new ArrayList<>();
        private final Map<String, String> parameters = new LinkedHashMap<>();
        private String templateCode;
        private String signName;
        private String provider;

        /**
         * 创建链式构建器。
         *
         * @param template 短信模板
         */
        private Builder(SmsTemplate template) {
            this.template = template;
        }

        /**
         * 添加一个目标手机号。
         *
         * @param phone 目标手机号
         * @return 当前构建器
         */
        public Builder to(String phone) {
            phones.add(phone);
            return this;
        }

        /**
         * 添加多个目标手机号。
         *
         * @param phones 目标手机号
         * @return 当前构建器
         */
        public Builder to(Collection<String> phones) {
            if (phones != null) {
                this.phones.addAll(phones);
            }
            return this;
        }

        /**
         * 设置模板编码。
         *
         * @param templateCode 模板编码
         * @return 当前构建器
         */
        public Builder template(String templateCode) {
            this.templateCode = templateCode;
            return this;
        }

        /**
         * 添加或覆盖模板参数。
         *
         * @param name 参数名称
         * @param value 参数值
         * @return 当前构建器
         */
        public Builder param(String name, String value) {
            parameters.put(name, value);
            return this;
        }

        /**
         * 批量添加模板参数。
         *
         * @param params 模板参数
         * @return 当前构建器
         */
        public Builder params(Map<String, String> params) {
            if (params != null) {
                parameters.putAll(params);
            }
            return this;
        }

        /**
         * 设置本次请求使用的短信签名。
         *
         * @param signName 短信签名
         * @return 当前构建器
         */
        public Builder signName(String signName) {
            this.signName = signName;
            return this;
        }

        /**
         * 设置本次请求使用的 Provider。
         *
         * @param provider Provider 名称
         * @return 当前构建器
         */
        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        /**
         * 构建请求并发送。
         *
         * @return 发送结果
         */
        public SmsResult send() {
            SmsRequest request = SmsRequest.builder()
                    .phones(phones)
                    .templateCode(templateCode)
                    .parameters(parameters)
                    .signName(signName)
                    .provider(provider)
                    .build();
            return template.send(request);
        }
    }
}
