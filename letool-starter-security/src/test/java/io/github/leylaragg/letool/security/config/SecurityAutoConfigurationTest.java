package io.github.leylaragg.letool.security.config;

import io.github.leylaragg.letool.security.aspect.SecurityAnnotationAspect;
import io.github.leylaragg.letool.security.exception.SecurityException;
import io.github.leylaragg.letool.security.handler.AccessDeniedExceptionHandler;
import io.github.leylaragg.letool.security.handler.SecurityExceptionHandler;
import io.github.leylaragg.letool.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
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
                    WebMvcAutoConfiguration.class,
                    SecurityAutoConfiguration.class
            ))
            .withPropertyValues(
                    "spring.main.allow-bean-definition-overriding=false",
                    "letool.security.jwt.secret=test-security-secret-key-at-least-256-bits",
                    "letool.security.jwt.issuer=security-test"
            );

    /**
     * 验证默认认证链由 Spring Security Resource Server 的 {@link JwtDecoder} 驱动，
     * 不再注册自维护的 JWT Servlet 过滤器。
     */
    @Test
    void shouldUseResourceServerJwtInfrastructureByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(JwtDecoder.class);
            assertThat(context).doesNotHaveBean(
                    "jwtAuthenticationFilter"
            );
        });
    }

    /**
     * 验证启用默认 JWT 能力但未配置生产密钥时在启动阶段失败。
     */
    @Test
    void shouldFailFastWhenJwtSecretUsesUnsafeDefault() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
                        SecurityFilterAutoConfiguration.class,
                        WebMvcAutoConfiguration.class,
                        SecurityAutoConfiguration.class
                ))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(SecurityException.class);
                });
    }

    /**
     * 验证携带凭据时不能配置通配符来源，避免任意网站发起带身份请求。
     */
    @Test
    void shouldRejectWildcardCorsOriginWhenCredentialsAreAllowed() {
        contextRunner
                .withPropertyValues(
                        "letool.security.cors.allowed-origins=*",
                        "letool.security.cors.allow-credentials=true"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    /**
     * 验证业务只提供外部 {@link JwtDecoder} 时不需要配置本地 HMAC 密钥，
     * 并且不会创建仅用于本地签发令牌的 {@link JwtTokenProvider}。
     */
    @Test
    void shouldSupportExternalJwtDecoderWithoutLocalSecret() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
                        SecurityFilterAutoConfiguration.class,
                        WebMvcAutoConfiguration.class,
                        SecurityAutoConfiguration.class
                ))
                .withUserConfiguration(ExternalDecoderConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JwtDecoder.class);
                    assertThat(context).doesNotHaveBean(JwtTokenProvider.class);
                });
    }

    /**
     * 验证用户提供完整安全基础设施 Bean 时，自动配置不会创建同类型默认 Bean。
     */
    @Test
    void shouldBackOffWhenUserProvidesSecurityBeans() {
        contextRunner
                .withUserConfiguration(UserSecurityConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(JwtTokenProvider.class);
                    assertThat(context).hasSingleBean(JwtDecoder.class);
                    assertThat(context).hasSingleBean(SecurityExceptionHandler.class);
                    assertThat(context).hasSingleBean(AccessDeniedExceptionHandler.class);
                    assertThat(context).hasSingleBean(SecurityAnnotationAspect.class);
                    assertThat(context).hasSingleBean(SecurityFilterChain.class);
                    assertThat(context.getBean(JwtTokenProvider.class))
                            .isSameAs(context.getBean("userJwtTokenProvider"));
                    assertThat(context.getBean(JwtDecoder.class))
                            .isSameAs(context.getBean("userJwtDecoder"));
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
        JwtDecoder userJwtDecoder() {
            return token -> null;
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

    /**
     * 模拟业务使用外部授权服务器，仅替换 JWT 解码器的配置。
     */
    @Configuration(proxyBeanMethods = false)
    static class ExternalDecoderConfiguration {

        /**
         * 提供外部 JWT 解码器测试替身。
         *
         * @return 不执行实际解码的测试替身
         */
        @Bean
        JwtDecoder externalJwtDecoder() {
            return token -> null;
        }
    }
}
