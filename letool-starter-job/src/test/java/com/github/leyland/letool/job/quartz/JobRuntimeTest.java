package com.github.leyland.letool.job.quartz;

import com.github.leyland.letool.job.config.JobProperties;
import com.github.leyland.letool.job.core.DefaultJobHandlerRegistry;
import com.github.leyland.letool.job.core.JobExecutionRecord;
import com.github.leyland.letool.job.core.JobLogService;
import com.github.leyland.letool.job.core.JobStatus;
import com.github.leyland.letool.job.exception.JobException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link JobRuntime} 执行、重试和日志隔离测试。
 */
class JobRuntimeTest {

    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");

    /**
     * 验证成功处理器会生成最终成功记录。
     *
     * @throws Exception Quartz 上下文构造失败时抛出
     */
    @Test
    void shouldRecordSuccessfulExecution() throws Exception {
        DefaultJobHandlerRegistry registry = new DefaultJobHandlerRegistry();
        registry.register("sync", context -> { });
        List<JobExecutionRecord> records = new ArrayList<>();
        JobRuntime runtime = runtime(registry, List.of(records::add), 1_024);

        runtime.execute(context(data(0, 0)));

        assertThat(records).singleElement().satisfies(record -> {
            assertThat(record.getStatus()).isEqualTo(JobStatus.SUCCESS);
            assertThat(record.getExecutionId()).isEqualTo("execution-1");
            assertThat(record.getJobName()).isEqualTo("sync");
        });
    }

    /**
     * 验证失败执行通过一次性 Quartz Trigger 安排退避重试。
     *
     * @throws Exception Quartz 上下文构造失败时抛出
     */
    @Test
    void shouldScheduleOneShotRetryWithSameExecutionId() throws Exception {
        DefaultJobHandlerRegistry registry = new DefaultJobHandlerRegistry();
        registry.register("sync", context -> { throw new IllegalStateException("temporary"); });
        List<JobExecutionRecord> records = new ArrayList<>();
        JobRuntime runtime = runtime(registry, List.of(records::add), 1_024);
        JobExecutionContext context = context(data(2, 0));

        assertThatThrownBy(() -> runtime.execute(context))
                .isInstanceOf(JobExecutionException.class)
                .hasCauseInstanceOf(JobException.class)
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("temporary");

        ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
        verify(context.getScheduler()).scheduleJob(triggerCaptor.capture());
        Trigger retryTrigger = triggerCaptor.getValue();
        assertThat(retryTrigger.getJobDataMap().getString(JobDataKeys.EXECUTION_ID))
                .isEqualTo("execution-1");
        assertThat(retryTrigger.getJobDataMap().getInt(JobDataKeys.RETRY_COUNT)).isEqualTo(1);
        assertThat(retryTrigger.getStartTime()).isEqualTo(Date.from(NOW.plusMillis(100)));
        assertThat(records).singleElement()
                .extracting(JobExecutionRecord::getStatus)
                .isEqualTo(JobStatus.RETRY_SCHEDULED);
    }

    /**
     * 验证重试 Trigger 持久化失败会使用独立错误码，并保留 Quartz 底层原因。
     *
     * @throws Exception Quartz 上下文构造失败时抛出
     */
    @Test
    void shouldExposeRetrySchedulingFailureWithStableCode() throws Exception {
        DefaultJobHandlerRegistry registry = new DefaultJobHandlerRegistry();
        registry.register("sync", context -> { throw new IllegalStateException("temporary"); });
        List<JobExecutionRecord> records = new ArrayList<>();
        JobRuntime runtime = runtime(registry, List.of(records::add), 1_024);
        JobExecutionContext executionContext = context(data(2, 0));
        when(executionContext.getScheduler().scheduleJob(any(Trigger.class)))
                .thenThrow(new SchedulerException("database unavailable"));

        assertThatThrownBy(() -> runtime.execute(executionContext))
                .isInstanceOf(JobExecutionException.class)
                .cause()
                .isInstanceOf(JobException.class)
                .extracting("code")
                .isEqualTo("JOB_009");

        assertThat(records).singleElement()
                .extracting(JobExecutionRecord::getStatus)
                .isEqualTo(JobStatus.FAILED);
    }

    /**
     * 验证日志扩展失败不会阻断后续扩展，错误摘要会按配置截断。
     *
     * @throws Exception Quartz 上下文构造失败时抛出
     */
    @Test
    void shouldIsolateLogFailureAndTruncateErrorSummary() throws Exception {
        DefaultJobHandlerRegistry registry = new DefaultJobHandlerRegistry();
        registry.register("sync", context -> { throw new IllegalStateException("very-long-message"); });
        List<JobExecutionRecord> records = new ArrayList<>();
        JobLogService failing = record -> { throw new IllegalStateException("log failed"); };
        JobRuntime runtime = runtime(registry, List.of(failing, records::add), 8);

        assertThatThrownBy(() -> runtime.execute(context(data(0, 0))))
                .isInstanceOf(JobExecutionException.class);

        assertThat(records).singleElement().satisfies(record -> {
            assertThat(record.getStatus()).isEqualTo(JobStatus.FAILED);
            assertThat(record.getErrorMessage()).hasSize(8);
        });
    }

