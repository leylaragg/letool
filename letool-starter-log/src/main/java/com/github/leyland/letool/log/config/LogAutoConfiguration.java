package com.github.leyland.letool.log.config;

import com.github.leyland.letool.log.aspect.MethodLogAspect;
import com.github.leyland.letool.log.aspect.WebLogAspect;
import com.github.leyland.letool.log.audit.AuditLogEvent;
import com.github.leyland.letool.log.audit.AuditLogService;
import com.github.leyland.letool.log.audit.DefaultAuditLogProcessor;
import com.github.leyland.letool.log.store.FileLogStore;
import com.github.leyland.letool.log.store.LogRecordStore;
import com.github.leyland.letool.log.store.MemoryLogStore;
import com.github.leyland.letool.log.trace.TraceIdFilter;
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
 *   <li><b>Web 日志</b>：仅在 Servlet Web 应用中注册。</li>
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
         * @return 方法日志切面
         */
        @Bean
        @ConditionalOnMissingBean(type = "com.github.leyland.letool.log.aspect.MethodLogAspect")
        public MethodLogAspect methodLogAspect() {
            return new MethodLogAspect();
        }
    }

    /**
     * 将 Servlet 请求日志能力隔离在 AspectJ 和 Servlet 类路径条件之后。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {
            "org.aspectj.lang.ProceedingJoinPoint",
            "org.aspectj.lang.annotation.Aspect",
            "jakarta.servlet.http.HttpServletRequest",
            "org.springframework.web.context.request.RequestContextHolder"
    })
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class WebLogAspectConfiguration {

        /**
         * 为 Spring MVC 控制器注册请求日志切面。
         *
         * @param properties 日志配置属性
         * @return Web 请求日志切面
         */
        @Bean
        @ConditionalOnProperty(prefix = "letool.log.web-log", name = "enabled", havingValue = "true", matchIfMissing = true)
        @ConditionalOnMissingBean(type = "com.github.leyland.letool.log.aspect.WebLogAspect")
        public WebLogAspect webLogAspect(LogProperties properties) {
            return new WebLogAspect(properties);
        }
    }

    /**
     * 将审计日志相关 Bean 隔离在审计功能开关之后。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "letool.log.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class AuditLogConfiguration {

        /**
         * 注册审计日志服务，并连接容器中可用的审计日志存储。
         *
         * @param auditLogStoreProvider 审计日志存储提供器
         * @return 审计日志服务
         */
        @Bean
        @ConditionalOnMissingBean(AuditLogService.class)
        public AuditLogService auditLogService(ObjectProvider<LogRecordStore<AuditLogEvent>> auditLogStoreProvider) {
            LogRecordStore<AuditLogEvent> store = auditLogStoreProvider.getIfAvailable(() -> new MemoryLogStore<>(10000));
            return new DefaultAuditLogProcessor(store);
        }

        /**
         * 为轻量应用和测试环境注册内存审计日志存储。
         *
         * @return 内存审计日志存储
         */
        @Bean
        @ConditionalOnProperty(prefix = "letool.log.audit", name = "storage", havingValue = "memory")
        @ConditionalOnMissingBean(LogRecordStore.class)
        public LogRecordStore<AuditLogEvent> auditMemoryLogStore() {
            return new MemoryLogStore<>(10000);
        }

        /**
         * 选择文件存储时注册 JSON Lines 审计日志存储。
         *
         * @param properties 日志配置属性
         * @return 文件审计日志存储
         */
        @Bean
        @ConditionalOnProperty(prefix = "letool.log.audit", name = "storage", havingValue = "file", matchIfMissing = true)
        @ConditionalOnMissingBean(LogRecordStore.class)
        public LogRecordStore<AuditLogEvent> auditFileLogStore(LogProperties properties) {
            String baseDir = System.getProperty("user.home") + "/.letool/logs/audit-log";
            return new FileLogStore<>(baseDir, AuditLogEvent.class);
        }
    }
}
