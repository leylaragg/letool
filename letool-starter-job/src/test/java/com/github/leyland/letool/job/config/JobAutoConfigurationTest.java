package com.github.leyland.letool.job.config;

import com.github.leyland.letool.job.core.JobLogService;
import com.github.leyland.letool.job.core.JobScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link JobAutoConfiguration} 的自动装配契约测试。
 *
 * <p>重点覆盖业务项目自定义任务调度基础设施 Bean 时，job starter 是否正确退让。</p>
 */
class JobAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JobAutoConfiguration.class))
            .withPropertyValues("spring.main.allow-bean-definition-overriding=false");

    /**
     * 验证用户提供任务执行器、日志服务和调度器时，自动配置不会创建重复 Bean。
     */
    @Test
    void shouldBackOffWhenUserProvidesJobInfrastructureBeans() {
        contextRunner
                .withUserConfiguration(UserJobConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(ScheduledThreadPoolExecutor.class);
                    assertThat(context).hasSingleBean(JobLogService.class);
                    assertThat(context).hasSingleBean(JobScheduler.class);
                    assertThat(context.getBean(ScheduledThreadPoolExecutor.class))
                            .isSameAs(context.getBean("jobScheduledExecutor"));
                    assertThat(context.getBean(JobLogService.class))
                            .isSameAs(context.getBean("jobLogService"));
                    assertThat(context.getBean(JobScheduler.class))
                            .isSameAs(context.getBean("jobScheduler"));
                });
    }

    /**
     * 模拟业务项目自行接管 job 基础设施的配置。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserJobConfiguration {

        @Bean(destroyMethod = "shutdown")
        ScheduledThreadPoolExecutor jobScheduledExecutor() {
            return new ScheduledThreadPoolExecutor(1);
        }

        @Bean
        JobLogService jobLogService() {
            return new JobLogService();
        }

        @Bean
        JobScheduler jobScheduler(ScheduledThreadPoolExecutor jobScheduledExecutor,
                                  JobLogService jobLogService,
                                  JobProperties properties) {
            return new JobScheduler(jobScheduledExecutor, jobLogService, properties);
        }
    }
}
