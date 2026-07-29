package com.github.leyland.letool.web.config;

import com.github.leyland.letool.exception.config.ExceptionAutoConfiguration;
import com.github.leyland.letool.exception.message.MessageResolver;
import com.github.leyland.letool.web.advice.GlobalExceptionHandler;
import com.github.leyland.letool.web.advice.ResponseWrapperAdvice;
import com.github.leyland.letool.web.filter.RepeatableRequestFilter;
import com.github.leyland.letool.web.filter.SqlInjectionFilter;
import com.github.leyland.letool.web.filter.XssFilter;
import com.github.leyland.letool.web.version.ApiVersionRequestMappingHandlerMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 配置 Servlet Web 基础设施，包括响应包装、过滤器、API 版本路由和带错误码异常处理。
 *
 * <p>带码 {@link GlobalExceptionHandler} 依赖异常框架的 {@link MessageResolver}；
 * 异常框架关闭或解析器不存在时，该处理器会自动退让。此依赖只作用于异常处理器 Bean，
 * 响应增强、过滤器及其他 Web 基础设施仍会独立配置。每个 Bean 同时保留缺失 Bean 条件，
 * 方便应用接管单个组件。</p>
 */
@AutoConfiguration(after = ExceptionAutoConfiguration.class)
@EnableConfigurationProperties(WebProperties.class)
@ConditionalOnClass(name = {
        "org.springframework.web.servlet.DispatcherServlet",
        "org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping",
        "org.springframework.boot.web.servlet.FilterRegistrationBean"
})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "letool.web", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WebAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(WebAutoConfiguration.class);

    /**
     * 仅在国际化消息解析器存在时创建带错误码的 HTTP 异常适配器。
     *
     * @param messageResolver 响应边界使用的异常框架消息解析器
     * @return 默认全局带码异常处理器
     */
    @Bean
    @ConditionalOnBean(MessageResolver.class)
    @ConditionalOnMissingBean(GlobalExceptionHandler.class)
    public GlobalExceptionHandler globalExceptionHandler(MessageResolver messageResolver) {
        return new GlobalExceptionHandler(messageResolver);
    }

    /**
     * 响应体统一包装.
     */
    @Bean
    @ConditionalOnMissingBean(ResponseWrapperAdvice.class)
    public ResponseWrapperAdvice responseBodyAdvice() {
        return new ResponseWrapperAdvice();
    }

    /**
     * 注册 API 版本路由扩展。
     *
     * <p>Spring MVC 默认只根据请求路径、HTTP 方法等条件判断路由是否冲突；
     * 如果多个接口使用相同路径但通过 {@link com.github.leyland.letool.web.version.ApiVersion}
     * 区分版本，默认映射会在启动阶段被判定为映射冲突。这里通过
     * {@link WebMvcRegistrations} 替换默认的 {@link RequestMappingHandlerMapping}，
     * 让版本号成为请求匹配条件的一部分。</p>
     *
     * <p>当业务侧已经提供自定义 {@link WebMvcRegistrations} 时，本配置会自动退让，
     * 避免覆盖用户自己的 MVC 映射扩展。</p>
     *
     * @return 支持 {@code @ApiVersion} 条件匹配的 MVC 注册扩展
     */
    @Bean
    @ConditionalOnMissingBean(WebMvcRegistrations.class)
    public WebMvcRegistrations apiVersionWebMvcRegistrations() {
        return new WebMvcRegistrations() {
            @Override
            public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
                return new ApiVersionRequestMappingHandlerMapping();
            }
        };
    }

    /**
     * XSS 防御过滤器.
     */
    @Bean
    @ConditionalOnProperty(prefix = "letool.web.xss-filter", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(name = "xssFilterRegistration")
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
    @ConditionalOnMissingBean(name = "sqlInjectionFilterRegistration")
    public FilterRegistrationBean<SqlInjectionFilter> sqlInjectionFilterRegistration() {
        FilterRegistrationBean<SqlInjectionFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new SqlInjectionFilter());
        registration.addUrlPatterns("/*");
        // 在 XSS 转义前执行，避免生成的 HTML 实体（如 “&lt;”）被误判为 SQL 分隔符。
        registration.setOrder(-103);
        registration.setName("sqlInjectionFilter");
        log.info("SQL injection filter registered");
        return registration;
    }

    /**
     * 可重复读请求体过滤器.
     */
    @Bean
    @ConditionalOnMissingBean(name = "repeatableRequestFilterRegistration")
    public FilterRegistrationBean<RepeatableRequestFilter> repeatableRequestFilterRegistration() {
        FilterRegistrationBean<RepeatableRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RepeatableRequestFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(-105);
        registration.setName("repeatableRequestFilter");
        return registration;
    }
}
