package io.github.leylaragg.letool.ratelimiter.core;

import io.github.leylaragg.letool.ratelimiter.config.RateLimiterProperties;
import io.github.leylaragg.letool.ratelimiter.exception.RateLimitException;
import io.github.leylaragg.letool.ratelimiter.exception.RateLimitConfigurationException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RateLimitTemplate} 编程式 API 测试。
 */
class RateLimitTemplateTest {

    /**
     * 未显式指定策略时应使用全局默认策略。
     */
    @Test
    void shouldUseDefaultPolicy() {
        AtomicReference<String> actualPolicy = new AtomicReference<>();
        RateLimiter limiter = (policy, key, permits) -> {
            actualPolicy.set(policy);
            return RateLimitResult.allowed();
        };
        RateLimiterProperties properties = new RateLimiterProperties();
        properties.setDefaultPolicy("default-api");
        properties.setLocalRulesEnabled(false);
        RateLimitTemplate template = new RateLimitTemplate(limiter, properties);

        assertThat(template.tryAcquire("user:1")).isTrue();
        assertThat(actualPolicy).hasValue("default-api");
    }

    /**
     * 指定策略和动态 key 时应完整传递给底层扩展接口。
     */
    @Test
    void shouldDelegateNamedPolicyAndKey() {
        AtomicReference<String> invocation = new AtomicReference<>();
        RateLimiter limiter = (policy, key, permits) -> {
            invocation.set(policy + "|" + key + "|" + permits);
            return RateLimitResult.allowed();
        };
        RateLimiterProperties properties = externalRuleProperties();
        RateLimitTemplate template = new RateLimitTemplate(limiter, properties);

        assertThat(template.tryAcquire("send-sms", "13800138000", 2)).isTrue();
        assertThat(invocation).hasValue("send-sms|13800138000|2");
    }

    /**
     * 被拒绝且没有回退逻辑时应抛出统一业务异常。
     */
    @Test
    void shouldThrowUnifiedExceptionWhenRejected() {
        RateLimiter limiter = (policy, key, permits) ->
                RateLimitResult.rejected("FlowException");
        RateLimitTemplate template = new RateLimitTemplate(limiter, externalRuleProperties());

        assertThatThrownBy(() -> template.executeOrThrow("order", "order:1", () -> "ok"))
                .isInstanceOf(RateLimitException.class)
                .hasMessageContaining("RATE_LIMIT_001");
    }

    /**
     * 被拒绝时应执行调用方提供的回退逻辑。
     */
    @Test
    void shouldExecuteFallbackWhenRejected() {
        RateLimiter limiter = (policy, key, permits) ->
                RateLimitResult.rejected("ParamFlowException");
        RateLimitTemplate template = new RateLimitTemplate(limiter, externalRuleProperties());

        String result = template.executeOrFallback(
                "send-sms", "13800138000", () -> "sent", () -> "busy");

        assertThat(result).isEqualTo("busy");
    }

    /**
     * 本地规则模式下引用不存在的策略应明确失败，避免无规则时默认放行。
     */
    @Test
    void shouldRejectUnknownLocalPolicy() {
        RateLimiter limiter = (policy, key, permits) -> RateLimitResult.allowed();
        RateLimitTemplate template = new RateLimitTemplate(
                limiter, new RateLimiterProperties());

        assertThatThrownBy(() -> template.tryAcquire("missing", null, 1))
                .isInstanceOf(RateLimitConfigurationException.class)
                .hasMessageContaining("policies.missing");
    }

    /**
     * 创建由外部 Sentinel 数据源管理规则的测试配置。
     *
     * @return 外部规则模式配置
     */
    private RateLimiterProperties externalRuleProperties() {
        RateLimiterProperties properties = new RateLimiterProperties();
        properties.setLocalRulesEnabled(false);
        return properties;
    }
}
