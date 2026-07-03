package com.github.leyland.letool.security.config;

import com.github.leyland.letool.security.aspect.SecurityAnnotationAspect;
import com.github.leyland.letool.security.filter.JwtAuthenticationFilter;
import com.github.leyland.letool.security.handler.AccessDeniedExceptionHandler;
import com.github.leyland.letool.security.handler.SecurityExceptionHandler;
import com.github.leyland.letool.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SecurityAutoConfiguration} 的自动装配契约测试。
 *
 * <p>重点覆盖业务项目自定义 Spring Security 基础设施 Bean 时，security starter
 * 是否遵守 Spring Boot starter 的退让规则。</p>
 */
class SecurityAutoConfigurationTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
                    SecurityFilterAutoConfiguration.class,
                    SecurityAutoConfiguration.class
            ))
            .withPropertyValues("spring.main.allow-bean-definition-overriding=false");

    /**
     * 验证用户提供完整安全基础设施 Bean 时，自动配置不会创建同类型默认 Bean。
     */
    @Test
    void shouldBackOffWhenUserProvidesSecurityBeans() {
        contextRunner
                .withUserConfiguration(UserSecurityConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(JwtTokenProvider.class);
                    assertThat(context).hasSingleBean(JwtAuthenticationFilter.class);
                    assertThat(context).hasSingleBean(SecurityExceptionHandler.class);
                    assertThat(context).hasSingleBean(AccessDeniedExceptionHandler.class);
                    assertThat(context).hasSingleBean(SecurityAnnotationAspect.class);
                    assertThat(context).hasSingleBean(SecurityFilterChain.class);
                    assertThat(context.getBean(JwtTokenProvider.class))
                            .isSameAs(context.getBean("userJwtTokenProvider"));
                    assertThat(context.getBean(JwtAuthenticationFilter.class))
                            .isSameAs(context.getBean("userJwtAuthenticationFilter"));
                    assertThat(context.getBean(SecurityExceptionHandler.class))
                            .isSameAs(context.getBean("userSecurityExceptionHandler"));
                    assertThat(context.getBean(AccessDeniedExceptionHandler.class))
                            .isSameAs(context.getBean("userAccessDeniedExceptionHandler"));
                    assertThat(context.getBean(SecurityAnnotationAspect.class))
                            .isSameAs(context.getBean("userSecurityAnnotationAspect"));
                    assertThat(context.getBean(SecurityFilterChain.class))
                            .isSameAs(context.getBean("userSecurityFilterChain"));
                });
    }

    /**
     * 验证 Spring Security servlet 核心类不存在时，security starter 不参与自动装配。
     */
    @Test
    void shouldBackOffWhenSpringSecurityServletClassesAreMissing() {
        new WebApplicationContextRunner()
                .withClassLoader(new FilteredClassLoader(SecurityFilterChain.class, HttpSecurity.class))
                .withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(JwtTokenProvider.class));
    }

    /**
     * 模拟业务项目完全接管安全认证链路的配置。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserSecurityConfiguration {

        @Bean
        JwtTokenProvider userJwtTokenProvider(SecurityProperties securityProperties) {
            return new JwtTokenProvider(securityProperties);
        }

        @Bean
        JwtAuthenticationFilter userJwtAuthenticationFilter(JwtTokenProvider userJwtTokenProvider,
                                                            SecurityProperties securityProperties) {
            return new JwtAuthenticationFilter(userJwtTokenProvider, securityProperties);
        }

        @Bean
        SecurityExceptionHandler userSecurityExceptionHandler() {
            return new SecurityExceptionHandler();
        }

        @Bean
        AccessDeniedExceptionHandler userAccessDeniedExceptionHandler() {
            return new AccessDeniedExceptionHandler();
        }

        @Bean
        SecurityAnnotationAspect userSecurityAnnotationAspect() {
            return new SecurityAnnotationAspect();
        }

        @Bean
        SecurityFilterChain userSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .build();
        }
    }
}
