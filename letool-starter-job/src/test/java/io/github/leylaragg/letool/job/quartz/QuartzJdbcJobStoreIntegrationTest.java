package io.github.leylaragg.letool.job.quartz;

import io.github.leylaragg.letool.job.config.JobAutoConfiguration;
import io.github.leylaragg.letool.job.core.JobDefinition;
import io.github.leylaragg.letool.job.core.JobHandler;
import io.github.leylaragg.letool.job.core.JobScheduler;
import io.github.leylaragg.letool.job.exception.JobException;
import org.junit.jupiter.api.Test;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Quartz JDBC JobStore 持久化边界集成测试。
 */
class QuartzJdbcJobStoreIntegrationTest {

    /**
     * 验证任务在应用上下文重建后仍然存在，且持久化数据只含字符串。
     */
    @Test
    void shouldPersistDefinitionAcrossApplicationContexts() {
        String databaseName = "jobdb_" + System.nanoTime();

        runner(databaseName, "always").run(firstContext -> {
            assertThat(firstContext).hasNotFailed();
            JobScheduler jobScheduler = firstContext.getBean(JobScheduler.class);
            jobScheduler.register(definition(), "persistentHandler");

            Scheduler scheduler = firstContext.getBean(Scheduler.class);
            JobDetail detail = scheduler.getJobDetail(
                    firstContext.getBean(QuartzJobMapper.class).jobKey("persistent", 0));
            assertThat(detail.getJobDataMap().values())
                    .allSatisfy(value -> assertThat(value).isInstanceOf(String.class));

            assertThatThrownBy(() -> jobScheduler.registerLocal(definition(), context -> { }))
                    .isInstanceOf(JobException.class)
                    .extracting("code")
                    .isEqualTo("JOB_007");
        });

        runner(databaseName, "never").run(secondContext -> {
            assertThat(secondContext).hasNotFailed();
            assertThat(secondContext.getBean(JobScheduler.class).getJob("persistent")).isPresent();
        });
    }

    /**
     * 创建指向同一 H2 内存数据库的应用上下文运行器。
     *
     * @param databaseName 数据库名称
     * @param initializeSchema Quartz Schema 初始化模式
     * @return 应用上下文运行器
     */
    private ApplicationContextRunner runner(String databaseName, String initializeSchema) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DataSourceAutoConfiguration.class,
                        QuartzAutoConfiguration.class,
                        JobAutoConfiguration.class))
                .withUserConfiguration(HandlerConfiguration.class)
                .withPropertyValues(
                        "spring.datasource.url=jdbc:h2:mem:" + databaseName + ";DB_CLOSE_DELAY=-1",
                        "spring.datasource.username=sa",
                        "spring.datasource.password=",
                        "spring.datasource.driver-class-name=org.h2.Driver",
                        "spring.quartz.scheduler-name=letool-jdbc-test",
                        "spring.quartz.auto-startup=false",
                        "spring.quartz.job-store-type=jdbc",
                        "spring.quartz.jdbc.initialize-schema=" + initializeSchema,
                        "spring.quartz.properties.org.quartz.scheduler.instanceId=AUTO",
                        "spring.quartz.properties.org.quartz.jobStore.useProperties=true",
                        "spring.quartz.properties.org.quartz.jobStore.driverDelegateClass=org.quartz.impl.jdbcjobstore.StdJDBCDelegate");
    }

    /** @return 可持久化测试任务定义 */
    private JobDefinition definition() {
        return JobDefinition.builder()
                .jobName("persistent")
                .cron("0 0 6 * * ?")
                .shardTotal(2)
                .param("tenant", "default")
                .build();
    }

    /**
     * 提供所有节点都能够解析的 Spring Bean 处理器。
     */
    @Configuration(proxyBeanMethods = false)
    static class HandlerConfiguration {

        /** @return 测试任务处理器 */
        @Bean
        JobHandler persistentHandler() {
            return context -> { };
        }
    }
}
