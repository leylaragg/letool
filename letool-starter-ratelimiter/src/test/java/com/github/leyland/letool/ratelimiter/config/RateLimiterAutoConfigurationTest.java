package com.github.leyland.letool.ratelimiter.config;

import com.github.leyland.letool.ratelimiter.aspect.RateLimitAspect;
import com.github.leyland.letool.ratelimiter.core.RateLimitTemplate;
import com.github.leyland.letool.ratelimiter.core.RateLimiter;
import com.github.leyland.letool.ratelimiter.sentinel.SentinelRateLimiter;
import com.github.leyland.letool.ratelimiter.sentinel.SentinelRuleRegistrar;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RateLimiterAutoConfiguration} 自动装配契约测试。
 */
class RateLimiterAutoConfigurationTest {

    /**
     * 自动配置测试运行器。
     */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RateLimiterAutoConfiguration.class))
            .withPropertyValues("spring.main.allow-bean-definition-overriding=false");

    /**
     * 默认启用时应装配 Sentinel 限流基础设施。
     */
    @Test
    void shouldCreateSentinelRateLimiterInfrastructure() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(RateLimiterProperties.class);
            assertThat(context).hasSingleBean(SentinelRuleRegistrar.class);
            assertThat(context).hasSingleBean(SentinelRateLimiter.class);
            assertThat(context).hasSingleBean(RateLimiter.class);
            assertThat(context).hasSingleBean(RateLimitTemplate.class);
            assertThat(context).hasSingleBean(RateLimitAspect.class);
        });
    }

    /**
     * 总开关关闭时不应装配任何限流基础设施。
     */
    @Test
    void shouldDisableAllRateLimiterInfrastructure() {
        contextRunner
                .withPropertyValues("letool.rate-limiter.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RateLimiterProperties.class);
                    assertThat(context).doesNotHaveBean(RateLimiter.class);
                    assertThat(context).doesNotHaveBean(RateLimitTemplate.class);
                    assertThat(context).doesNotHaveBean(RateLimitAspect.class);
                });
    }

    /**
     * 关闭本地规则管理时应保留调用 API，并允许外部动态数据源接管规则。
     */
    @Test
    void shouldAllowExternalSentinelRuleManagement() {
        contextRunner
                .withPropertyValues("letool.rate-limiter.local-rules-enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(RateLimiter.class);
                    assertThat(context).hasSingleBean(RateLimitTemplate.class);
                    assertThat(context).doesNotHaveBean(SentinelRuleRegistrar.class);
                });
    }

    /**
     * 缺少 AspectJ 时应保留编程式 API，并跳过声明式切面。
     */
    @Test
    void shouldKeepProgrammaticApiWithoutAspectJ() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("org.aspectj"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RateLimitTemplate.class);
                    assertThat(context).doesNotHaveBean(RateLimitAspect.class);
                });
    }

    /**
     * 用户提供自定义限流器和模板时，自动配置应主动退让。
     */
    @Test
    void shouldBackOffForUserProvidedInfrastructure() {
        contextRunner
                .withUserConfiguration(UserRateLimiterConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(RateLimiter.class);
                    assertThat(context).hasSingleBean(RateLimitTemplate.class);
                    assertThat(context.getBean(RateLimiter.class))
                            .isSameAs(context.getBean("customRateLimiter"));
                    assertThat(context.getBean(RateLimitTemplate.class))
                            .isSameAs(context.getBean("customRateLimitTemplate"));
                });
    }

    /**
     * 用户自定义限流基础设施。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserRateLimiterConfiguration {

        /**
         * 创建始终放行的测试限流器。
         *
         * @return 自定义限流器
         */
        @Bean
        RateLimiter customRateLimiter() {
            return (policy, key, permits) ->
                    com.github.leyland.letool.ratelimiter.core.RateLimitResult.allowed();
        }

        /**
         * 创建自定义限流模板。
         *
         * @param customRateLimiter 自定义限流器
         * @param properties        限流配置
         * @return 自定义限流模板
         */
        @Bean
        RateLimitTemplate customRateLimitTemplate(RateLimiter customRateLimiter,
                                                   RateLimiterProperties properties) {
            return new RateLimitTemplate(customRateLimiter, properties);
        }
    }
}
