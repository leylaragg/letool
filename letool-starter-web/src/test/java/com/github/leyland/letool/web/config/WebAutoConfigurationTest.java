package com.github.leyland.letool.web.config;

import com.github.leyland.letool.exception.config.ExceptionAutoConfiguration;
import com.github.leyland.letool.exception.message.MessageResolver;
import com.github.leyland.letool.web.advice.GlobalExceptionHandler;
import com.github.leyland.letool.web.advice.ResponseWrapperAdvice;
import com.github.leyland.letool.web.filter.RepeatableRequestFilter;
import com.github.leyland.letool.web.filter.SqlInjectionFilter;
import com.github.leyland.letool.web.filter.XssFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 Web 基础设施的自动退让行为，包括异常消息解析器边界。
 */
class WebAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ExceptionAutoConfiguration.class,
                    WebAutoConfiguration.class))
            .withPropertyValues("spring.main.allow-bean-definition-overriding=false");

    @Test
    void defaultConfigurationShouldProvideResolverAndGlobalExceptionHandler() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MessageResolver.class);
            assertThat(context).hasSingleBean(GlobalExceptionHandler.class);
            assertThat(context).hasSingleBean(ResponseWrapperAdvice.class);
        });
    }

    @Test
    void disabledExceptionFrameworkShouldBackOffCodedHandlerOnly() {
        contextRunner
                .withPropertyValues("letool.exception.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MessageResolver.class);
                    assertThat(context).doesNotHaveBean(GlobalExceptionHandler.class);
                    assertThat(context).hasSingleBean(ResponseWrapperAdvice.class);
                });
    }

    @Test
    void shouldBackOffWhenUserProvidesWebInfrastructureBeans() {
        contextRunner
                .withUserConfiguration(UserWebConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(MessageResolver.class);
                    assertThat(context).hasSingleBean(GlobalExceptionHandler.class);
                    assertThat(context).hasSingleBean(ResponseWrapperAdvice.class);
                    assertThat(context.getBean(GlobalExceptionHandler.class))
                            .isSameAs(context.getBean("globalExceptionHandler"));
                    assertThat(context.getBean(ResponseWrapperAdvice.class))
                            .isSameAs(context.getBean("responseBodyAdvice"));
                    assertThat(context.getBean("xssFilterRegistration"))
                            .isSameAs(context.getBean("userXssFilterRegistration"));
                    assertThat(context.getBean("sqlInjectionFilterRegistration"))
                            .isSameAs(context.getBean("userSqlInjectionFilterRegistration"));
                    assertThat(context.getBean("repeatableRequestFilterRegistration"))
                            .isSameAs(context.getBean("userRepeatableRequestFilterRegistration"));
                });
    }

    @Test
    void shouldBackOffWhenServletMvcClassesAreMissing() {
        new WebApplicationContextRunner()
                .withClassLoader(new FilteredClassLoader(
                        DispatcherServlet.class,
                        RequestMappingHandlerMapping.class,
                        FilterRegistrationBean.class
                ))
                .withConfiguration(AutoConfigurations.of(
                        ExceptionAutoConfiguration.class,
                        WebAutoConfiguration.class))
                .run(context -> assertThat(context)
                        .doesNotHaveBean(GlobalExceptionHandler.class));
    }

    /**
     * 模拟应用自行接管 Web 基础设施 Bean。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserWebConfiguration {

        @Bean
        GlobalExceptionHandler globalExceptionHandler(MessageResolver messageResolver) {
            return new GlobalExceptionHandler(messageResolver);
        }

        @Bean
        ResponseWrapperAdvice responseBodyAdvice() {
            return new ResponseWrapperAdvice();
        }

        @Bean({"xssFilterRegistration", "userXssFilterRegistration"})
        FilterRegistrationBean<XssFilter> xssFilterRegistration() {
            FilterRegistrationBean<XssFilter> registration = new FilterRegistrationBean<>();
            registration.setFilter(new XssFilter());
            registration.setName("userXssFilter");
            return registration;
        }

        @Bean({"sqlInjectionFilterRegistration", "userSqlInjectionFilterRegistration"})
        FilterRegistrationBean<SqlInjectionFilter> sqlInjectionFilterRegistration() {
            FilterRegistrationBean<SqlInjectionFilter> registration = new FilterRegistrationBean<>();
            registration.setFilter(new SqlInjectionFilter());
            registration.setName("userSqlInjectionFilter");
            return registration;
        }

        @Bean({"repeatableRequestFilterRegistration", "userRepeatableRequestFilterRegistration"})
        FilterRegistrationBean<RepeatableRequestFilter> repeatableRequestFilterRegistration() {
            FilterRegistrationBean<RepeatableRequestFilter> registration = new FilterRegistrationBean<>();
            registration.setFilter(new RepeatableRequestFilter());
            registration.setName("userRepeatableRequestFilter");
            return registration;
        }
    }
}
