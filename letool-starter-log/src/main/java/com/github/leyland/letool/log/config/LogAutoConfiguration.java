package com.github.leyland.letool.log.config;

import com.github.leyland.letool.log.aspect.MethodLogAspect;
import com.github.leyland.letool.log.aspect.WebLogAspect;
import com.github.leyland.letool.log.audit.AuditLogEvent;
import com.github.leyland.letool.log.audit.AuditLogService;
import com.github.leyland.letool.log.audit.DefaultAuditLogProcessor;
import com.github.leyland.letool.log.store.FileLogStore;
import com.github.leyland.letool.log.store.LogRecordStore;
import com.github.leyland.letool.log.store.MemoryLogStore;
import com.github.leyland.letool.log.trace.MdcTaskDecorator;
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
import org.springframework.core.task.TaskDecorator;

/**
 * Log module auto-configuration.
 *
 * <p>The log starter follows the toolkit/starter contract: defaults are enabled
 * when useful, each feature can be disabled independently, and application-defined
 * beans always win.</p>
 *
 * <h3>Registration strategy</h3>
 * <ul>
 *   <li><b>Trace</b>: registered only when {@code letool.log.trace.enabled=true}.</li>
 *   <li><b>Method log</b>: registered when AspectJ is available.</li>
 *   <li><b>Web log</b>: registered only in a servlet web application.</li>
 *   <li><b>Audit log</b>: registered independently from trace/web logging.</li>
 * </ul>
 */
@AutoConfiguration
@EnableConfigurationProperties(LogProperties.class)
@ConditionalOnProperty(prefix = "letool.log", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LogAutoConfiguration {

    /**
     * Registers the TraceId servlet filter at the front of the request chain.
     */
    @Bean
    @ConditionalOnWebApplication
    @ConditionalOnProperty(prefix = "letool.log.trace", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(name = "traceIdFilter")
    public FilterRegistrationBean<TraceIdFilter> traceIdFilter(LogProperties properties) {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TraceIdFilter(properties));
        registration.addUrlPatterns("/*");
        // Keep TraceId available before most application filters and interceptors.
        registration.setOrder(Integer.MIN_VALUE + 100);
        return registration;
    }

    /**
     * Registers an MDC task decorator for propagating trace context to worker threads.
     */
    @Bean
    @ConditionalOnProperty(prefix = "letool.log.trace", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(value = TaskDecorator.class, name = "mdcTaskDecorator")
    public TaskDecorator mdcTaskDecorator() {
        return new MdcTaskDecorator();
    }

    /**
     * Groups AOP-based method logging behind an AspectJ classpath guard.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {
            "org.aspectj.lang.ProceedingJoinPoint",
            "org.aspectj.lang.annotation.Aspect"
    })
    static class MethodLogAspectConfiguration {

        /**
         * Registers the method logging aspect for {@code @MethodLog}.
         */
        @Bean
        @ConditionalOnMissingBean(type = "com.github.leyland.letool.log.aspect.MethodLogAspect")
        public MethodLogAspect methodLogAspect() {
            return new MethodLogAspect();
        }
    }

    /**
     * Groups servlet request logging behind both AspectJ and Servlet classpath guards.
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
         * Registers request logging for Spring MVC controllers.
         */
        @Bean
        @ConditionalOnProperty(prefix = "letool.log.web-log", name = "enabled", havingValue = "true", matchIfMissing = true)
        @ConditionalOnMissingBean(type = "com.github.leyland.letool.log.aspect.WebLogAspect")
        public WebLogAspect webLogAspect(LogProperties properties) {
            return new WebLogAspect(properties);
        }
    }

    /**
     * Groups audit logging beans behind the audit feature switch.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "letool.log.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class AuditLogConfiguration {

        /**
         * Registers the audit log service and wires it to the available audit store.
         */
        @Bean
        @ConditionalOnMissingBean(AuditLogService.class)
        public AuditLogService auditLogService(ObjectProvider<LogRecordStore<AuditLogEvent>> auditLogStoreProvider) {
            LogRecordStore<AuditLogEvent> store = auditLogStoreProvider.getIfAvailable(() -> new MemoryLogStore<>(10000));
            return new DefaultAuditLogProcessor(store);
        }

        /**
         * Registers an in-memory audit store for lightweight local applications and tests.
         */
        @Bean
        @ConditionalOnProperty(prefix = "letool.log.audit", name = "storage", havingValue = "memory")
        @ConditionalOnMissingBean(LogRecordStore.class)
        public LogRecordStore<AuditLogEvent> auditMemoryLogStore() {
            return new MemoryLogStore<>(10000);
        }

        /**
         * Registers a JSON Lines audit file store when file storage is selected.
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
