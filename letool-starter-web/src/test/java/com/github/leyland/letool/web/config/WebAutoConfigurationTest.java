package com.github.leyland.letool.web.config;

import com.github.leyland.letool.exception.config.ExceptionAutoConfiguration;
import com.github.leyland.letool.exception.message.MessageResolver;
import com.github.leyland.letool.web.advice.GlobalExceptionHandler;
import com.github.leyland.letool.web.advice.ResponseWrapperAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Web 自动配置的开关、依赖边界和业务接管测试。
 */
class WebAutoConfigurationTest {

    /** 仅加载异常与 Web 自动配置的 Servlet 应用上下文。 */
    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ExceptionAutoConfiguration.class,
                    WebAutoConfiguration.class))
            .withPropertyValues("spring.main.allow-bean-definition-overriding=false");

    /**
     * 验证默认启用低成本能力，并保持请求体缓存默认关闭。
     */
    @Test
    void shouldProvideLowCostDefaultsWithoutRequestBodyCache() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MessageResolver.class);
            assertThat(context).hasSingleBean(GlobalExceptionHandler.class);
            assertThat(context).hasSingleBean(ResponseWrapperAdvice.class);
            assertThat(context).hasSingleBean(WebMvcRegistrations.class);
            assertThat(context).doesNotHaveBean("repeatableRequestFilterRegistration");
        });
    }

    /**
     * 验证响应包装和 API 版本能力可以独立关闭。
     */
    @Test
    void shouldHonorIndependentFeatureSwitches() {
        contextRunner
                .withPropertyValues(
                        "letool.web.response-wrapper.enabled=false",
                        "letool.web.api-version.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ResponseWrapperAdvice.class);
                    assertThat(context).doesNotHaveBean(WebMvcRegistrations.class);
                    assertThat(context).hasSingleBean(GlobalExceptionHandler.class);
                });
    }

    /**
     * 验证请求体缓存只有显式开启并存在 MVC 异常解析器时才注册。
     */
    @Test
    void shouldCreateRepeatableRequestFilterOnlyWhenEnabled() {
        contextRunner
                .withUserConfiguration(ExceptionResolverConfiguration.class)
                .withPropertyValues("letool.web.repeatable-request.enabled=true")
                .run(context -> assertThat(context)
                        .hasBean("repeatableRequestFilterRegistration"));
    }

    /**
     * 验证超出 16 MiB 硬上限的缓存配置会在启动阶段失败。
     */
    @Test
    void shouldRejectUnsafeRepeatableRequestLimitAtStartup() {
        contextRunner
                .withUserConfiguration(ExceptionResolverConfiguration.class)
                .withPropertyValues(
                        "letool.web.repeatable-request.enabled=true",
                        "letool.web.repeatable-request.max-body-size=17MB")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .rootCause()
                        .hasMessageContaining("16 MiB"));
    }

    /**
     * 验证业务提供同类型基础设施时默认实现逐项退让。
     */
    @Test
    void shouldBackOffWhenApplicationProvidesInfrastructure() {
        contextRunner
                .withUserConfiguration(UserWebConfiguration.class)
                .run(context -> {
                    assertThat(context.getBean(GlobalExceptionHandler.class))
                            .isSameAs(context.getBean("customGlobalExceptionHandler"));
                    assertThat(context.getBean(ResponseWrapperAdvice.class))
                            .isSameAs(context.getBean("customResponseWrapperAdvice"));
                    assertThat(context.getBean(WebMvcRegistrations.class))
                            .isSameAs(context.getBean("customWebMvcRegistrations"));
                });
    }

    /**
     * 验证异常模块关闭时只退让依赖消息解析器的异常适配器。
     */
    @Test
    void shouldKeepIndependentFeaturesWhenExceptionModuleIsDisabled() {
        contextRunner
                .withPropertyValues("letool.exception.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MessageResolver.class);
                    assertThat(context).doesNotHaveBean(GlobalExceptionHandler.class);
                    assertThat(context).hasSingleBean(ResponseWrapperAdvice.class);
                    assertThat(context).hasSingleBean(WebMvcRegistrations.class);
                });
    }

    /**
     * 验证 Web 总开关关闭时不装配任何模块组件。
     */
    @Test
    void shouldDisableAllWebInfrastructureWithMasterSwitch() {
        contextRunner
                .withPropertyValues("letool.web.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(GlobalExceptionHandler.class);
                    assertThat(context).doesNotHaveBean(ResponseWrapperAdvice.class);
                    assertThat(context).doesNotHaveBean(WebMvcRegistrations.class);
                    assertThat(context).doesNotHaveBean("repeatableRequestFilterRegistration");
                });
    }

    /**
     * 提供请求体过滤器所需的 MVC 异常解析器。
     */
    @Configuration(proxyBeanMethods = false)
    static class ExceptionResolverConfiguration {

        /**
         * 创建最小异常解析器测试替身。
         *
         * @return 不处理异常的解析器
         */
        @Bean(name = "handlerExceptionResolver")
        HandlerExceptionResolver handlerExceptionResolver() {
            return (request, response, handler, exception) -> null;
        }
    }

    /**
     * 模拟业务逐项接管 Web 基础设施。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserWebConfiguration {

        /**
         * 创建业务自定义异常处理器。
         *
         * @param messageResolver 异常消息解析器
         * @return 自定义异常处理器
         */
        @Bean
        GlobalExceptionHandler customGlobalExceptionHandler(MessageResolver messageResolver) {
            return new GlobalExceptionHandler(messageResolver);
        }

        /**
         * 创建业务自定义响应包装器。
         *
         * @return 自定义响应包装器
         */
        @Bean
        ResponseWrapperAdvice customResponseWrapperAdvice() {
            return new ResponseWrapperAdvice(List.of("/custom/**"));
        }

        /**
         * 创建业务自定义 MVC 注册扩展。
         *
         * @return 自定义 MVC 注册扩展
         */
        @Bean
        WebMvcRegistrations customWebMvcRegistrations() {
            return new WebMvcRegistrations() {
            };
        }
    }
}
