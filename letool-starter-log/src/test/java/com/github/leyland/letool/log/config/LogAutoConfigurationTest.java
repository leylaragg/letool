package com.github.leyland.letool.log.config;

import com.github.leyland.letool.log.aspect.MethodLogAspect;
import com.github.leyland.letool.log.aspect.WebLogAspect;
import com.github.leyland.letool.log.audit.AuditLogEvent;
import com.github.leyland.letool.log.audit.AuditLogService;
import com.github.leyland.letool.log.store.LogRecordStore;
import com.github.leyland.letool.log.trace.TraceIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the Spring Boot starter contract for {@link LogAutoConfiguration}.
 *
 * <p>The log starter is a cross-cutting toolkit module, so it must allow
 * applications to disable each feature independently and replace default beans
 * without bean-definition conflicts.</p>
 */
class LogAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LogAutoConfiguration.class))
            .withPropertyValues("spring.main.allow-bean-definition-overriding=false");

    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LogAutoConfiguration.class))
            .withPropertyValues("spring.main.allow-bean-definition-overriding=false");

    /**
     * 非 Web 应用默认只装配通用日志能力，不应创建 Servlet 过滤器或 Web 日志切面。
     */
    @Test
    void shouldCreateNonWebLogInfrastructureBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(LogProperties.class);
            assertThat(context).hasSingleBean(AuditLogService.class);
            assertThat(context).hasSingleBean(LogRecordStore.class);
            assertThat(context).hasSingleBean(MethodLogAspect.class);
            assertThat(context).hasBean("mdcTaskDecorator");
            assertThat(context).doesNotHaveBean("traceIdFilter");
            assertThat(context).doesNotHaveBean(WebLogAspect.class);
        });
    }

    /**
     * Web 应用默认应额外创建 TraceId 过滤器和 Web 请求日志切面。
     */
    @Test
    void shouldCreateWebLogInfrastructureBeansInWebApplication() {
        webContextRunner.run(context -> {
            assertThat(context).hasSingleBean(AuditLogService.class);
            assertThat(context).hasSingleBean(MethodLogAspect.class);
            assertThat(context).hasSingleBean(WebLogAspect.class);
            assertThat(context).hasBean("traceIdFilter");
        });
    }

    /**
     * 总开关关闭时，log starter 不应创建任何日志基础设施 Bean。
     */
    @Test
    void shouldDisableLogAutoConfiguration() {
        contextRunner
                .withPropertyValues("letool.log.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(LogProperties.class);
                    assertThat(context).doesNotHaveBean(AuditLogService.class);
                    assertThat(context).doesNotHaveBean(MethodLogAspect.class);
                    assertThat(context).doesNotHaveBean("mdcTaskDecorator");
                });
    }

    /**
     * 没有 AspectJ 时，审计和 MDC 仍可用，但方法日志/Web 日志切面不应加载。
     */
    @Test
    void shouldStartWithoutAspectsWhenAspectJClasspathIsMissing() {
        webContextRunner
                .withClassLoader(new FilteredClassLoader("org.aspectj"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AuditLogService.class);
                    assertThat(context).hasBean("traceIdFilter");
                    assertThat(context).doesNotHaveBean(MethodLogAspect.class);
                    assertThat(context).doesNotHaveBean(WebLogAspect.class);
                });
    }

    /**
     * 没有 Web/Servlet classpath 时，log starter 应保留非 Web 日志能力并跳过 Web 组件。
     */
    @Test
    void shouldStartWithoutWebAndServletClasspath() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("org.springframework.web", "jakarta.servlet"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AuditLogService.class);
                    assertThat(context).doesNotHaveBean("traceIdFilter");
                    assertThat(context).doesNotHaveBean(WebLogAspect.class);
                });
    }

    /**
     * 关闭 Web 请求日志时，只应跳过 WebLogAspect，不影响 TraceId 过滤器。
     */
    @Test
    void shouldDisableWebLogAspectOnly() {
        webContextRunner
                .withPropertyValues("letool.log.web-log.enabled=false")
                .run(context -> {
                    assertThat(context).hasBean("traceIdFilter");
                    assertThat(context).doesNotHaveBean(WebLogAspect.class);
                    assertThat(context).hasSingleBean(AuditLogService.class);
                });
    }

    /**
     * 关闭审计时，只应跳过审计服务和审计存储，不影响 trace/MDC。
     */
    @Test
    void shouldDisableAuditOnly() {
        contextRunner
                .withPropertyValues("letool.log.audit.enabled=false")
                .run(context -> {
                    assertThat(context).hasBean("mdcTaskDecorator");
                    assertThat(context).doesNotHaveBean(AuditLogService.class);
                    assertThat(context).doesNotHaveBean(LogRecordStore.class);
                });
    }

    /**
     * Disabling trace must not disable unrelated audit infrastructure.
     */
    @Test
    void shouldKeepAuditEnabledWhenTraceIsDisabled() {
        contextRunner
                .withPropertyValues("letool.log.trace.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(AuditLogService.class);
                    assertThat(context).hasSingleBean(LogRecordStore.class);
                    assertThat(context).doesNotHaveBean("mdcTaskDecorator");
                    assertThat(context).hasSingleBean(MethodLogAspect.class);
                });
    }

    /**
     * User-defined log infrastructure should replace starter defaults cleanly.
     */
    @Test
    void shouldBackOffWhenUserProvidesLogInfrastructureBeans() {
        webContextRunner
                .withUserConfiguration(UserLogConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MethodLogAspect.class);
                    assertThat(context).hasSingleBean(WebLogAspect.class);
                    assertThat(context).hasSingleBean(AuditLogService.class);
                    assertThat(context).hasSingleBean(LogRecordStore.class);
                    assertThat(context).hasBean("traceIdFilter");
                    assertThat(context.getBean(MethodLogAspect.class))
                            .isSameAs(context.getBean("userMethodLogAspect"));
                    assertThat(context.getBean(WebLogAspect.class))
                            .isSameAs(context.getBean("userWebLogAspect"));
                    assertThat(context.getBean(AuditLogService.class))
                            .isSameAs(context.getBean("userAuditLogService"));
                    assertThat(context.getBean(LogRecordStore.class))
                            .isSameAs(context.getBean("userAuditLogStore"));
                    assertThat(context.getBean("traceIdFilter"))
                            .isSameAs(context.getBean("userTraceIdFilter"));
                });
    }

    /**
     * Simulates an application replacing all log starter infrastructure beans.
     */
    @Configuration(proxyBeanMethods = false)
    static class UserLogConfiguration {

        @Bean({"traceIdFilter", "userTraceIdFilter"})
        FilterRegistrationBean<TraceIdFilter> traceIdFilter(LogProperties properties) {
            FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>();
            registration.setFilter(new TraceIdFilter(properties));
            return registration;
        }

        @Bean({"methodLogAspect", "userMethodLogAspect"})
        MethodLogAspect methodLogAspect() {
            return new MethodLogAspect();
        }

        @Bean({"webLogAspect", "userWebLogAspect"})
        WebLogAspect webLogAspect(LogProperties properties) {
            return new WebLogAspect(properties);
        }

        @Bean({"auditLogService", "userAuditLogService"})
        AuditLogService auditLogService() {
            return event -> {
            };
        }

        @Bean({"auditLogStore", "userAuditLogStore"})
        LogRecordStore<AuditLogEvent> auditLogStore() {
            return new LogRecordStore<>() {
                @Override
                public void save(AuditLogEvent record) {
                }

                @Override
                public List<AuditLogEvent> queryRecent(int limit) {
                    return List.of();
                }

                @Override
                public long count() {
                    return 0;
                }
            };
        }
    }
}
