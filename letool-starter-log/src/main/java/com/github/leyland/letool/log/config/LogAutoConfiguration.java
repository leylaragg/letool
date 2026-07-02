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
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskDecorator;

/**
 * 日志模块自动配置 —— 按条件注册 TraceId 过滤器、MDC 装饰器、日志切面、审计日志服务.
 *
 * <h3>注册策略</h3>
 * <ul>
 *   <li><b>TraceIdFilter</b> —— 仅在 Web 环境注册，Order = Integer.MIN_VALUE + 100，确保最先执行</li>
 *   <li><b>MdcTaskDecorator</b> —— 无条件注册，所有 ThreadPoolTaskExecutor 自动装配</li>
 *   <li><b>MethodLogAspect</b> —— 仅在 classpath 包含 AspectJ 时注册</li>
 *   <li><b>WebLogAspect</b> —— 仅在 Web 环境注册</li>
 *   <li><b>AuditLogService</b> —— 内存存储始终注册，文件存储按需注册</li>
 * </ul>
 */
@AutoConfiguration
@EnableConfigurationProperties(LogProperties.class)
@ConditionalOnProperty(prefix = "letool.log.trace", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LogAutoConfiguration {

    /**
     * 注册 TraceId 过滤器 —— 在 Web 请求链的最前端注入 TraceId，响应返回时写入 Header.
     */
    @Bean
    @ConditionalOnWebApplication
    public FilterRegistrationBean<TraceIdFilter> traceIdFilter(LogProperties properties) {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TraceIdFilter(properties));
        registration.addUrlPatterns("/*");
        // 优先级设为最高，确保 TraceId 在其他 Filter / Interceptor 之前可用
        registration.setOrder(Integer.MIN_VALUE + 100);
        return registration;
    }

    /**
     * 注册 MDC 任务装饰器 —— 线程池执行任务时自动传递父线程的 MDC 上下文到子线程.
     */
    @Bean
    @ConditionalOnMissingBean(value = TaskDecorator.class, name = "mdcTaskDecorator")
    public TaskDecorator mdcTaskDecorator() {
        return new MdcTaskDecorator();
    }

    /**
     * 注册方法日志切面 —— 拦截 @MethodLog 注解.
     */
    @Bean
    @ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
    public MethodLogAspect methodLogAspect() {
        return new MethodLogAspect();
    }

    /**
     * 注册 Web 日志切面 —— 拦截所有 Controller 请求.
     */
    @Bean
    @ConditionalOnWebApplication
    public WebLogAspect webLogAspect(LogProperties properties) {
        return new WebLogAspect(properties);
    }

    /**
     * 注册审计日志服务 —— 内存环形缓冲区（默认 10000 条），重启丢失.
     */
    @Bean
    @ConditionalOnProperty(prefix = "letool.log.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    public AuditLogService auditLogService() {
        LogRecordStore<AuditLogEvent> memoryStore = new MemoryLogStore<>(10000);
        return new DefaultAuditLogProcessor(memoryStore);
    }

    /**
     * 注册审计日志文件存储 —— JSON Lines 格式持久化，按日期滚动.
     */
    @Bean
    @ConditionalOnProperty(prefix = "letool.log.audit", name = "enabled", havingValue = "true", matchIfMissing = true)
    public LogRecordStore<AuditLogEvent> auditFileLogStore(LogProperties properties) {
        String baseDir = System.getProperty("user.home") + "/.letool/logs/audit-log";
        return new FileLogStore<>(baseDir, AuditLogEvent.class);
    }
}
