package io.github.leylaragg.letool.ratelimiter.core;

import io.github.leylaragg.letool.ratelimiter.config.RateLimiterProperties;
import io.github.leylaragg.letool.ratelimiter.exception.RateLimitConfigurationException;
import io.github.leylaragg.letool.ratelimiter.exception.RateLimitException;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 面向业务代码的编程式限流模板。
 *
 * <p>该模板隐藏 Sentinel 资源命名与阻断异常等实现细节，同时保留
 * {@link RateLimiter} 扩展接口，便于业务项目替换底层实现。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public class RateLimitTemplate {

    /**
     * 底层限流器。
     */
    private final RateLimiter rateLimiter;

    /**
     * 默认策略名称。
     */
    private final String defaultPolicy;

    /**
     * 本地规则模式下允许使用的策略名称；外部规则模式下为空集合。
     */
    private final Set<String> localPolicies;

    /**
     * 创建限流模板。
     *
     * @param rateLimiter 底层限流器
     * @param properties  限流配置
     */
    public RateLimitTemplate(RateLimiter rateLimiter, RateLimiterProperties properties) {
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter must not be null");
        Objects.requireNonNull(properties, "properties must not be null");
        this.defaultPolicy = requirePolicy(properties.getDefaultPolicy());
        this.localPolicies = properties.isLocalRulesEnabled()
                ? copyLocalPolicies(properties)
                : Set.of();
    }

    /**
     * 使用默认策略和动态 key 尝试获取一个许可。
     *
     * @param key 动态业务 key；为空时执行默认策略的全局限流
     * @return 允许通过时返回 {@code true}
     */
    public boolean tryAcquire(String key) {
        return tryAcquire(defaultPolicy, key, 1);
    }

    /**
     * 使用默认策略和动态 key 尝试获取指定数量的许可。
     *
     * @param key     动态业务 key；为空时执行默认策略的全局限流
     * @param permits 许可数量
     * @return 允许通过时返回 {@code true}
     */
    public boolean tryAcquire(String key, int permits) {
        return tryAcquire(defaultPolicy, key, permits);
    }

    /**
     * 使用命名策略尝试获取指定数量的许可。
     *
     * @param policy  策略名称；为空白时使用默认策略
     * @param key     动态业务 key；为空时执行全局限流
     * @param permits 许可数量
     * @return 允许通过时返回 {@code true}
     */
    public boolean tryAcquire(String policy, String key, int permits) {
        return tryAcquireWithResult(policy, key, permits).isAllowed();
    }

    /**
     * 使用默认策略获取详细限流结果。
     *
     * @param key     动态业务 key；为空时执行全局限流
     * @param permits 许可数量
     * @return 详细限流结果
     */
    public RateLimitResult tryAcquireWithResult(String key, int permits) {
        return tryAcquireWithResult(defaultPolicy, key, permits);
    }

    /**
     * 使用命名策略获取详细限流结果。
     *
     * @param policy  策略名称；为空白时使用默认策略
     * @param key     动态业务 key；为空时执行全局限流
     * @param permits 许可数量
     * @return 详细限流结果
     */
    public RateLimitResult tryAcquireWithResult(String policy, String key, int permits) {
        String actualPolicy = resolvePolicy(policy);
        return rateLimiter.tryAcquire(actualPolicy, key, permits);
    }

    /**
     * 使用默认策略执行带回退逻辑的操作。
     *
     * @param key      动态业务 key
     * @param action   正常业务逻辑
     * @param fallback 限流回退逻辑
     * @param <T>      返回值类型
     * @return 正常结果或回退结果
     */
    public <T> T executeOrFallback(String key, Supplier<T> action, Supplier<T> fallback) {
        return executeOrFallback(defaultPolicy, key, 1, action, fallback);
    }

    /**
     * 使用命名策略执行带回退逻辑的操作。
     *
     * @param policy   策略名称
     * @param key      动态业务 key
     * @param action   正常业务逻辑
     * @param fallback 限流回退逻辑
     * @param <T>      返回值类型
     * @return 正常结果或回退结果
     */
    public <T> T executeOrFallback(String policy,
                                   String key,
                                   Supplier<T> action,
                                   Supplier<T> fallback) {
        return executeOrFallback(policy, key, 1, action, fallback);
    }

    /**
     * 使用命名策略和指定许可数执行带回退逻辑的操作。
     *
     * @param policy   策略名称
     * @param key      动态业务 key
     * @param permits  许可数量
     * @param action   正常业务逻辑
     * @param fallback 限流回退逻辑
     * @param <T>      返回值类型
     * @return 正常结果或回退结果
     */
    public <T> T executeOrFallback(String policy,
                                   String key,
                                   int permits,
                                   Supplier<T> action,
                                   Supplier<T> fallback) {
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(fallback, "fallback must not be null");
        if (tryAcquire(policy, key, permits)) {
            return action.get();
        }
        return fallback.get();
    }

    /**
     * 使用默认策略执行操作，被拒绝时抛出统一异常。
     *
     * @param key      动态业务 key
     * @param supplier 正常业务逻辑
     * @param <T>      返回值类型
     * @return 正常业务结果
     * @throws RateLimitException 请求被拒绝时抛出
     */
    public <T> T executeOrThrow(String key, Supplier<T> supplier) {
        return executeOrThrow(defaultPolicy, key, 1, supplier);
    }

    /**
     * 使用命名策略执行操作，被拒绝时抛出统一异常。
     *
     * @param policy   策略名称
     * @param key      动态业务 key
     * @param supplier 正常业务逻辑
     * @param <T>      返回值类型
     * @return 正常业务结果
     * @throws RateLimitException 请求被拒绝时抛出
     */
    public <T> T executeOrThrow(String policy, String key, Supplier<T> supplier) {
        return executeOrThrow(policy, key, 1, supplier);
    }

    /**
     * 使用命名策略和指定许可数执行操作，被拒绝时抛出统一异常。
     *
     * @param policy   策略名称
     * @param key      动态业务 key
     * @param permits  许可数量
     * @param supplier 正常业务逻辑
     * @param <T>      返回值类型
     * @return 正常业务结果
     * @throws RateLimitException 请求被拒绝时抛出
     */
    public <T> T executeOrThrow(String policy,
                                String key,
                                int permits,
                                Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        String actualPolicy = resolvePolicy(policy);
        if (!tryAcquire(actualPolicy, key, permits)) {
            throw RateLimitException.rejected(actualPolicy);
        }
        return supplier.get();
    }

    /**
     * 创建链式限流构建器。
     *
     * @return 新的限流构建器
     */
    public Builder builder() {
        return new Builder();
    }

    /**
     * 获取当前默认策略名称。
     *
     * @return 默认策略名称
     */
    public String getDefaultPolicy() {
        return defaultPolicy;
    }

    /**
     * 解析实际使用的策略名称。
     *
     * @param policy 调用方指定的策略名称
     * @return 非空策略名称
     */
    private String resolvePolicy(String policy) {
        String actualPolicy = policy == null || policy.isBlank()
                ? defaultPolicy
                : requirePolicy(policy);
        if (!localPolicies.isEmpty() && !localPolicies.contains(actualPolicy)) {
            throw RateLimitConfigurationException.invalid(
                    "policies." + actualPolicy);
        }
        return actualPolicy;
    }

    /**
     * 校验策略名称。
     *
     * @param policy 策略名称
     * @return 已校验策略名称
     */
    private String requirePolicy(String policy) {
        if (policy == null || policy.isBlank()) {
            throw RateLimitConfigurationException.invalid("default-policy");
        }
        return policy;
    }

    /**
     * 复制本地策略名称并校验默认策略。
     *
     * @param properties 限流配置
     * @return 不可变策略名称集合
     */
    private Set<String> copyLocalPolicies(RateLimiterProperties properties) {
        if (properties.getPolicies() == null
                || !properties.getPolicies().containsKey(defaultPolicy)) {
            throw RateLimitConfigurationException.invalid("default-policy");
        }
        Set<String> policies = new LinkedHashSet<>();
        for (String policy : properties.getPolicies().keySet()) {
            if (policy == null || policy.isBlank()) {
                throw RateLimitConfigurationException.invalid("policies");
            }
            policies.add(policy);
        }
        return Set.copyOf(policies);
    }

    /**
     * 限流操作链式构建器。
     */
    public final class Builder {

        /**
         * 策略名称，默认使用全局默认策略。
         */
        private String policy = defaultPolicy;

        /**
         * 动态业务 key。
         */
        private String key;

        /**
         * 本次请求消耗的许可数。
         */
        private int permits = 1;

        /**
         * 设置策略名称。
         *
         * @param policy 策略名称
         * @return 当前构建器
         */
        public Builder policy(String policy) {
            this.policy = policy;
            return this;
        }

        /**
         * 设置动态业务 key。
         *
         * @param key 动态业务 key
         * @return 当前构建器
         */
        public Builder key(String key) {
            this.key = key;
            return this;
        }

        /**
         * 设置本次请求消耗的许可数。
         *
         * @param permits 许可数量
         * @return 当前构建器
         */
        public Builder permits(int permits) {
            this.permits = permits;
            return this;
        }

        /**
         * 尝试获取许可。
         *
         * @return 允许通过时返回 {@code true}
         */
        public boolean tryAcquire() {
            return RateLimitTemplate.this.tryAcquire(policy, key, permits);
        }

        /**
         * 执行带回退逻辑的操作。
         *
         * @param action   正常业务逻辑
         * @param fallback 限流回退逻辑
         * @param <T>      返回值类型
         * @return 正常结果或回退结果
         */
        public <T> T executeOrFallback(Supplier<T> action, Supplier<T> fallback) {
            return RateLimitTemplate.this.executeOrFallback(
                    policy, key, permits, action, fallback);
        }

        /**
         * 执行业务操作，被拒绝时抛出统一异常。
         *
         * @param supplier 正常业务逻辑
         * @param <T>      返回值类型
         * @return 正常业务结果
         */
        public <T> T executeOrThrow(Supplier<T> supplier) {
            return RateLimitTemplate.this.executeOrThrow(
                    policy, key, permits, supplier);
        }
    }
}
