package com.github.leyland.letool.security.jwt.config;

import com.github.leyland.letool.security.jwt.interceptor.TokenAuthInterceptor;
import com.github.leyland.letool.security.jwt.jwt.JwtTokenProvider;
import com.github.leyland.letool.security.jwt.service.*;
import com.github.leyland.letool.security.jwt.service.impl.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Token 模式自动配置
 * <p>
 * 当 mode=token 时启用，不依赖 Spring Security
 *
 * @author Rungo
 */
@Configuration
@EnableConfigurationProperties(SecurityJwtProperties.class)
@ConditionalOnProperty(prefix = "letool.security.jwt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TokenModeAutoConfiguration implements WebMvcConfigurer {

    private final SecurityJwtProperties properties;

    @Autowired(required = false)
    private JwtUserDetailsService userDetailsService;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private TokenStorageService tokenStorageService;

    public TokenModeAutoConfiguration(SecurityJwtProperties properties) {
        this.properties = properties;
    }

    /**
     * JWT Token Provider
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtTokenProvider jwtTokenProvider() {
        return new JwtTokenProvider(properties.getJwt());
    }

    /**
     * 默认用户详情服务 (仅当业务系统未提供时使用)
     */
    @Bean
    @ConditionalOnMissingBean(JwtUserDetailsService.class)
    public JwtUserDetailsService defaultJwtUserDetailsService() {
        return new DefaultJwtUserDetailsService();
    }

    /**
     * Token 存储服务 - 内存模式
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "letool.security.jwt.storage", name = "type", havingValue = "MEMORY", matchIfMissing = true)
    public TokenStorageService memoryTokenStorageService() {
        return new MemoryTokenStorageServiceImpl();
    }

    /**
     * Token 存储服务 - 无存储模式
     */
    @Bean
    @ConditionalOnMissingBean(TokenStorageService.class)
    @ConditionalOnProperty(prefix = "letool.security.jwt.storage", name = "type", havingValue = "NONE")
    public TokenStorageService noneTokenStorageService() {
        return new NoneTokenStorageServiceImpl();
    }

    /**
     * 认证服务
     */
    @Bean
    @ConditionalOnMissingBean
    public AuthService authService(JwtTokenProvider tokenProvider,
                                    JwtUserDetailsService userDetailsService,
                                    TokenStorageService tokenStorageService) {
        return new AuthServiceImpl(tokenProvider, userDetailsService, tokenStorageService, properties);
    }

    /**
     * Token 认证拦截器
     */
    @Bean
    @ConditionalOnMissingBean
    public TokenAuthInterceptor tokenAuthInterceptor(JwtTokenProvider tokenProvider,
                                                      TokenStorageService tokenStorageService,
                                                      JwtUserDetailsService userDetailsService) {
        return new TokenAuthInterceptor(tokenProvider, tokenStorageService, userDetailsService, properties);
    }

    /**
     * 注册拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 使用注入的 userDetailsService，如果没有则使用默认实现
        JwtUserDetailsService service = userDetailsService != null ? userDetailsService : new DefaultJwtUserDetailsService();
        TokenAuthInterceptor interceptor = new TokenAuthInterceptor(tokenProvider, tokenStorageService, service, properties);
        registry.addInterceptor(interceptor)
                .addPathPatterns("/**")
                .order(1);
    }
}