    /**
     * 验证处理器缺失会形成可观测失败记录，并以 Quartz 标准异常结束本次触发。
     *
     * @throws Exception Quartz 上下文构造失败时抛出
     */
    @Test
    void shouldRecordMissingHandlerAsFailedExecution() throws Exception {
        DefaultJobHandlerRegistry registry = new DefaultJobHandlerRegistry();
        List<JobExecutionRecord> records = new ArrayList<>();
        JobRuntime runtime = runtime(registry, List.of(records::add), 1_024);

        assertThatThrownBy(() -> runtime.execute(context(data(0, 0))))
                .isInstanceOf(JobExecutionException.class)
                .hasCauseInstanceOf(JobException.class);

        assertThat(records).singleElement().satisfies(record -> {
            assertThat(record.getStatus()).isEqualTo(JobStatus.FAILED);
            assertThat(record.getJobName()).isEqualTo("sync");
        });
    }

    /**
     * 验证 JVM 严重错误不会被包装为普通业务失败，也不会安排重试。
     *
     * @throws Exception Quartz 上下文构造失败时抛出
     */
    @Test
    void shouldPropagateFatalErrorWithoutRetry() throws Exception {
        DefaultJobHandlerRegistry registry = new DefaultJobHandlerRegistry();
        registry.register("sync", context -> { throw new AssertionError("fatal"); });
        JobRuntime runtime = runtime(registry, List.of(), 1_024);
        JobExecutionContext executionContext = context(data(2, 0));

        assertThatThrownBy(() -> runtime.execute(executionContext))
                .isInstanceOf(AssertionError.class)
                .hasMessage("fatal");
        verify(executionContext.getScheduler(), never()).scheduleJob(any(Trigger.class));
    }

    /**
     * 创建固定时钟的任务运行时。
     *
     * @param registry 处理器注册表
     * @param logServices 日志扩展
     * @param maxLength 错误摘要长度
     * @return 任务运行时
     */
    private JobRuntime runtime(
            DefaultJobHandlerRegistry registry,
            List<JobLogService> logServices,
            int maxLength) {
        JobProperties properties = new JobProperties();
        properties.setErrorSummaryMaxLength(maxLength);
        return new JobRuntime(registry, logServices, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /**
     * 创建 Quartz 执行上下文 Mock。
     *
     * @param data 合并后的 JobDataMap
     * @return Quartz 执行上下文
     * @throws Exception Scheduler 元数据读取失败时抛出
     */
    private JobExecutionContext context(JobDataMap data) throws Exception {
        JobExecutionContext context = mock(JobExecutionContext.class);
        Scheduler scheduler = mock(Scheduler.class);
        JobDetail detail = mock(JobDetail.class);
        when(context.getMergedJobDataMap()).thenReturn(data);
        when(context.getScheduler()).thenReturn(scheduler);
        when(context.getJobDetail()).thenReturn(detail);
        when(context.getFireInstanceId()).thenReturn("fire-1");
        when(context.getScheduledFireTime()).thenReturn(Date.from(NOW.minusSeconds(1)));
        when(context.isRecovering()).thenReturn(false);
        when(detail.getKey()).thenReturn(JobKey.jobKey("sync#0", "letool"));
        when(scheduler.getSchedulerInstanceId()).thenReturn("node-a");
        when(scheduler.scheduleJob(any(Trigger.class))).thenReturn(Date.from(NOW.plusMillis(100)));
        return context;
    }

    /**
     * 创建执行所需的合并 JobDataMap。
     *
     * @param maxRetries 最大额外重试次数
     * @param retryCount 当前重试次数
     * @return JobDataMap
     */
    private JobDataMap data(int maxRetries, int retryCount) {
        JobDataMap data = new JobDataMap();
        data.put(JobDataKeys.JOB_NAME, "sync");
        data.put(JobDataKeys.SHARD_INDEX, 0);
        data.put(JobDataKeys.SHARD_TOTAL, 1);
        data.put(JobDataKeys.MAX_RETRIES, maxRetries);
        data.put(JobDataKeys.BACKOFF_MS, 100L);
        data.put(JobDataKeys.BACKOFF_MULTIPLIER, 2.0);
        data.put(JobDataKeys.MAX_BACKOFF_MS, 1_000L);
        data.put(JobDataKeys.EXECUTION_ID, "execution-1");
        data.put(JobDataKeys.RETRY_COUNT, retryCount);
        data.put(JobDataKeys.PARAMETER_PREFIX + "tenant", "default");
        return data;
    }
}
