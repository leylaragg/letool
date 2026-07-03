package com.github.leyland.letool.ratelimiter.config;

import com.github.leyland.letool.ratelimiter.algorithm.SlidingWindowLimiter;
import com.github.leyland.letool.ratelimiter.algorithm.TokenBucketLimiter;
import com.github.leyland.letool.ratelimiter.aspect.RateLimitAspect;
import com.github.leyland.letool.ratelimiter.circuit.DefaultCircuitBreaker;
import com.github.leyland.letool.ratelimiter.core.RateLimitTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RateLimiterAutoConfiguration} 的自动装配契约测试。
 *
 * <p>重点覆盖业务项目自定义限流基础设施 Bean 时，ratelimiter starter 是否正确退让。</p>
 */
class RateLimiterAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RateLimiterAutoConfiguration.class))
            .withPropertyValues("spring.main.allow-bean-definition-overriding=false");

    /**
     * 默认启用时应创建编程式限流、熔断和注解切面基础设施。
     */
    @Test
    void shouldCreateDefaultRateLimiterInfrastructureBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(RateLimiterProperties.class);
            assertThat(context).hasSingleBean(TokenBucketLimiter.class);
            assertThat(context).hasSingleBean(SlidingWindowLimiter.class);
            assertThat(context).hasSingleBean(RateLimitTemplate.class);
            assertThat(context).hasSingleBean(DefaultCircuitBreaker.class);
            assertThat(context).hasSingleBean(RateLimitAspect.class);
        });
    }

    /**
     * 总开关关闭时，ratelimiter starter 不应创建任何限流基础设施。
     */
    @Test
    void shouldDisableRateLimiterAutoConfiguration() {
        contextRunner
                .withPropertyValues("letool.rate-limiter.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RateLimiterProperties.class);
                    assertThat(context).doesNotHaveBean(RateLimitTemplate.class);
                    assertThat(context).doesNotHaveBean(RateLimitAspect.class);
                });
    }

    /**
     * 没有 AspectJ 时，应保留编程式限流 API，并跳过注解切面。
     */
    @Test
    void shouldStartWithoutRateLimitAspectWhenAspectJClasspathIsMissing() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("org.aspectj"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RateLimitTemplate.class);
                    assertThat(context).hasSingleBean(DefaultCircuitBreaker.class);
                    assertThat(context).doesNotHaveBean(RateLimitAspect.class);
                });
    }

    /**
     * 限流核心不应依赖 cache/redis classpath，缺失时仍可作为纯本地算法工具使用。
     */
    @Test
    void shouldStartWithoutCacheAndRedisClasspath() {
        contextRunner
                .withClassLoader(new FilteredClassLoader(
                        "com.github.leyland.letool.cache",
                        "org.springframework.data.redis"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RateLimitTemplate.class);
                });
    }

    /**
     * 验证用户提供限流器、模板、熔断器和切面时，自动配置不会创建重复 Bean。
     */
    @Test
    void shouldBackOffWhenUserProvidesRateLimiterInfrastructureBeans() {
        contextRunner
                .withUserConfiguration(UserRateLimiterConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(TokenBucketLimiter.class);
                    assertThat(context).hasSingleBean(SlidingWindowLimiter.class);
                    assertThat(context).hasSingleBean(RateLimitTemplate.class);
                    assertThat(context).hasSingleBean(DefaultCircuitBreaker.class);
                    assertThat(context).hasSingleBean(RateLimitAspect.class);
                    assertThat(context.getBean(TokenBucketLimiter.class))
                            .isSameAs(context.getBean("tokenBucketLimiter"));
                    assertThat(context.getBean(SlidingWindowLimiter.class))
                            .isSameAs(context.getBean("slidingWindowLimiter"));
                    assertThat(context.getBean(RateLimitTemplate.class))
                            .isSameAs(context.getBean("rateLimitTemplate"));
                    assertThat(context.getBean(DefaultCircuitBreaker.class))
                            .isSameAs(context.getBean("defaultCircuitBreaker"));
                    assertThat(context.getBean(RateLimitAspect.class))
                            .isSameAs(context.getBean("rateLimitAspect"));
                });
    }

    /**
     * Disabling annotation support should keep the programmatic rate-limit API available.
     */
    @Test
    void shouldDisableRateLimitAspectWhenAnnotationSupportIsDisabled() {
        contextRunner
                .withPropertyValues("letool.rate-limiter.annotation.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(TokenBucketLimiter.class);
                    assertThat(context).hasSingleBean(SlidingWindowLimiter.class);
                    assertThat(context).hasSingleBean(RateLimitTemplate.class);
                    assertThat(context).hasSingleBean(DefaultCircuitBreaker.class);
                    assertThat(context).doesNotHaveBean(RateLimitAspect.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class UserRateLimiterConfiguration {

        @Bean(destroyMethod = "shutdown")
        TokenBucketLimiter tokenBucketLimiter() {
            return new TokenBucketLimiter(100, 10.0);
        }

        @Bean(destroyMethod = "shutdown")
        SlidingWindowLimiter slidingWindowLimiter() {
            return new SlidingWindowLimiter(60, 100);
        }

        @Bean
        RateLimitTemplate rateLimitTemplate(TokenBucketLimiter tokenBucketLimiter,
                                            RateLimiterProperties properties) {
            return new RateLimitTemplate(tokenBucketLimiter, properties);
        }

        @Bean
        DefaultCircuitBreaker defaultCircuitBreaker() {
            return new DefaultCircuitBreaker("user", 0.5, 60, 30, 3);
        }

        @Bean
        RateLimitAspect rateLimitAspect(RateLimitTemplate rateLimitTemplate) {
            RateLimitAspect.CircuitBreakerConfig config =
                    new RateLimitAspect.CircuitBreakerConfig(0.5, 60, 30, 3);
            return new RateLimitAspect(rateLimitTemplate, config);
        }
    }
}
