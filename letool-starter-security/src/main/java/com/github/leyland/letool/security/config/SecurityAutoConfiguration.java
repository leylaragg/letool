package com.github.leyland.letool.security.config;

import com.github.leyland.letool.security.aspect.SecurityAnnotationAspect;
import com.github.leyland.letool.security.exception.SecurityException;
import com.github.leyland.letool.security.handler.AccessDeniedExceptionHandler;
import com.github.leyland.letool.security.handler.SecurityExceptionHandler;
import com.github.leyland.letool.security.jwt.JwtTokenProvider;
import com.github.leyland.letool.security.jwt.SecurityJwtAuthenticationConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import java.util.Arrays;
import java.util.List;

/**
 * 安全模块自动配置。
 *
 * <p>HTTP Bearer Token 的读取、签名验证、过期校验和异常传播全部交给 Spring
 * Security OAuth2 Resource Server。Letool 只负责声明安全默认值、将 JWT Claims
 * 映射为 {@code LoginUser}，以及提供登录接口可选使用的轻量令牌签发器。</p>
 *
 * <p>用户可以通过标准 {@link JwtDecoder}、{@link SecurityFilterChain}、
 * {@link SecurityExceptionHandler}、{@link AccessDeniedExceptionHandler} 或稳定名称
 * {@code letoolJwtAuthenticationConverter} 接管相应能力。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(SecurityProperties.class)
@ConditionalOnClass({HttpSecurity.class, SecurityFilterChain.class, JwtDecoder.class})
@ConditionalOnWebApplication
@ConditionalOnProperty(
        prefix = "letool.security",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
@EnableMethodSecurity
public class SecurityAutoConfiguration {

    private static final Logger log =
            LoggerFactory.getLogger(SecurityAutoConfiguration.class);

    /**
     * 注册可供登录接口签发 AccessToken 和 RefreshToken 的提供器。
     *
     * <p>只有显式配置 JWT 密钥时才创建；只使用外部授权服务器和自定义
     * {@link JwtDecoder} 的应用无需提供本地签发能力。</p>
     *
     * @param properties 安全配置
     * @return JWT 令牌提供器
     */
    @Bean
    @ConditionalOnMissingBean(JwtTokenProvider.class)
    @ConditionalOnProperty(prefix = "letool.security.jwt", name = "secret")
    public JwtTokenProvider jwtTokenProvider(SecurityProperties properties) {
        return new JwtTokenProvider(properties);
    }

    /**
     * 注册基于 HMAC-SHA256 的 Resource Server JWT 解码器。
     *
     * <p>解码器同时校验签发者、时间窗口和 {@code token_type=access}，
     * 因而刷新令牌无法进入业务资源。用户提供自己的 {@link JwtDecoder} 时自动退让。</p>
     *
     * @param properties 安全配置
     * @return Spring Security JWT 解码器
     */
    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder jwtDecoder(SecurityProperties properties) {
        SecurityProperties.Jwt jwt = properties.getJwt();
        SecretKey secretKey = JwtTokenProvider.createSecretKey(jwt);
        if (jwt.getIssuer() == null || jwt.getIssuer().isBlank()) {
            throw SecurityException.configurationInvalid("jwt.issuer");
        }

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<Jwt> issuerValidator =
                JwtValidators.createDefaultWithIssuer(jwt.getIssuer().trim());
        OAuth2TokenValidator<Jwt> accessTokenValidator =
                this::validateAccessTokenType;
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                issuerValidator,
                accessTokenValidator
        ));
        return decoder;
    }

    /**
     * 注册 JWT 到 Letool 登录用户和 Spring authorities 的转换器。
     *
     * @return 默认 JWT 认证转换器
     */
    @Bean("letoolJwtAuthenticationConverter")
    @ConditionalOnMissingBean(name = "letoolJwtAuthenticationConverter")
    public Converter<Jwt, AbstractAuthenticationToken>
    letoolJwtAuthenticationConverter() {
        return new SecurityJwtAuthenticationConverter();
    }

    /**
     * 注册 JWT 未认证响应处理器。
     *
     * @return 未认证响应处理器
     */
    @Bean
    @ConditionalOnMissingBean(SecurityExceptionHandler.class)
    public SecurityExceptionHandler securityExceptionHandler() {
        return new SecurityExceptionHandler();
    }

    /**
     * 注册权限不足响应处理器。
     *
     * @return 权限不足响应处理器
     */
    @Bean
    @ConditionalOnMissingBean(AccessDeniedExceptionHandler.class)
    public AccessDeniedExceptionHandler accessDeniedExceptionHandler() {
        return new AccessDeniedExceptionHandler();
    }

    /**
     * 注册 Letool 便捷权限注解切面。
     *
     * @return 权限注解切面
     */
    @Bean
    @ConditionalOnMissingBean(SecurityAnnotationAspect.class)
    public SecurityAnnotationAspect securityAnnotationAspect() {
        return new SecurityAnnotationAspect();
    }

    /**
     * 构建无状态 Resource Server 安全过滤链。
     *
     * @param http Spring Security HTTP 配置器
     * @param jwtDecoder JWT 解码器
     * @param authenticationConverter JWT 认证转换器
     * @param authEntryPoint 未认证处理器
     * @param accessDeniedHandler 权限不足处理器
     * @param properties 安全配置
     * @return 安全过滤链
     * @throws Exception 当 Spring Security 构建过滤链失败时抛出
     */
    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            @Qualifier("letoolJwtAuthenticationConverter")
            Converter<Jwt, AbstractAuthenticationToken> authenticationConverter,
            SecurityExceptionHandler authEntryPoint,
            AccessDeniedExceptionHandler accessDeniedHandler,
            SecurityProperties properties) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(
                        corsConfigurationSource(properties)
                ))
                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS
                ))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> {
                    // 与 Spring MVC 统一使用 PathPattern 语法，避免依赖已废弃的 Ant 路径匹配器。
                    PathPatternRequestMatcher.Builder matcherBuilder =
                            PathPatternRequestMatcher.withDefaults();
                    for (String path : validatedExcludePaths(properties)) {
                        authorize.requestMatchers(
                                matcherBuilder.matcher(path)
                        ).permitAll();
                    }
                    authorize.anyRequest().authenticated();
                })
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationEntryPoint(authEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(
                                        authenticationConverter
                                )));

        log.info("Security Resource Server initialized, auth mode: {}",
                properties.getAuthMode());
        return http.build();
    }

    /**
     * 校验访问令牌类型。
     *
     * @param jwt 已完成签名和标准 Claim 解码的 JWT
     * @return OAuth2 校验结果
     */
    private OAuth2TokenValidatorResult validateAccessTokenType(Jwt jwt) {
        String tokenType = jwt.getClaimAsString(
                JwtTokenProvider.TOKEN_TYPE_CLAIM
        );
        if (JwtTokenProvider.ACCESS_TOKEN_TYPE.equals(tokenType)) {
            return OAuth2TokenValidatorResult.success();
        }
        OAuth2Error error = new OAuth2Error(
                OAuth2ErrorCodes.INVALID_TOKEN,
                "Token is not an access token",
                null
        );
        return OAuth2TokenValidatorResult.failure(error);
    }

    /**
     * 获取并校验公开路径。
     *
     * @param properties 安全配置
     * @return 去除首尾空白后的路径列表
     */
    private List<String> validatedExcludePaths(SecurityProperties properties) {
        return properties.getExcludePaths().stream()
                .map(path -> {
                    if (path == null || path.isBlank()) {
                        throw SecurityException.configurationInvalid(
                                "excludePaths"
                        );
                    }
                    return path.trim();
                })
                .distinct()
                .toList();
    }

    /**
     * 根据配置构建 CORS 配置源。
     *
     * @param properties 安全配置
     * @return CORS 配置源；关闭 CORS 时每次返回 {@code null} 配置
     */
    private CorsConfigurationSource corsConfigurationSource(
            SecurityProperties properties) {
        SecurityProperties.Cors cors = properties.getCors();
        if (!cors.isEnabled()) {
            return request -> null;
        }

        List<String> origins = splitRequired(
                cors.getAllowedOrigins(),
                "cors.allowedOrigins"
        );
        if (cors.isAllowCredentials() && origins.contains("*")) {
            throw SecurityException.configurationInvalid(
                    "cors.allowedOrigins"
            );
        }
        if (cors.getMaxAge() < 0) {
            throw SecurityException.configurationInvalid("cors.maxAge");
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(origins);
        configuration.setAllowedMethods(splitRequired(
                cors.getAllowedMethods(),
                "cors.allowedMethods"
        ));
        configuration.setAllowedHeaders(splitRequired(
                cors.getAllowedHeaders(),
                "cors.allowedHeaders"
        ));
        configuration.setAllowCredentials(cors.isAllowCredentials());
        configuration.setMaxAge(cors.getMaxAge());

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * 拆分并校验逗号分隔配置。
     *
     * @param value 配置文本
     * @param field 配置字段名
     * @return 非空配置项列表
     */
    private List<String> splitRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw SecurityException.configurationInvalid(field);
        }
        List<String> values = Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .distinct()
                .toList();
        if (values.isEmpty()) {
            throw SecurityException.configurationInvalid(field);
        }
        return values;
    }
}
