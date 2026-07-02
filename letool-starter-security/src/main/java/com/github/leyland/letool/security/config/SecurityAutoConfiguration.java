package com.github.leyland.letool.security.config;

import com.github.leyland.letool.security.aspect.SecurityAnnotationAspect;
import com.github.leyland.letool.security.filter.JwtAuthenticationFilter;
import com.github.leyland.letool.security.handler.AccessDeniedExceptionHandler;
import com.github.leyland.letool.security.handler.SecurityExceptionHandler;
import com.github.leyland.letool.security.jwt.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * 安全模块自动配置，注册 Spring Security 所需的全部 Bean。
 *
 * <p>启用条件：Web 环境 + {@code letool.security.enabled=true}（默认启用）。</p>
 *
 * <p>注册的 Bean：</p>
 * <ul>
 *   <li>{@link JwtTokenProvider} — JWT 令牌生成与解析</li>
 *   <li>{@link JwtAuthenticationFilter} — 从 Authorization Header 提取 Token 并认证</li>
 *   <li>{@link SecurityExceptionHandler} — 未认证请求返回 401 JSON</li>
 *   <li>{@link AccessDeniedExceptionHandler} — 权限不足返回 403 JSON</li>
 *   <li>{@link SecurityAnnotationAspect} — {@code @RequireRole / @RequirePermission} 切面</li>
 *   <li>{@link SecurityFilterChain} — 无状态会话、CSRF 禁用、路径鉴权、JWT 过滤器链</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(SecurityProperties.class)
@ConditionalOnClass({HttpSecurity.class, SecurityFilterChain.class})
@ConditionalOnWebApplication
@ConditionalOnProperty(prefix = "letool.security", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableMethodSecurity
public class SecurityAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SecurityAutoConfiguration.class);

    /** 暴露 JwtTokenProvider 供其他模块引用 */
    @Bean
    @ConditionalOnMissingBean(JwtTokenProvider.class)
    public JwtTokenProvider jwtTokenProvider(SecurityProperties properties) {
        return new JwtTokenProvider(properties);
    }

    @Bean
    @ConditionalOnMissingBean(JwtAuthenticationFilter.class)
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                                            SecurityProperties properties) {
        return new JwtAuthenticationFilter(jwtTokenProvider, properties);
    }

    @Bean
    @ConditionalOnMissingBean(SecurityExceptionHandler.class)
    public SecurityExceptionHandler securityExceptionHandler() {
        return new SecurityExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean(AccessDeniedExceptionHandler.class)
    public AccessDeniedExceptionHandler accessDeniedExceptionHandler() {
        return new AccessDeniedExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean(SecurityAnnotationAspect.class)
    public SecurityAnnotationAspect securityAnnotationAspect() {
        return new SecurityAnnotationAspect();
    }

    /**
     * 构建 SecurityFilterChain：无状态会话、禁用 CSRF、JWT 过滤器、
     * 排除路径放行、其余请求需认证。
     */
    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                    JwtAuthenticationFilter jwtFilter,
                                                    SecurityExceptionHandler authEntryPoint,
                                                    AccessDeniedExceptionHandler accessDeniedHandler,
                                                    SecurityProperties properties) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource(properties)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> {
                    for (String path : properties.getExcludePaths()) {
                        auth.requestMatchers(path).permitAll();
                    }
                    auth.requestMatchers("/auth/login", "/auth/register", "/public/**",
                            "/swagger-ui/**", "/v3/api-docs/**", "/actuator/health").permitAll();
                    auth.anyRequest().authenticated();
                })
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        log.info("Security auto configuration initialized, auth mode: {}", properties.getAuthMode());
        return http.build();
    }

    /**
     * 根据配置构建 CORS 配置源，禁用时返回空配置。
     */
    private CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        SecurityProperties.Cors corsProps = properties.getCors();
        if (!corsProps.isEnabled()) {
            return request -> null;
        }
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(corsProps.getAllowedOrigins().split(",")));
        config.setAllowedMethods(Arrays.asList(corsProps.getAllowedMethods().split(",")));
        config.setAllowedHeaders(List.of(corsProps.getAllowedHeaders().split(",")));
        config.setAllowCredentials(true);
        config.setMaxAge(corsProps.getMaxAge());
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
