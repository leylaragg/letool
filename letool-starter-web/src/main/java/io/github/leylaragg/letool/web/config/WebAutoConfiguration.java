package io.github.leylaragg.letool.web.config;

import io.github.leylaragg.letool.exception.config.ExceptionAutoConfiguration;
import io.github.leylaragg.letool.exception.message.MessageResolver;
import io.github.leylaragg.letool.web.advice.GlobalExceptionHandler;
import io.github.leylaragg.letool.web.advice.ResponseWrapperAdvice;
import io.github.leylaragg.letool.web.filter.RepeatableRequestFilter;
import io.github.leylaragg.letool.web.version.ApiVersionRequestMappingHandlerMapping;
import org.springframework.beans.factory.annotation.Qualifier;
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
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 配置 Servlet Web 基础设施，包括统一响应、异常协议、API 版本路由和受限请求体重复读取。
 *
 * <p>带码 {@link GlobalExceptionHandler} 依赖异常框架的 {@link MessageResolver}；
 * 异常框架关闭或解析器不存在时，该处理器会自动退让。此依赖只作用于异常处理器 Bean，
 * 响应增强、版本路由及其他 Web 基础设施仍会独立配置。每个 Bean 同时保留缺失 Bean 条件，
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
     * 创建遵循排除路径配置的统一响应包装器。
     *
     * @param properties Web 模块配置属性
     * @return 默认统一响应包装器
    */
    @Bean
    @ConditionalOnProperty(
            prefix = "letool.web.response-wrapper",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean(ResponseWrapperAdvice.class)
    public ResponseWrapperAdvice responseBodyAdvice(WebProperties properties) {
        return new ResponseWrapperAdvice(properties.getResponseWrapper().getExcludePaths());
    }

    /**
     * 注册 API 版本路由扩展。
     *
     * <p>Spring MVC 默认只根据请求路径、HTTP 方法等条件判断路由是否冲突；
     * 如果多个接口使用相同路径但通过 {@link io.github.leylaragg.letool.web.version.ApiVersion}
     * 区分版本，默认映射会在启动阶段被判定为映射冲突。这里通过
     * {@link WebMvcRegistrations} 替换默认的 {@link RequestMappingHandlerMapping}，
     * 让版本号成为请求匹配条件的一部分。</p>
     *
     * <p>当业务侧已经提供自定义 {@link WebMvcRegistrations} 时，本配置会自动退让，
     * 避免覆盖用户自己的 MVC 映射扩展。</p>
     *
     * @param properties Web 模块配置属性
     * @return 支持 {@code @ApiVersion} 条件匹配的 MVC 注册扩展
    */
    @Bean
    @ConditionalOnProperty(
            prefix = "letool.web.api-version",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnMissingBean(WebMvcRegistrations.class)
    public WebMvcRegistrations apiVersionWebMvcRegistrations(WebProperties properties) {
        WebProperties.ApiVersion apiVersion = properties.getApiVersion();
        return new WebMvcRegistrations() {
            @Override
            public RequestMappingHandlerMapping getRequestMappingHandlerMapping() {
                return new ApiVersionRequestMappingHandlerMapping(
                        apiVersion.getHeaderName(),
                        apiVersion.getParameterName());
            }
        };
    }

    /**
     * 注册受大小、路径和媒体类型限制的可重复读请求体过滤器。
     *
     * @param properties Web 模块配置属性
     * @param exceptionResolver Spring MVC 统一异常解析器
     * @return 可重复读请求体过滤器注册对象
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "letool.web.repeatable-request",
            name = "enabled",
            havingValue = "true")
    @ConditionalOnMissingBean(name = "repeatableRequestFilterRegistration")
    public FilterRegistrationBean<RepeatableRequestFilter> repeatableRequestFilterRegistration(
            WebProperties properties,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        WebProperties.RepeatableRequest repeatableRequest = properties.getRepeatableRequest();
        FilterRegistrationBean<RepeatableRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RepeatableRequestFilter(
                repeatableRequest.getMaxBodySize().toBytes(),
                repeatableRequest.getExcludePaths(),
                repeatableRequest.getIncludeMediaTypes(),
                exceptionResolver));
        registration.addUrlPatterns("/*");
        registration.setOrder(-105);
        registration.setName("repeatableRequestFilter");
        return registration;
    }
}
