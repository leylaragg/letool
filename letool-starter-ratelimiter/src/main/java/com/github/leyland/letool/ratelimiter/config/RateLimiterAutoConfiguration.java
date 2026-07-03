package com.github.leyland.letool.ratelimiter.config;

import com.github.leyland.letool.ratelimiter.algorithm.SlidingWindowLimiter;
import com.github.leyland.letool.ratelimiter.algorithm.TokenBucketLimiter;
import com.github.leyland.letool.ratelimiter.aspect.RateLimitAspect;
import com.github.leyland.letool.ratelimiter.circuit.DefaultCircuitBreaker;
import com.github.leyland.letool.ratelimiter.core.RateLimitTemplate;
import com.github.leyland.letool.ratelimiter.core.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * letool-starter-ratelimiter 模块的 Spring Boot 自动配置类。
 *
 * <p>负责按条件装配限流和熔断相关的所有 Bean，装配链路如下：</p>
 *
 * <h3>限流链路</h3>
 * <ol>
 *   <li>{@link TokenBucketLimiter} —— 默认令牌桶限流器</li>
 *   <li>{@link SlidingWindowLimiter} —— 滑动窗口限流器（备选）</li>
 *   <li>{@link RateLimitTemplate} —— 基于 {@link RateLimiter} 提供函数式 API</li>
 *   <li>{@link RateLimitAspect} —— 基于 {@link RateLimitTemplate} 提供 AOP 声明式限流和熔断</li>
 * </ol>
 *
 * <h3>熔断链路</h3>
 * <ol>
 *   <li>{@link DefaultCircuitBreaker} —— 默认熔断器实现</li>
 *   <li>{@link RateLimitAspect} —— 切面中按名称惰性创建熔断器实例</li>
 * </ol>
 *
 * <p>激活条件：</p>
 * <ul>
 *   <li>配置项 {@code letool.rate-limiter.enabled} 为 {@code true}（默认为 true）</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(RateLimiterProperties.class)
