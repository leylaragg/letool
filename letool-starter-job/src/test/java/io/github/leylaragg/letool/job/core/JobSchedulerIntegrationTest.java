package io.github.leylaragg.letool.job.core;

import io.github.leylaragg.letool.job.exception.JobException;
import io.github.leylaragg.letool.job.quartz.QuartzJobMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.quartz.impl.StdSchedulerFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link JobScheduler} 基于真实 Quartz RAMJobStore 的集成测试。
 */
class JobSchedulerIntegrationTest {

    private Scheduler quartzScheduler;
    private JobScheduler jobScheduler;
    private DefaultJobHandlerRegistry registry;

    /**
     * 为每个测试创建独立 Quartz 调度器。
     *
     * @throws Exception Quartz 初始化失败时抛出
     */
    @BeforeEach
    void setUp() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("org.quartz.scheduler.instanceName", "test-" + System.nanoTime());
        properties.setProperty("org.quartz.scheduler.instanceId", "NON_CLUSTERED");
        properties.setProperty("org.quartz.threadPool.threadCount", "2");
        properties.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");
        quartzScheduler = new StdSchedulerFactory(properties).getScheduler();
        registry = new DefaultJobHandlerRegistry();
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("syncHandler", (JobHandler) context -> { });
        jobScheduler = new JobScheduler(
                quartzScheduler, new QuartzJobMapper("letool"), registry, beanFactory);
    }

    /**
     * 关闭独立 Quartz 调度器。
     *
     * @throws Exception Quartz 关闭失败时抛出
     */
    @AfterEach
    void tearDown() throws Exception {
        if (quartzScheduler != null) {
            quartzScheduler.shutdown(true);
        }
    }

    /**
     * 验证注册幂等、定义冲突和显式替换行为。
     */
    @Test
    void shouldRegisterIdempotentlyAndRejectConflictingDefinition() {
        JobDefinition definition = definition("0 0 6 * * ?", 2);
        jobScheduler.register(definition, "syncHandler");
        jobScheduler.register(definition, "syncHandler");

        assertThat(jobScheduler.getJobCount()).isEqualTo(1);
        assertThat(jobScheduler.getJob("sync")).isPresent();

        JobDefinition conflicting = definition("0 30 6 * * ?", 2);
        assertThatThrownBy(() -> jobScheduler.register(conflicting, "syncHandler"))
                .isInstanceOf(JobException.class)
                .extracting("code")
                .isEqualTo("JOB_002");

        jobScheduler.replace(conflicting, "syncHandler");
        assertThat(jobScheduler.getJob("sync")).get()
                .extracting(JobDefinition::getCron)
                .isEqualTo("0 30 6 * * ?");
    }

    /**
     * 验证手动触发、暂停恢复和注销都作用于全部分片。
     */
    @Test
    void shouldManageAllShardsThroughLogicalJobName() {
        jobScheduler.register(definition("0 0 6 * * ?", 3), "syncHandler");

        List<JobTriggerReceipt> receipts = jobScheduler.trigger("sync");
        assertThat(receipts).hasSize(3);
        assertThat(receipts).extracting(JobTriggerReceipt::shardIndex)
                .containsExactly(0, 1, 2);
        assertThat(jobScheduler.trigger("sync", 1).shardIndex()).isEqualTo(1);

        jobScheduler.pause("sync");
        assertThat(jobScheduler.isPaused("sync")).isTrue();
        jobScheduler.resume("sync");
        assertThat(jobScheduler.isPaused("sync")).isFalse();

        jobScheduler.unregister("sync");
        assertThat(jobScheduler.getJob("sync")).isEmpty();
    }

    /**
     * 验证 RAMJobStore 明确允许本地 Lambda 便利注册。
     */
    @Test
    void shouldAllowLocalHandlerOnlyForRamJobStore() {
        jobScheduler.registerLocal(definition(null, 1), context -> { });

        assertThat(registry.contains("sync")).isTrue();
        assertThat(jobScheduler.getAllJobs()).extracting(JobDefinition::getJobName)
                .containsExactly("sync");
    }

    /**
     * 创建测试任务定义。
     *
     * @param cron Cron 表达式
     * @param shardTotal 分片总数
     * @return 测试任务定义
     */
    private JobDefinition definition(String cron, int shardTotal) {
        return JobDefinition.builder()
                .jobName("sync")
                .cron(cron)
                .shardTotal(shardTotal)
                .param("tenant", "default")
                .build();
    }
}
