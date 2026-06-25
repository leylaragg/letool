package com.github.leyland.letool.web.config;

import com.github.leyland.letool.web.advice.GlobalExceptionHandler;
import com.github.leyland.letool.web.advice.ResponseWrapperAdvice;
import com.github.leyland.letool.web.filter.RepeatableRequestFilter;
import com.github.leyland.letool.web.filter.SqlInjectionFilter;
import com.github.leyland.letool.web.filter.XssFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

/**
 * Web 增强自动配置 —— 注册全局异常处理器、响应包装、安全过滤器等.
 *
 * <p>仅在 Web 环境下激活（{@code @ConditionalOnWebApplication}）.</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(WebProperties.class)
@ConditionalOnWebApplication
@ConditionalOnProperty(prefix = "letool.web", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WebAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(WebAutoConfiguration.class);

    /**
     * 全局异常处理器.
     */
    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    /**
     * 响应体统一包装.
     */
    @Bean
    public ResponseWrapperAdvice responseBodyAdvice() {
        return new ResponseWrapperAdvice();
    }

    /**
     * XSS 防御过滤器.
     */
    @Bean
    @ConditionalOnProperty(prefix = "letool.web.xss-filter", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<XssFilter> xssFilterRegistration() {
        FilterRegistrationBean<XssFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new XssFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(-102);
        registration.setName("xssFilter");
        log.info("XSS filter registered");
        return registration;
    }

    /**
     * SQL 注入防御过滤器.
     */
    @Bean
    @ConditionalOnProperty(prefix = "letool.web.sql-injection-filter", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<SqlInjectionFilter> sqlInjectionFilterRegistration() {
        FilterRegistrationBean<SqlInjectionFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new SqlInjectionFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(-101);
        registration.setName("sqlInjectionFilter");
        log.info("SQL injection filter registered");
        return registration;
    }

    /**
     * 可重复读请求体过滤器.
     */
    @Bean
    public FilterRegistrationBean<RepeatableRequestFilter> repeatableRequestFilterRegistration() {
        FilterRegistrationBean<RepeatableRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RepeatableRequestFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(-105);
        registration.setName("repeatableRequestFilter");
        return registration;
    }
}
