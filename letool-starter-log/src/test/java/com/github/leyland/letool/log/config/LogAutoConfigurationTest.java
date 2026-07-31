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
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.bind.UnboundConfigurationPropertiesException;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LogAutoConfiguration} 的 Spring Boot Starter 契约测试。
 *
 * <p>日志模块负责日志、审计和请求链路能力，但不创建线程池、数据库连接或
 * 线程上下文传播组件。各项能力可以独立关闭，并允许业务应用替换默认 Bean。</p>
 */
class LogAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LogAutoConfiguration.class))
            .withPropertyValues("spring.main.allow-bean-definition-overriding=false");

    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LogAutoConfiguration.class))
            .withPropertyValues("spring.main.allow-bean-definition-overriding=false");

    /**
     * 非 Web 应用默认应创建可编程使用的审计服务和方法切面。
     */
    @Test
    void shouldCreateNonWebLogInfrastructureBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(LogProperties.class);
            assertThat(context).hasSingleBean(AuditLogService.class);
            assertThat(context.getBean(AuditLogService.class))
                    .isInstanceOf(Slf4jAuditLogService.class);
            assertThat(context).hasSingleBean(AuditLogAspect.class);
            assertThat(context).hasSingleBean(MethodLogAspect.class);
            assertThat(context).doesNotHaveBean(AuditContextProvider.class);
            assertThat(context).doesNotHaveBean(TaskDecorator.class);
            assertThat(context).doesNotHaveBean("traceIdFilter");
            assertThat(context).doesNotHaveBean("webLogFilter");
        });
    }

    /**
     * Servlet Web 应用默认应额外创建请求链路、Web 日志和 Servlet 审计上下文。
     */
    @Test
    void shouldCreateWebLogInfrastructureBeansInWebApplication() {
        webContextRunner.run(context -> {
            assertThat(context).hasSingleBean(AuditLogService.class);
            assertThat(context).hasSingleBean(AuditLogAspect.class);
            assertThat(context).hasSingleBean(ServletAuditContextProvider.class);
            assertThat(context).hasSingleBean(MethodLogAspect.class);
            assertThat(context).hasBean("traceIdFilter");
            assertThat(context).hasBean("webLogFilter");
            assertThat(context).hasSingleBean(WebLogFilter.class);
            assertThat(context).hasBean("webLogFilterRegistration");
            assertThat(context.getBean(
                    "webLogFilterRegistration",
                    FilterRegistrationBean.class).getFilter())
                    .isSameAs(context.getBean(WebLogFilter.class));
        });
    }

    /**
     * 总开关关闭时不应创建任何日志基础设施 Bean。
     */
    @Test
    void shouldDisableLogAutoConfiguration() {
        contextRunner
                .withPropertyValues("letool.log.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(LogProperties.class);
                    assertThat(context).doesNotHaveBean(AuditLogService.class);
                    assertThat(context).doesNotHaveBean(AuditLogAspect.class);
                    assertThat(context).doesNotHaveBean(MethodLogAspect.class);
                    assertThat(context).doesNotHaveBean("mdcTaskDecorator");
                });
    }

    /**
     * AspectJ 缺失时应跳过方法和审计切面，但保留独立的 Servlet 过滤器。
     */
    @Test
    void shouldStartWithoutAspectsWhenAspectJClasspathIsMissing() {
        webContextRunner
                .withClassLoader(new FilteredClassLoader("org.aspectj"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AuditLogService.class);
                    assertThat(context).hasSingleBean(ServletAuditContextProvider.class);
                    assertThat(context).hasBean("traceIdFilter");
                    assertThat(context).hasBean("webLogFilter");
                    assertThat(context).doesNotHaveBean(AuditLogAspect.class);
                    assertThat(context).doesNotHaveBean(MethodLogAspect.class);
                });
    }

    /**
     * Web 与 Servlet 类缺失时应保留通用日志和审计能力。
     */
    @Test
    void shouldStartWithoutWebAndServletClasspath() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("org.springframework.web", "jakarta.servlet"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(AuditLogService.class);
                    assertThat(context).hasSingleBean(AuditLogAspect.class);
                    assertThat(context).doesNotHaveBean(AuditContextProvider.class);
                    assertThat(context).doesNotHaveBean("traceIdFilter");
                    assertThat(context).doesNotHaveBean("webLogFilter");
                });
    }

    /**
     * 关闭 Web 请求日志时只应跳过 Web 日志过滤器。
     */
    @Test
    void shouldDisableWebLogFilterOnly() {
        webContextRunner
                .withPropertyValues("letool.log.web-log.enabled=false")
                .run(context -> {
                    assertThat(context).hasBean("traceIdFilter");
                    assertThat(context).doesNotHaveBean("webLogFilter");
                    assertThat(context).hasSingleBean(AuditLogService.class);
                    assertThat(context).hasSingleBean(AuditLogAspect.class);
                });
    }

    /**
     * 关闭审计能力时应同时跳过审计服务、切面和上下文提供器。
     */
    @Test
    void shouldDisableAuditOnly() {
        webContextRunner
                .withPropertyValues("letool.log.audit.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AuditLogService.class);
                    assertThat(context).doesNotHaveBean(AuditLogAspect.class);
                    assertThat(context).doesNotHaveBean(AuditContextProvider.class);
                    assertThat(context).hasSingleBean(MethodLogAspect.class);
                    assertThat(context).hasBean("webLogFilter");
                });
    }

    /**
     * 关闭请求链路追踪时不应关闭审计能力。
     */
    @Test
    void shouldKeepAuditEnabledWhenTraceIsDisabled() {
        contextRunner
                .withPropertyValues("letool.log.trace.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(AuditLogService.class);
                    assertThat(context).hasSingleBean(AuditLogAspect.class);
                    assertThat(context).doesNotHaveBean("mdcTaskDecorator");
                    assertThat(context).hasSingleBean(MethodLogAspect.class);
                });
    }

    /**
     * 用户声明日志基础设施时，Starter 默认实现应完整退让。
     */
    @Test
    void shouldBackOffWhenUserProvidesLogInfrastructureBeans() {
        webContextRunner
                .withUserConfiguration(UserLogConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MethodLogAspect.class);
                    assertThat(context).hasSingleBean(AuditLogService.class);
                    assertThat(context).hasSingleBean(AuditLogAspect.class);
                    assertThat(context).hasSingleBean(AuditContextProvider.class);
                    assertThat(context).hasBean("traceIdFilter");
                    assertThat(context).hasBean("webLogFilter");
                    assertThat(context).hasBean("webLogFilterRegistration");
                    assertThat(context.getBean(MethodLogAspect.class))
                            .isSameAs(context.getBean("userMethodLogAspect"));
                    assertThat(context.getBean(WebLogFilter.class))
                            .isSameAs(context.getBean("userWebLogFilter"));
                    assertThat(context.getBean(
                            "webLogFilterRegistration",
                            FilterRegistrationBean.class).getFilter())
                            .isSameAs(context.getBean("userWebLogFilter"));
                    assertThat(context.getBean(AuditLogService.class))
                            .isSameAs(context.getBean("userAuditLogService"));
                    assertThat(context.getBean(AuditLogAspect.class))
                            .isSameAs(context.getBean("userAuditLogAspect"));
                    assertThat(context.getBean(AuditContextProvider.class))
                            .isSameAs(context.getBean("userAuditContextProvider"));
                    assertThat(context.getBean("traceIdFilter"))
                            .isSameAs(context.getBean("userTraceIdFilter"));
                });
    }

    /**
     * 已删除的数据库存储配置应启动失败，避免升级后静默改变持久化方式。
     */
    @Test
    void shouldRejectRemovedAuditStorageProperty() {
        contextRunner
                .withPropertyValues("letool.log.audit.storage=database")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(UnboundConfigurationPropertiesException.class);
                });
    }

    /**
     * 已删除的请求体日志配置应启动失败，避免用户误以为请求体会被记录。
     */
    @Test
    void shouldRejectRemovedWebBodyLoggingProperty() {
        webContextRunner
                .withPropertyValues("letool.log.web-log.include-body=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(UnboundConfigurationPropertiesException.class);
                });
    }

    /**
     * 模拟业务应用替换全部日志基础设施 Bean。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserLogConfiguration {

        /**
         * 创建用户自定义的 TraceId 过滤器。
         *
         * @param properties 日志配置属性
         * @return 用户 TraceId 过滤器注册 Bean
         */
        @Bean({"traceIdFilter", "userTraceIdFilter"})
        FilterRegistrationBean<TraceIdFilter> traceIdFilter(LogProperties properties) {
            FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>();
            registration.setFilter(new TraceIdFilter(properties));
            return registration;
        }

        /**
         * 创建用户自定义的方法日志切面。
         *
         * @return 用户方法日志切面
         */
        @Bean({"methodLogAspect", "userMethodLogAspect"})
        MethodLogAspect methodLogAspect() {
            return new MethodLogAspect();
        }

        /**
         * 创建用户自定义的 Web 日志过滤器。
         *
         * @param properties 日志配置属性
         * @return 用户 Web 日志过滤器
         */
        @Bean({"webLogFilter", "userWebLogFilter"})
        WebLogFilter webLogFilter(LogProperties properties) {
            return new WebLogFilter(properties);
        }

        /**
         * 创建用户自定义的审计日志服务。
         *
         * @return 用户审计日志服务
         */
        @Bean({"auditLogService", "userAuditLogService"})
        AuditLogService auditLogService() {
            return event -> {
                // 测试扩展点只验证自动配置退让，不执行真实持久化。
            };
        }

        /**
         * 创建用户自定义的审计上下文提供器。
         *
         * @return 用户审计上下文提供器
         */
        @Bean({"auditContextProvider", "userAuditContextProvider"})
        AuditContextProvider auditContextProvider() {
            return AuditContextProvider.empty();
        }

        /**
         * 创建用户自定义的审计日志切面。
         *
         * @param auditLogService 用户审计日志服务
         * @param contextProvider 用户审计上下文提供器
         * @return 用户审计日志切面
         */
        @Bean({"auditLogAspect", "userAuditLogAspect"})
        AuditLogAspect auditLogAspect(
                AuditLogService auditLogService,
                AuditContextProvider contextProvider) {
            return new AuditLogAspect(
                    auditLogService,
                    Fastjson2JsonCodec.createDefault(),
                    contextProvider);
        }
    }
}
