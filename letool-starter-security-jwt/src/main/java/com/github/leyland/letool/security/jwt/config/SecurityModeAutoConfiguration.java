package com.github.leyland.letool.security.jwt.config;

import cn.hutool.core.util.StrUtil;
import com.github.leyland.letool.security.jwt.constant.SecurityJwtConstant;
import com.github.leyland.letool.security.jwt.jwt.*;
import com.github.leyland.letool.security.jwt.service.*;
import com.github.leyland.letool.security.jwt.service.impl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.servlet.Filter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Spring Security 模式自动配置
 * <p>
 * 当 mode=security 时启用，使用完整的 Spring Security 框架
 *
 * @author Rungo
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true)
@EnableConfigurationProperties(SecurityJwtProperties.class)
@ConditionalOnClass(name = "org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter")
@ConditionalOnProperty(prefix = "letool.security.jwt", name = "enabled", havingValue = "true")
public class SecurityModeAutoConfiguration extends WebSecurityConfigurerAdapter {

    private static final Logger log = LoggerFactory.getLogger(SecurityModeAutoConfiguration.class);

    private final SecurityJwtProperties properties;

    @Autowired
    private JwtUserDetailsService userDetailsService;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private TokenStorageService tokenStorageService;

    public SecurityModeAutoConfiguration(SecurityJwtProperties properties) {
        this.properties = properties;
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
     * JWT Token Provider
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtTokenProvider jwtTokenProvider() {
        return new JwtTokenProvider(properties.getJwt());
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
     * 自定义 AuthenticationProvider
     */
    @Bean
    @ConditionalOnMissingBean
    public AuthenticationProvider jwtAuthenticationProvider() {
        return new JwtAuthenticationProvider(userDetailsService);
    }

    /**
     * JWT 认证过滤器
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenProvider tokenProvider,
                                                           TokenStorageService tokenStorageService) {
        return new JwtAuthenticationFilter(tokenProvider, tokenStorageService, properties, userDetailsService);
    }

    /**
     * 认证入口点
     */
    @Bean
    @ConditionalOnMissingBean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return new JwtAuthenticationEntryPoint();
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.authenticationProvider(jwtAuthenticationProvider());
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                // 禁用 CSRF
                .csrf().disable()
                // 禁用 Session
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                // 配置白名单路径
                .authorizeRequests()
                .antMatchers(properties.getWhitePaths()).permitAll()
                .anyRequest().authenticated()
                .and()
                // 添加 JWT 过滤器
                .addFilterBefore(jwtAuthenticationFilter(tokenProvider, tokenStorageService),
                        UsernamePasswordAuthenticationFilter.class)
                // 配置异常处理
                .exceptionHandling()
                .authenticationEntryPoint(authenticationEntryPoint());
    }

    /**
     * JWT 认证过滤器
     */
    public static class JwtAuthenticationFilter implements Filter {

        private final JwtTokenProvider tokenProvider;
        private final TokenStorageService tokenStorageService;
        private final SecurityJwtProperties properties;
        private final JwtUserDetailsService userDetailsService;

        public JwtAuthenticationFilter(JwtTokenProvider tokenProvider,
                                       TokenStorageService tokenStorageService,
                                       SecurityJwtProperties properties,
                                       JwtUserDetailsService userDetailsService) {
            this.tokenProvider = tokenProvider;
            this.tokenStorageService = tokenStorageService;
            this.properties = properties;
            this.userDetailsService = userDetailsService;
        }

        @Override
        public void doFilter(javax.servlet.ServletRequest request, javax.servlet.ServletResponse response,
                             javax.servlet.FilterChain chain) throws IOException, javax.servlet.ServletException {
            HttpServletRequest httpRequest = (HttpServletRequest) request;

            String authHeader = httpRequest.getHeader(tokenProvider.getHeaderName());
            String token = tokenProvider.extractToken(authHeader);

            if (StrUtil.isNotEmpty(token)) {
                try {
                    TokenPayload payload = tokenProvider.parseToken(token);

                    // 验证 Token 类型
                    if (!SecurityJwtConstant.TOKEN_TYPE_ACCESS.equals(payload.getTokenType())) {
                        throw new JwtTokenInvalidException("非Access Token");
                    }

                    // 验证存储
                    if (properties.getStorage().isEnableCache() && !tokenStorageService.existsAccessToken(payload.getTokenId())) {
                        throw new JwtTokenInvalidException("Token已失效");
                    }

                    // 检查用户状态
                    if (userDetailsService.isUserDisabled(payload.getUserId())) {
                        throw new JwtTokenInvalidException("用户已被禁用");
                    }

                    // 设置上下文
                    SecurityContextHolder.setPayload(payload);

                } catch (JwtTokenException e) {
                    log.warn("JWT认证失败: {}", e.getMessage());
                }
            }

            chain.doFilter(request, response);
            SecurityContextHolder.clear();
        }
    }

    /**
     * JWT 认证 Provider
     */
    public static class JwtAuthenticationProvider implements AuthenticationProvider {

        private final JwtUserDetailsService userDetailsService;

        public JwtAuthenticationProvider(JwtUserDetailsService userDetailsService) {
            this.userDetailsService = userDetailsService;
        }

        @Override
        public Authentication authenticate(Authentication authentication) throws AuthenticationException {
            String username = authentication.getName();
            String password = (String) authentication.getCredentials();

            JwtUser jwtUser = userDetailsService.loadUserByUsername(username);
            if (jwtUser == null) {
                throw new BadCredentialsException("用户不存在");
            }

            if (!userDetailsService.verifyPassword(username, password)) {
                throw new BadCredentialsException("密码错误");
            }

            if (userDetailsService.isUserDisabled(jwtUser.getId())) {
                throw new DisabledException("用户已被禁用");
            }

            // 构建 GrantedAuthority
            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            if (jwtUser.getRoles() != null) {
                authorities.addAll(jwtUser.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .collect(Collectors.toList()));
            }
            if (jwtUser.getPermissions() != null) {
                authorities.addAll(jwtUser.getPermissions().stream()
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList()));
            }

            return new UsernamePasswordAuthenticationToken(jwtUser, password, authorities);
        }

        @Override
        public boolean supports(Class<?> authentication) {
            return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
        }
    }

    /**
     * JWT 认证入口点
     */
    public static class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

        @Override
        public void commence(HttpServletRequest request, HttpServletResponse response,
                             AuthenticationException authException) throws IOException {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"code\":401,\"success\":false,\"msg\":\"" + authException.getMessage() + "\"}");
        }
    }
}