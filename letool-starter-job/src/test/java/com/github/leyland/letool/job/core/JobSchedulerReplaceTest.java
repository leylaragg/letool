package com.github.leyland.letool.job.core;

import com.github.leyland.letool.job.exception.JobException;
import com.github.leyland.letool.job.quartz.QuartzJobMapper;
import org.junit.jupiter.api.Test;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link JobScheduler} 替换任务失败场景测试。
 */
class JobSchedulerReplaceTest {

    /**
     * 验证新定义写入失败时不会先删除线上旧定义，也不会遗留本次临时注册的处理器。
     *
     * @throws SchedulerException 构造 Quartz 失败场景时抛出
     */
    @Test
    void shouldKeepExistingJobWhenReplacementCannotBeScheduled() throws SchedulerException {
        Scheduler scheduler = mock(Scheduler.class);
        JobKey existingKey = JobKey.jobKey("sync#0", "letool");
        when(scheduler.getJobKeys(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Set.of(existingKey));
        doThrow(new SchedulerException("模拟持久化失败"))
                .when(scheduler).scheduleJobs(anyMap(), anyBoolean());

        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("syncHandler", (JobHandler) context -> { });
        DefaultJobHandlerRegistry registry = new DefaultJobHandlerRegistry();
        JobScheduler jobScheduler = new JobScheduler(
                scheduler, new QuartzJobMapper("letool"), registry, beanFactory);
        JobDefinition replacement = JobDefinition.builder()
                .jobName("sync")
                .cron("0 30 6 * * ?")
                .build();

        assertThatThrownBy(() -> jobScheduler.replace(replacement, "syncHandler"))
                .isInstanceOf(JobException.class)
                .extracting("code")
                .isEqualTo("JOB_006");

        verify(scheduler, never()).deleteJobs(anyList());
        assertThat(registry.contains("sync")).isFalse();
    }

    /**
     * 验证新定义已写入但旧分片清理失败时保留处理器，避免新任务失去执行入口。
     *
     * @throws SchedulerException 构造 Quartz 失败场景时抛出
     */
    @Test
    void shouldKeepHandlerWhenReplacementWasStoredBeforeCleanupFailure() throws SchedulerException {
        Scheduler scheduler = mock(Scheduler.class);
        when(scheduler.getJobKeys(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Set.of(
                        JobKey.jobKey("sync#0", "letool"),
                        JobKey.jobKey("sync#1", "letool")));
        doThrow(new SchedulerException("模拟旧分片清理失败"))
                .when(scheduler).deleteJobs(anyList());

        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("syncHandler", (JobHandler) context -> { });
        DefaultJobHandlerRegistry registry = new DefaultJobHandlerRegistry();
        JobScheduler jobScheduler = new JobScheduler(
                scheduler, new QuartzJobMapper("letool"), registry, beanFactory);
        JobDefinition replacement = JobDefinition.builder()
                .jobName("sync")
                .cron("0 30 6 * * ?")
                .shardTotal(1)
                .build();

        assertThatThrownBy(() -> jobScheduler.replace(replacement, "syncHandler"))
                .isInstanceOf(JobException.class)
                .extracting("code")
                .isEqualTo("JOB_006");

        assertThat(registry.contains("sync")).isTrue();
    }
}
