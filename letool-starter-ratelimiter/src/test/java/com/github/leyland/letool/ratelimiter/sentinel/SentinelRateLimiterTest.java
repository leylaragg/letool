package com.github.leyland.letool.ratelimiter.sentinel;

import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import com.github.leyland.letool.ratelimiter.config.RateLimiterProperties;
import com.github.leyland.letool.ratelimiter.core.RateLimitResult;
import com.github.leyland.letool.ratelimiter.exception.RateLimitConfigurationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sentinel 限流适配器与本地规则注册器测试。
 */
class SentinelRateLimiterTest {

    /**
     * 当前测试创建的规则注册器，用于回收 JVM 级规则所有权。
     */
    private final List<SentinelRuleRegistrar> registrars = new ArrayList<>();

    /**
     * 每个测试后清理 Sentinel 全局规则，避免测试相互影响。
     */
    @AfterEach
    void clearSentinelRules() {
        registrars.forEach(SentinelRuleRegistrar::unregisterRules);
        FlowRuleManager.loadRules(List.of());
        ParamFlowRuleManager.loadRules(List.of());
    }

    /**
     * 无动态 key 时应使用 Sentinel 普通流控规则。
     */
    @Test
    void shouldApplyGlobalFlowRule() {
        RateLimiterProperties properties = propertiesWithPolicy("global-api", 1D);
        SentinelRuleRegistrar registrar = newRegistrar(properties);
        registrar.registerRules();
        SentinelRateLimiter limiter = new SentinelRateLimiter();

        RateLimitResult result = limiter.tryAcquire("global-api", null, 2);

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getBlockReason()).isEqualTo("FlowException");
    }

    /**
     * 存在动态 key 时应使用 Sentinel 热点参数规则。
     */
    @Test
    void shouldApplyHotspotParameterRule() {
        RateLimiterProperties properties = propertiesWithPolicy("send-sms", 1D);
        SentinelRuleRegistrar registrar = newRegistrar(properties);
        registrar.registerRules();
        SentinelRateLimiter limiter = new SentinelRateLimiter();

        RateLimitResult result = limiter.tryAcquire("send-sms", "13800138000", 2);

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getBlockReason()).isEqualTo("ParamFlowException");
    }

    /**
     * 热点参数规则应对不同 key 分别计数。
     */
    @Test
    void shouldIsolateDifferentHotspotKeys() {
        RateLimiterProperties properties = propertiesWithPolicy("send-sms", 1D);
        SentinelRuleRegistrar registrar = newRegistrar(properties);
        registrar.registerRules();
        SentinelRateLimiter limiter = new SentinelRateLimiter();

        RateLimitResult firstUser = limiter.tryAcquire("send-sms", "user-a", 1);
        RateLimitResult repeatedUser = limiter.tryAcquire("send-sms", "user-a", 1);
        RateLimitResult otherUser = limiter.tryAcquire("send-sms", "user-b", 1);

        assertThat(firstUser.isAllowed()).isTrue();
        assertThat(repeatedUser.isAllowed()).isFalse();
        assertThat(otherUser.isAllowed()).isTrue();
    }

    /**
     * 注册本地规则时不应覆盖用户已有的非 Letool Sentinel 规则。
     */
    @Test
    void shouldPreserveExternalSentinelRules() {
        FlowRule externalRule = new FlowRule("external-api");
        externalRule.setCount(5D);
        FlowRuleManager.loadRules(List.of(externalRule));
        SentinelRuleRegistrar registrar =
                newRegistrar(propertiesWithPolicy("default", 10D));

        registrar.registerRules();
        registrar.unregisterRules();

        assertThat(FlowRuleManager.getRules())
                .extracting(FlowRule::getResource)
                .containsExactly("external-api");
    }

    /**
     * 一个上下文注销时不应删除另一个仍存活上下文拥有的规则。
     */
    @Test
    void shouldKeepRulesOwnedByAnotherRegistrar() {
        SentinelRuleRegistrar first =
                newRegistrar(propertiesWithPolicy("first-api", 1D));
        SentinelRuleRegistrar second =
                newRegistrar(propertiesWithPolicy("second-api", 2D));
        first.registerRules();
        second.registerRules();

        first.unregisterRules();

        assertThat(FlowRuleManager.getRules())
                .extracting(FlowRule::getResource)
                .containsExactly("letool:rate-limit:second-api:global");
    }

    /**
     * 两个上下文声明同名但不同阈值的策略时应拒绝静默覆盖。
     */
    @Test
    void shouldRejectConflictingPoliciesAcrossRegistrars() {
        SentinelRuleRegistrar first =
                newRegistrar(propertiesWithPolicy("shared-api", 1D));
        SentinelRuleRegistrar second =
                newRegistrar(propertiesWithPolicy("shared-api", 2D));
        first.registerRules();

        assertThatThrownBy(second::registerRules)
                .isInstanceOf(RateLimitConfigurationException.class)
                .hasMessageContaining("RATE_LIMIT_002");
    }

    /**
     * 创建并记录测试规则注册器。
     *
     * @param properties 限流配置
     * @return 测试规则注册器
     */
    private SentinelRuleRegistrar newRegistrar(RateLimiterProperties properties) {
        SentinelRuleRegistrar registrar = new SentinelRuleRegistrar(properties);
        registrars.add(registrar);
        return registrar;
    }

    /**
     * 创建只包含单个策略的测试配置。
     *
     * @param policyName 策略名称
     * @param threshold  QPS 阈值
     * @return 限流配置
     */
    private RateLimiterProperties propertiesWithPolicy(String policyName, double threshold) {
        RateLimiterProperties properties = new RateLimiterProperties();
        properties.getPolicies().clear();
        properties.setDefaultPolicy(policyName);
        RateLimiterProperties.Policy policy = new RateLimiterProperties.Policy();
        policy.setThreshold(threshold);
        properties.getPolicies().put(policyName, policy);
        return properties;
    }
}
