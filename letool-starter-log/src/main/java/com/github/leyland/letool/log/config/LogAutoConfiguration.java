package com.github.leyland.letool.log.config;

import com.github.leyland.letool.log.aspect.AuditLogAspect;
import com.github.leyland.letool.log.aspect.MethodLogAspect;
import com.github.leyland.letool.log.audit.AuditContextProvider;
import com.github.leyland.letool.log.audit.AuditLogService;
import com.github.leyland.letool.log.audit.ServletAuditContextProvider;
import com.github.leyland.letool.log.audit.Slf4jAuditLogService;
import com.github.leyland.letool.log.trace.TraceIdFilter;
import com.github.leyland.letool.log.web.WebLogFilter;
import com.github.leyland.letool.tool.json.Fastjson2JsonCodec;
import com.github.leyland.letool.tool.json.JsonCodec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 日志模块自动配置。
 *
 * <p>日志 Starter 默认提供请求链路、方法日志、Web 日志和审计日志能力。
 * 各项能力可以独立关闭，业务应用声明的同类型基础设施始终优先。</p>
 *
 * <h2>注册策略</h2>
 * <ul>
 *   <li><b>请求链路</b>：仅在 {@code letool.log.trace.enabled=true} 时注册。</li>
 *   <li><b>方法日志</b>：仅在 AspectJ 可用时注册。</li>
 *   <li><b>Web 日志</b>：仅在 Servlet Web 应用中注册请求过滤器。</li>
 *   <li><b>审计日志</b>：独立于请求链路和 Web 日志注册。</li>
 * </ul>
 *
 * <p>线程上下文传播由线程模块或业务自定义 {@code TaskDecorator} 负责，
 * 日志模块不注册线程基础设施，避免多个 Starter 同时启用时产生重复 Bean。</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(LogProperties.class)
