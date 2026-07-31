package com.github.leyland.letool.ratelimiter.config;

import com.alibaba.csp.sentinel.SphU;
import com.github.leyland.letool.ratelimiter.aspect.RateLimitAspect;
import com.github.leyland.letool.ratelimiter.core.RateLimitTemplate;
import com.github.leyland.letool.ratelimiter.core.RateLimiter;
import com.github.leyland.letool.ratelimiter.sentinel.SentinelRateLimiter;
import com.github.leyland.letool.ratelimiter.sentinel.SentinelRuleRegistrar;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Letool Sentinel 限流自动配置。
 *
 * <p>自动配置提供 Sentinel Core 薄适配、可选本地静态规则和声明式限流切面。
 * 所有核心 Bean 均支持业务项目通过同类型 Bean 覆盖。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
@AutoConfiguration
@ConditionalOnClass(SphU.class)
@EnableConfigurationProperties(RateLimiterProperties.class)
@ConditionalOnProperty(
        prefix = "letool.rate-limiter",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RateLimiterAutoConfiguration {

    /**
     * 注册 Letool 本地 Sentinel 规则。
     *
     * @param properties 限流配置
     * @return 本地规则注册器
     */
    @Bean(initMethod = "registerRules", destroyMethod = "unregisterRules")
    @ConditionalOnMissingBean(SentinelRuleRegistrar.class)
    @ConditionalOnProperty(
            prefix = "letool.rate-limiter",
            name = "local-rules-enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public SentinelRuleRegistrar sentinelRuleRegistrar(RateLimiterProperties properties) {
        return new SentinelRuleRegistrar(properties);
    }

    /**
     * 注册默认 Sentinel 限流器。
     *
     * @return Sentinel 限流器
     */
    @Bean
    @ConditionalOnMissingBean(RateLimiter.class)
    public SentinelRateLimiter sentinelRateLimiter() {
        return new SentinelRateLimiter();
    }

    /**
     * 注册编程式限流模板。
     *
     * @param rateLimiter 底层限流器
     * @param properties  限流配置
     * @return 编程式限流模板
     */
    @Bean
    @ConditionalOnMissingBean(RateLimitTemplate.class)
    public RateLimitTemplate rateLimitTemplate(RateLimiter rateLimiter,
                                                RateLimiterProperties properties) {
        return new RateLimitTemplate(rateLimiter, properties);
    }

    /**
     * 注册 {@code @RateLimit} 声明式限流切面。
     *
     * @param rateLimitTemplate 编程式限流模板
     * @return 声明式限流切面
     */
    @Bean
    @ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
    @ConditionalOnMissingBean(RateLimitAspect.class)
    @ConditionalOnProperty(
            prefix = "letool.rate-limiter.annotation",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public RateLimitAspect rateLimitAspect(RateLimitTemplate rateLimitTemplate) {
        return new RateLimitAspect(rateLimitTemplate);
    }
}
