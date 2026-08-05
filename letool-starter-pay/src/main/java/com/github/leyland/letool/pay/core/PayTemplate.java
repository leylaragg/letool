package com.github.leyland.letool.pay.core;

import com.github.leyland.letool.pay.config.PayProperties;
import com.github.leyland.letool.pay.exception.PayErrorCode;
import com.github.leyland.letool.pay.exception.PayException;
import com.github.leyland.letool.pay.model.PayCloseRequest;
import com.github.leyland.letool.pay.model.PayNotification;
import com.github.leyland.letool.pay.model.PayNotificationRequest;
import com.github.leyland.letool.pay.model.PayQueryRequest;
import com.github.leyland.letool.pay.model.PayRequest;
import com.github.leyland.letool.pay.model.PayResponse;
import com.github.leyland.letool.pay.model.RefundQueryRequest;
import com.github.leyland.letool.pay.model.RefundRequest;
import com.github.leyland.letool.pay.model.RefundResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 屏蔽支付平台差异的统一便捷入口。
 *
 * <p>模板只负责 Provider 注册、确定性路由和标准模型转发，不对资金操作自动重试，
 * 也不替业务方持久化订单或处理事务幂等。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public final class PayTemplate {

    private static final Logger log = LoggerFactory.getLogger(PayTemplate.class);

    private final Map<String, PayProvider> providers;
    private final String defaultProvider;

    /**
     * 创建支付模板并校验 Provider 注册表。
     *
     * @param providers 容器中的全部支付 Provider
     * @param properties 支付核心配置
     */
    public PayTemplate(List<PayProvider> providers, PayProperties properties) {
        if (providers == null || providers.isEmpty()) {
            throw PayException.of(PayErrorCode.CONFIGURATION_INVALID, "未注册任何 PayProvider");
        }
        if (properties == null) {
            throw PayException.of(PayErrorCode.CONFIGURATION_INVALID, "PayProperties 不能为空");
        }

        Map<String, PayProvider> registry = new LinkedHashMap<>();
        for (PayProvider provider : providers) {
            if (provider == null) {
                throw PayException.of(PayErrorCode.CONFIGURATION_INVALID, "PayProvider 列表不能包含 null");
            }
            String name = normalizeRequiredProvider(provider.getProviderName());
            if (registry.putIfAbsent(name, provider) != null) {
                throw PayException.of(PayErrorCode.DUPLICATE_PROVIDER, name);
            }
        }
        String configuredDefault = normalizeOptionalProvider(properties.getDefaultProvider());
        if (registry.size() > 1 && configuredDefault == null) {
            throw PayException.of(PayErrorCode.CONFIGURATION_INVALID,
                    "注册多个 PayProvider 时必须配置 letool.pay.default-provider");
        }
        if (configuredDefault != null && !registry.containsKey(configuredDefault)) {
            throw PayException.of(PayErrorCode.PROVIDER_NOT_FOUND, configuredDefault);
        }
        this.providers = Collections.unmodifiableMap(registry);
        this.defaultProvider = configuredDefault;
        log.info("支付模板已加载 Provider：{}，默认 Provider：{}", registry.keySet(), defaultProvider);
    }

    /**
     * 创建支付订单。
     *
     * @param request 支付请求
     * @return 标准化支付响应
     */
    public PayResponse create(PayRequest request) {
        requireRequest(request, "支付请求");
        return provider(request.getProvider()).create(request);
    }

    /**
     * 查询支付订单。
     *
     * @param request 查询请求
     * @return 标准化支付响应
     */
    public PayResponse query(PayQueryRequest request) {
        requireRequest(request, "支付查询请求");
        return provider(request.getProvider()).query(request);
    }

    /**
     * 关闭支付订单。
     *
     * @param request 关闭请求
     * @return 标准化支付响应
     */
    public PayResponse close(PayCloseRequest request) {
        requireRequest(request, "支付关单请求");
        return provider(request.getProvider()).close(request);
    }

    /**
     * 发起退款。
     *
     * @param request 退款请求
     * @return 标准化退款响应
     */
    public RefundResponse refund(RefundRequest request) {
        requireRequest(request, "退款请求");
        return provider(request.getProvider()).refund(request);
    }

    /**
     * 查询退款。
     *
     * @param request 退款查询请求
     * @return 标准化退款响应
     */
    public RefundResponse queryRefund(RefundQueryRequest request) {
        requireRequest(request, "退款查询请求");
        return provider(request.getProvider()).queryRefund(request);
    }

    /**
     * 验签并解析支付平台通知。
     *
     * @param request 原始通知请求
     * @return 标准化支付通知
     */
    public PayNotification parseNotification(PayNotificationRequest request) {
        requireRequest(request, "支付通知请求");
        return provider(request.getProvider()).parseNotification(request);
    }

    /**
     * 获取已注册 Provider 名称。
     *
     * @return 不可变 Provider 名称集合
     */
    public Set<String> getProviderNames() {
        return providers.keySet();
    }

    private PayProvider provider(String requestedProvider) {
        String name = normalizeOptionalProvider(requestedProvider);
        if (name == null) {
            name = defaultProvider;
        }
        if (name == null) {
            throw PayException.of(PayErrorCode.CONFIGURATION_INVALID,
                    "请求未指定 Provider，且 letool.pay.default-provider 未配置");
        }
        PayProvider provider = providers.get(name);
        if (provider == null) {
            throw PayException.of(PayErrorCode.PROVIDER_NOT_FOUND, name);
        }
        return provider;
    }

    private String normalizeRequiredProvider(String provider) {
        String normalized = normalizeOptionalProvider(provider);
        if (normalized == null) {
            throw PayException.of(PayErrorCode.CONFIGURATION_INVALID, "Provider 名称不能为空");
        }
        return normalized;
    }

    private String normalizeOptionalProvider(String provider) {
        return provider == null || provider.isBlank()
                ? null : provider.trim().toLowerCase(Locale.ROOT);
    }

    private void requireRequest(Object request, String requestName) {
        if (request == null) {
            throw PayException.of(PayErrorCode.REQUEST_INVALID, requestName + "不能为空");
        }
    }
}
