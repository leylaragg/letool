package com.github.leyland.letool.security.config;

import com.github.leyland.letool.security.aspect.SecurityAnnotationAspect;
import com.github.leyland.letool.security.filter.JwtAuthenticationFilter;
import com.github.leyland.letool.security.handler.AccessDeniedExceptionHandler;
import com.github.leyland.letool.security.handler.SecurityExceptionHandler;
import com.github.leyland.letool.security.jwt.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
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

@AutoConfiguration
@EnableConfigurationProperties(SecurityProperties.class)
@ConditionalOnWebApplication
@ConditionalOnProperty(prefix = "letool.security", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableMethodSecurity
public class SecurityAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SecurityAutoConfiguration.class);

    @Bean
    public JwtTokenProvider jwtTokenProvider(SecurityProperties properties) {
        return new JwtTokenProvider(properties);
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
                                                            SecurityProperties properties) {
        return new JwtAuthenticationFilter(jwtTokenProvider, properties);
    }

    @Bean
    public SecurityExceptionHandler securityExceptionHandler() {
        return new SecurityExceptionHandler();
    }

    @Bean
    public AccessDeniedExceptionHandler accessDeniedExceptionHandler() {
        return new AccessDeniedExceptionHandler();
    }

    @Bean
    public SecurityAnnotationAspect securityAnnotationAspect() {
        return new SecurityAnnotationAspect();
    }

    @Bean
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