@ConditionalOnProperty(prefix = "letool.rate-limiter", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RateLimiterAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterAutoConfiguration.class);

    // ======================== 限流器 Bean ========================

    /**
     * 注册默认的令牌桶限流器 Bean（TokenBucketLimiter）。
     *
     * <p>使用 {@link RateLimiterProperties.TokenBucket} 中的配置初始化桶容量和补充速率。</p>
     *
     * @param properties 限流配置属性
     * @return 令牌桶限流器实例
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(TokenBucketLimiter.class)
    public TokenBucketLimiter tokenBucketLimiter(RateLimiterProperties properties) {
        RateLimiterProperties.TokenBucket config = properties.getTokenBucket();
        log.info("Initializing TokenBucketLimiter: capacity={}, refillRate={}",
                config.getCapacity(), config.getRefillRate());
        return new TokenBucketLimiter(config.getCapacity(), config.getRefillRate());
    }

    /**
     * 注册滑动窗口限流器 Bean（SlidingWindowLimiter）。
     *
     * <p>使用 {@link RateLimiterProperties.SlidingWindow} 中的配置初始化窗口大小和最大许可数。</p>
     *
     * @param properties 限流配置属性
     * @return 滑动窗口限流器实例
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(SlidingWindowLimiter.class)
    public SlidingWindowLimiter slidingWindowLimiter(RateLimiterProperties properties) {
        RateLimiterProperties.SlidingWindow config = properties.getSlidingWindow();
        log.info("Initializing SlidingWindowLimiter: windowSize={}s, maxPermits={}",
                config.getWindowSize(), config.getMaxPermits());
        return new SlidingWindowLimiter(config.getWindowSize(), config.getMaxPermits());
    }

    // ======================== 限流模板 Bean ========================

    /**
     * 注册 {@link RateLimitTemplate} Bean，为用户提供函数式编程风格的限流操作 API。
     *
     * <p>根据配置的 {@code defaultAlgorithm} 属性选择合适的限流器（令牌桶或滑动窗口）：
     * <ul>
     *   <li>{@code "token-bucket"} → {@link TokenBucketLimiter}</li>
     *   <li>{@code "sliding-window"} → {@link SlidingWindowLimiter}</li>
     *   <li>其他 → {@link TokenBucketLimiter}（默认回退）</li>
     * </ul>
     *
     * @param properties         限流配置属性
     * @param tokenBucketLimiter 令牌桶限流器
     * @param slidingWindowLimiter 滑动窗口限流器
     * @return 限流模板实例
     */
    @Bean
    @ConditionalOnMissingBean(RateLimitTemplate.class)
    public RateLimitTemplate rateLimitTemplate(RateLimiterProperties properties,
                                                TokenBucketLimiter tokenBucketLimiter,
                                                SlidingWindowLimiter slidingWindowLimiter) {
        String algorithm = properties.getDefaultAlgorithm();
        RateLimiter limiter;

        if ("sliding-window".equalsIgnoreCase(algorithm)) {
            limiter = slidingWindowLimiter;
            log.info("Using SlidingWindowLimiter as default rate limiter");
        } else {
            // 默认使用令牌桶，包括 "token-bucket" 和未知值
            limiter = tokenBucketLimiter;
            log.info("Using TokenBucketLimiter as default rate limiter (algorithm={})", algorithm);
        }

        return new RateLimitTemplate(limiter, properties);
    }

    // ======================== 熔断器 Bean ========================

    /**
     * 注册默认熔断器 Bean（DefaultCircuitBreaker）。
     *
     * <p>此 Bean 作为全局默认熔断器，供 {@link RateLimitAspect} 在需要时
     * 使用其配置参数创建新的命名熔断器实例。</p>
     *
     * <p>注：通常每个 {@code @CircuitBreak} 注解会创建独立的熔断器实例。
     * 此默认 Bean 主要用于提供全局配置参考。</p>
     *
     * @param properties 限流配置属性
     * @return 默认熔断器实例
     */
    @Bean
    @ConditionalOnMissingBean(DefaultCircuitBreaker.class)
    public DefaultCircuitBreaker defaultCircuitBreaker(RateLimiterProperties properties) {
        RateLimiterProperties.CircuitBreaker config = properties.getCircuitBreaker();
        log.info("Initializing DefaultCircuitBreaker: failureThreshold={}, windowSize={}s, "
                        + "recoveryTimeout={}s, halfOpenMaxRequests={}",
                config.getFailureThreshold(), config.getWindowSize(),
                config.getRecoveryTimeout(), config.getHalfOpenMaxRequests());
        return new DefaultCircuitBreaker(
                "default",
                config.getFailureThreshold(),
                config.getWindowSize(),
                config.getRecoveryTimeout(),
                config.getHalfOpenMaxRequests()
        );
    }

    // ======================== 切面 Bean ========================

    /**
     * 注册 {@link RateLimitAspect} Bean，提供基于 {@code @RateLimit} 和
     * {@code @CircuitBreak} 注解的声明式限流和熔断。
     *
     * @param rateLimitTemplate 限流模板实例
     * @param properties        限流配置属性（用于构建默认熔断器配置）
     * @return 限流/熔断切面实例
     */
    @Bean
    @ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
    @ConditionalOnMissingBean(RateLimitAspect.class)
    @ConditionalOnProperty(prefix = "letool.rate-limiter.annotation", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RateLimitAspect rateLimitAspect(RateLimitTemplate rateLimitTemplate,
                                           RateLimiterProperties properties) {
        RateLimiterProperties.CircuitBreaker cbConfig = properties.getCircuitBreaker();
        RateLimitAspect.CircuitBreakerConfig config = new RateLimitAspect.CircuitBreakerConfig(
                cbConfig.getFailureThreshold(),
                (int) cbConfig.getWindowSize(),
                (int) cbConfig.getRecoveryTimeout(),
                cbConfig.getHalfOpenMaxRequests()
        );
        return new RateLimitAspect(rateLimitTemplate, config);
    }
}
