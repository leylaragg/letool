package com.github.leyland.letool.web.config;

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
 * {@link WebAutoConfiguration} 的自动装配契约测试。
 *
 * <p>重点覆盖业务项目自定义 Web 基础设施 Bean 时，web starter 是否正确退让，
 * 以及 Servlet MVC 依赖缺失时是否避免加载 Servlet 专用配置。</p>
 */
class WebAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WebAutoConfiguration.class))
            .withPropertyValues("spring.main.allow-bean-definition-overriding=false");

    /**
     * 验证用户提供异常处理、响应包装和过滤器注册 Bean 时，自动配置不会创建重复 Bean。
     */
    @Test
    void shouldBackOffWhenUserProvidesWebInfrastructureBeans() {
        contextRunner
                .withUserConfiguration(UserWebConfiguration.class)
                .run(context -> {
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

    /**
     * 验证 Servlet MVC 核心类不存在时，web starter 不参与自动装配。
     */
    @Test
    void shouldBackOffWhenServletMvcClassesAreMissing() {
        new WebApplicationContextRunner()
                .withClassLoader(new FilteredClassLoader(
                        DispatcherServlet.class,
                        RequestMappingHandlerMapping.class,
                        FilterRegistrationBean.class
                ))
                .withConfiguration(AutoConfigurations.of(WebAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(GlobalExceptionHandler.class));
    }

    /**
     * 模拟业务项目自行接管 Web 基础设施 Bean 的配置。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserWebConfiguration {

        @Bean
        GlobalExceptionHandler globalExceptionHandler() {
            return new GlobalExceptionHandler();
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