@ConditionalOnProperty(prefix = "letool.log", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LogAutoConfiguration {

    /**
     * 在请求过滤链前部注册 TraceId Servlet 过滤器。
     *
     * @param properties 日志配置属性
     * @return TraceId 过滤器注册 Bean
     */
    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnProperty(prefix = "letool.log.trace", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(name = "traceIdFilter")
    public FilterRegistrationBean<TraceIdFilter> traceIdFilter(LogProperties properties) {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TraceIdFilter(properties));
        registration.addUrlPatterns("/*");
        // 尽早建立 TraceId，确保后续过滤器和拦截器都能读取同一份链路标识。
        registration.setOrder(Integer.MIN_VALUE + 100);
        return registration;
    }

    /**
     * 将基于 AOP 的方法日志能力隔离在 AspectJ 类路径条件之后。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {
            "org.aspectj.lang.ProceedingJoinPoint",
            "org.aspectj.lang.annotation.Aspect"
    })
    static class MethodLogAspectConfiguration {

        /**
         * 为 {@code @MethodLog} 注册方法日志切面。
         *
         * @param jsonCodecProvider 用户自定义 JSON 编解码器提供器
         * @param properties 日志模块配置
         * @return 方法日志切面
         */
        @Bean
        @ConditionalOnMissingBean(type = "com.github.leyland.letool.log.aspect.MethodLogAspect")
        public MethodLogAspect methodLogAspect(
                ObjectProvider<JsonCodec> jsonCodecProvider,
                LogProperties properties) {
            JsonCodec jsonCodec = jsonCodecProvider.getIfAvailable(
                    Fastjson2JsonCodec::createDefault);
            return new MethodLogAspect(jsonCodec, properties);
        }
    }

    /**
     * 将注解式审计能力隔离在 AspectJ 类路径和审计开关之后。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {
            "org.aspectj.lang.ProceedingJoinPoint",
            "org.aspectj.lang.annotation.Aspect"
    })
    @ConditionalOnProperty(
            prefix = "letool.log.audit",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    static class AuditLogAspectConfiguration {

        /**
         * 为 {@code @AuditLog} 注册审计日志切面。
         *
         * @param auditLogService 审计事件输出服务
         * @param jsonCodecProvider 用户自定义 JSON 编解码器提供器
         * @param contextProvider 当前审计上下文提供器
         * @return 审计日志切面
         */
        @Bean
        @ConditionalOnMissingBean(AuditLogAspect.class)
        public AuditLogAspect auditLogAspect(
                AuditLogService auditLogService,
                ObjectProvider<JsonCodec> jsonCodecProvider,
                ObjectProvider<AuditContextProvider> contextProvider) {
            JsonCodec jsonCodec = jsonCodecProvider.getIfAvailable(
                    Fastjson2JsonCodec::createDefault);
            AuditContextProvider provider = contextProvider.getIfAvailable(
                    AuditContextProvider::empty);
            return new AuditLogAspect(auditLogService, jsonCodec, provider);
        }
    }

    /**
     * 将 Servlet 请求日志能力隔离在 Servlet Web 类路径条件之后。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {
            "jakarta.servlet.http.HttpServletRequest",
            "org.springframework.web.filter.OncePerRequestFilter"
    })
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(
            prefix = "letool.log.web-log",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    static class WebLogFilterConfiguration {

        /**
         * 创建可由业务应用按类型替换的 Web 请求日志过滤器。
         *
         * @param properties 日志配置属性
         * @return Web 请求日志过滤器
         */
        @Bean
        @ConditionalOnMissingBean(
                value = WebLogFilter.class,
                name = "webLogFilterRegistration")
        public WebLogFilter webLogFilter(LogProperties properties) {
            return new WebLogFilter(properties);
        }

        /**
         * 在 TraceId 过滤器之后注册 Web 请求日志过滤器。
         *
         * @param webLogFilter Web 请求日志过滤器
         * @return Web 请求日志过滤器注册 Bean
         */
        @Bean
        @ConditionalOnMissingBean(name = "webLogFilterRegistration")
        public FilterRegistrationBean<WebLogFilter> webLogFilterRegistration(
                WebLogFilter webLogFilter) {
            FilterRegistrationBean<WebLogFilter> registration = new FilterRegistrationBean<>();
            registration.setFilter(webLogFilter);
            registration.addUrlPatterns("/*");
            registration.setAsyncSupported(true);
            // TraceIdFilter 的顺序为 MIN_VALUE + 100，确保请求日志能读取已经建立的 TraceId。
            registration.setOrder(Integer.MIN_VALUE + 200);
            return registration;
        }
    }

    /**
     * 将审计日志相关 Bean 隔离在审计功能开关之后。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "letool.log.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class AuditLogConfiguration {

        /**
         * 注册向专用 SLF4J Logger 输出结构化 JSON 的默认审计服务。
         *
         * @param jsonCodecProvider 用户自定义 JSON 编解码器提供器
         * @return 审计日志服务
         */
        @Bean
        @ConditionalOnMissingBean(AuditLogService.class)
        public AuditLogService auditLogService(
                ObjectProvider<JsonCodec> jsonCodecProvider) {
            JsonCodec jsonCodec = jsonCodecProvider.getIfAvailable(
                    Fastjson2JsonCodec::createDefault);
            return new Slf4jAuditLogService(jsonCodec);
        }
    }

    /**
     * 在 Servlet Web 应用中提供默认审计上下文。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {
            "jakarta.servlet.http.HttpServletRequest",
            "org.springframework.web.context.request.RequestContextHolder"
    })
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(
            prefix = "letool.log.audit",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    static class ServletAuditContextConfiguration {

        /**
         * 注册基于标准 Servlet 请求信息的审计上下文提供器。
         *
         * @return Servlet 审计上下文提供器
         */
        @Bean
        @ConditionalOnMissingBean(AuditContextProvider.class)
        public ServletAuditContextProvider servletAuditContextProvider() {
            return new ServletAuditContextProvider();
        }
    }
}
