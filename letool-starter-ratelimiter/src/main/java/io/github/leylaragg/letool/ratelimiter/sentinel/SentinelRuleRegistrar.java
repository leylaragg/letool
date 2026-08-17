package io.github.leylaragg.letool.ratelimiter.sentinel;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowRuleManager;
import io.github.leylaragg.letool.ratelimiter.config.RateLimiterProperties;
import io.github.leylaragg.letool.ratelimiter.exception.RateLimitConfigurationException;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 Letool 命名策略注册为 Sentinel 本地静态规则。
 *
 * <p>注册与注销都会保留非 Letool 命名空间中的规则，避免覆盖业务项目自行注册的
 * Sentinel 资源。动态数据源场景应关闭本地规则，由外部系统统一管理规则生命周期。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public final class SentinelRuleRegistrar {

    /**
     * 保护 Sentinel 全局规则集合的进程内锁。
     */
    private static final Object RULE_MONITOR = new Object();

    /**
     * JVM 内活跃注册器及其不可变规则快照。
     *
     * <p>Sentinel Core 的规则管理器是 JVM 全局单例，因此这里必须按注册器实例维护所有权。
     * 一个 Spring 上下文关闭时，只移除该上下文拥有的规则。</p>
     */
    private static final Map<SentinelRuleRegistrar, Map<String, Double>> ACTIVE_REGISTRARS =
            new IdentityHashMap<>();

    /**
     * Letool 限流配置。
     */
    private final RateLimiterProperties properties;

    /**
     * 创建本地规则注册器。
     *
     * @param properties Letool 限流配置
     */
    public SentinelRuleRegistrar(RateLimiterProperties properties) {
        this.properties = properties;
    }

    /**
     * 注册配置中的普通流控规则与热点参数规则。
     */
    public void registerRules() {
        Map<String, Double> localPolicies = snapshotLocalPolicies();
        synchronized (RULE_MONITOR) {
            validateNoConflicts(localPolicies);
            ACTIVE_REGISTRARS.put(this, localPolicies);
            reloadOwnedRules();
        }
    }

    /**
     * 注销 Letool 本地规则，并保留注册期间新增的外部规则。
     */
    public void unregisterRules() {
        synchronized (RULE_MONITOR) {
            ACTIVE_REGISTRARS.remove(this);
            reloadOwnedRules();
        }
    }

    /**
     * 合并所有活跃注册器的规则，并保留非 Letool 命名空间中的外部规则。
     */
    private static void reloadOwnedRules() {
        List<FlowRule> flowRules = externalFlowRules();
        List<ParamFlowRule> paramFlowRules = externalParamFlowRules();
        Map<String, Double> mergedPolicies = new LinkedHashMap<>();
        ACTIVE_REGISTRARS.values().forEach(
                policies -> policies.forEach(mergedPolicies::putIfAbsent));
        mergedPolicies.forEach((name, threshold) -> {
            flowRules.add(createFlowRule(name, threshold));
            paramFlowRules.add(createParamFlowRule(name, threshold));
        });
        FlowRuleManager.loadRules(flowRules);
        ParamFlowRuleManager.loadRules(paramFlowRules);
    }

    /**
     * 获取当前非 Letool 普通流控规则。
     *
     * @return 可变的外部规则副本
     */
    private static List<FlowRule> externalFlowRules() {
        List<FlowRule> rules = new ArrayList<>();
        for (FlowRule rule : FlowRuleManager.getRules()) {
            if (!SentinelResourceNames.isLetoolResource(rule.getResource())) {
                rules.add(rule);
            }
        }
        return rules;
    }

    /**
     * 获取当前非 Letool 热点参数规则。
     *
     * @return 可变的外部规则副本
     */
    private static List<ParamFlowRule> externalParamFlowRules() {
        List<ParamFlowRule> rules = new ArrayList<>();
        for (ParamFlowRule rule : ParamFlowRuleManager.getRules()) {
            if (!SentinelResourceNames.isLetoolResource(rule.getResource())) {
                rules.add(rule);
            }
        }
        return rules;
    }

    /**
     * 创建普通 QPS 流控规则。
     *
     * @param name      策略名称
     * @param threshold 每秒许可阈值
     * @return Sentinel 普通流控规则
     */
    private static FlowRule createFlowRule(String name, double threshold) {
        FlowRule rule = new FlowRule(SentinelResourceNames.global(name));
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(threshold);
        return rule;
    }

    /**
     * 创建按第 0 个参数统计的热点规则。
     *
     * @param name      策略名称
     * @param threshold 每秒许可阈值
     * @return Sentinel 热点参数规则
     */
    private static ParamFlowRule createParamFlowRule(String name, double threshold) {
        return new ParamFlowRule(SentinelResourceNames.keyed(name))
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setParamIdx(0)
                .setCount(threshold);
    }

    /**
     * 校验配置并创建当前注册器的不可变规则快照。
     *
     * @return 策略名称与阈值快照
     */
    private Map<String, Double> snapshotLocalPolicies() {
        Map<String, RateLimiterProperties.Policy> policies = properties.getPolicies();
        if (policies == null || policies.isEmpty()) {
            throw RateLimitConfigurationException.invalid("policies");
        }
        String defaultPolicy = properties.getDefaultPolicy();
        if (defaultPolicy == null || defaultPolicy.isBlank()) {
            throw RateLimitConfigurationException.invalid("default-policy");
        }
        if (!policies.containsKey(defaultPolicy)) {
            throw RateLimitConfigurationException.invalid("default-policy");
        }
        Map<String, Double> snapshot = new LinkedHashMap<>();
        policies.forEach((name, policy) -> {
            validatePolicy(name, policy);
            snapshot.put(name, policy.getThreshold());
        });
        return Map.copyOf(snapshot);
    }

    /**
     * 校验当前快照不会与其他活跃上下文的同名规则冲突。
     *
     * @param localPolicies 当前注册器的规则快照
     */
    private void validateNoConflicts(Map<String, Double> localPolicies) {
        for (Map.Entry<SentinelRuleRegistrar, Map<String, Double>> owner
                : ACTIVE_REGISTRARS.entrySet()) {
            if (owner.getKey() == this) {
                continue;
            }
            for (Map.Entry<String, Double> policy : localPolicies.entrySet()) {
                Double registeredThreshold = owner.getValue().get(policy.getKey());
                if (registeredThreshold != null
                        && Double.compare(registeredThreshold, policy.getValue()) != 0) {
                    throw RateLimitConfigurationException.invalid(
                            "policies." + policy.getKey());
                }
            }
        }
    }

    /**
     * 校验单个命名策略。
     *
     * @param name   策略名称
     * @param policy 策略配置
     */
    private void validatePolicy(String name, RateLimiterProperties.Policy policy) {
        if (name == null || name.isBlank()) {
            throw RateLimitConfigurationException.invalid("policies");
        }
        if (policy == null
                || !Double.isFinite(policy.getThreshold())
                || policy.getThreshold() <= 0D) {
            throw RateLimitConfigurationException.invalid(
                    "policies." + name + ".threshold");
        }
    }
}
