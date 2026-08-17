package io.github.leylaragg.letool.job.quartz;

import io.github.leylaragg.letool.job.config.JobProperties;
import io.github.leylaragg.letool.job.core.JobContext;
import io.github.leylaragg.letool.job.core.JobExecutionRecord;
import io.github.leylaragg.letool.job.core.JobHandler;
import io.github.leylaragg.letool.job.core.JobHandlerRegistry;
import io.github.leylaragg.letool.job.core.JobLogService;
import io.github.leylaragg.letool.job.core.JobTriggerType;
import io.github.leylaragg.letool.job.exception.JobErrorCode;
import io.github.leylaragg.letool.job.exception.JobException;
import io.github.leylaragg.letool.job.retry.RetryPolicy;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 执行 Letool Quartz 分发任务、安排退避重试并发布执行记录。
 */
public class JobRuntime {

    /** SchedulerContext 中保存当前节点运行时的稳定键。 */
    public static final String SCHEDULER_CONTEXT_KEY = "letool.job.runtime";

    private static final Logger LOGGER = LoggerFactory.getLogger(JobRuntime.class);

    private final JobHandlerRegistry handlerRegistry;
    private final List<JobLogService> logServices;
    private final JobProperties properties;
    private final Clock clock;

    /**
     * 创建使用系统 UTC 时钟的任务运行时。
     *
     * @param handlerRegistry 当前节点处理器注册表
     * @param logServices 有序日志扩展
     * @param properties Letool Job 配置
     */
    public JobRuntime(
            JobHandlerRegistry handlerRegistry,
            List<JobLogService> logServices,
            JobProperties properties) {
        this(handlerRegistry, logServices, properties, Clock.systemUTC());
    }

    /**
     * 创建可注入时钟的任务运行时。
     *
     * @param handlerRegistry 当前节点处理器注册表
     * @param logServices 有序日志扩展
     * @param properties Letool Job 配置
     * @param clock 执行时间来源
     */
    public JobRuntime(
            JobHandlerRegistry handlerRegistry,
            List<JobLogService> logServices,
            JobProperties properties,
            Clock clock) {
        this.handlerRegistry = handlerRegistry;
        this.logServices = List.copyOf(logServices == null ? List.of() : logServices);
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 执行一次 Quartz 触发。
     *
     * @param executionContext Quartz 执行上下文
     * @throws JobExecutionException 业务处理、重试安排或上下文解析失败时抛出
     */
    public void execute(JobExecutionContext executionContext) throws JobExecutionException {
        JobDataMap data = executionContext.getMergedJobDataMap();
        JobContext context = createContext(executionContext, data);
        try {
            JobHandler handler = handlerRegistry.getRequired(context.getJobName());
            handler.execute(context);
            recordSafely(JobExecutionRecord.success(context, clock.instant()));
        } catch (Exception failure) {
            Exception reportedFailure = executionFailure(context, failure);
            JobExecutionRecord record;
            if (RetryPolicy.shouldRetry(context.getRetryCount(), data.getInt(JobDataKeys.MAX_RETRIES))) {
                try {
                    scheduleRetry(executionContext, context, data);
                    record = JobExecutionRecord.retryScheduled(
                            context, clock.instant(), safeSummary(failure));
                } catch (SchedulerException retryFailure) {
                    JobException schedulingFailure = new JobException(
                            JobErrorCode.RETRY_SCHEDULING_FAILED,
                            context.getJobName(),
                            retryFailure,
                            context.getJobName());
                    schedulingFailure.addSuppressed(reportedFailure);
                    reportedFailure = schedulingFailure;
                    record = JobExecutionRecord.failed(
                            context, clock.instant(), safeSummary(failure));
                }
            } else {
                record = JobExecutionRecord.failed(context, clock.instant(), safeSummary(failure));
            }
            recordSafely(record);
            throw new JobExecutionException(reportedFailure);
        }
    }

    /**
     * 将业务异常规范为 Job 模块稳定错误码，同时保留已有基础设施异常。
     *
     * @param context 执行上下文
     * @param failure 原始异常
     * @return 交给 Quartz 的规范异常
     */
    private Exception executionFailure(JobContext context, Exception failure) {
        if (failure instanceof JobException) {
            return failure;
        }
        return new JobException(
                JobErrorCode.EXECUTION_FAILED,
                context.getJobName(),
                failure,
                context.getJobName());
    }

    private JobContext createContext(JobExecutionContext executionContext, JobDataMap data)
            throws JobExecutionException {
        try {
            Instant now = clock.instant();
            Date scheduled = executionContext.getScheduledFireTime();
            return new JobContext(
                    textOrRandom(data.getString(JobDataKeys.EXECUTION_ID)),
                    data.getString(JobDataKeys.JOB_NAME),
                    data.getInt(JobDataKeys.SHARD_INDEX),
                    data.getInt(JobDataKeys.SHARD_TOTAL),
                    data.containsKey(JobDataKeys.RETRY_COUNT) ? data.getInt(JobDataKeys.RETRY_COUNT) : 0,
                    triggerType(executionContext, data),
                    scheduled == null ? now : scheduled.toInstant(),
                    now,
                    executionContext.getFireInstanceId(),
                    executionContext.getScheduler().getSchedulerInstanceId(),
                    parameters(data));
        } catch (RuntimeException | SchedulerException exception) {
            throw new JobExecutionException("解析 Letool 任务执行上下文失败", exception);
        }
    }

    private void scheduleRetry(
            JobExecutionContext executionContext,
            JobContext context,
            JobDataMap data) throws SchedulerException {
        int nextRetry = context.getRetryCount() + 1;
        long delay = RetryPolicy.getBackoffDelay(
                context.getRetryCount(),
                data.getLong(JobDataKeys.BACKOFF_MS),
                data.getDouble(JobDataKeys.BACKOFF_MULTIPLIER),
                data.getLong(JobDataKeys.MAX_BACKOFF_MS));
        JobDataMap retryData = new JobDataMap();
        retryData.put(JobDataKeys.EXECUTION_ID, context.getExecutionId());
        retryData.put(JobDataKeys.RETRY_COUNT, String.valueOf(nextRetry));
        retryData.put(JobDataKeys.TRIGGER_TYPE, JobTriggerType.RETRY.name());
        TriggerKey key = TriggerKey.triggerKey(
                executionContext.getJobDetail().getKey().getName()
                        + ".retry." + context.getExecutionId() + "." + nextRetry,
                executionContext.getJobDetail().getKey().getGroup() + ".retry");
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(key)
                .forJob(executionContext.getJobDetail().getKey())
                .usingJobData(retryData)
                .startAt(Date.from(clock.instant().plusMillis(delay)))
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withRepeatCount(0)
                        .withMisfireHandlingInstructionFireNow())
                .build();
        executionContext.getScheduler().scheduleJob(trigger);
    }

    private JobTriggerType triggerType(JobExecutionContext context, JobDataMap data) {
        if (context.isRecovering()) {
            return JobTriggerType.RECOVERY;
        }
        String configured = data.getString(JobDataKeys.TRIGGER_TYPE);
        return configured == null || configured.isBlank()
                ? JobTriggerType.CRON : JobTriggerType.valueOf(configured);
    }

    private Map<String, String> parameters(JobDataMap data) {
        Map<String, String> parameters = new LinkedHashMap<>();
        data.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(JobDataKeys.PARAMETER_PREFIX))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> parameters.put(
                        entry.getKey().substring(JobDataKeys.PARAMETER_PREFIX.length()),
                        String.valueOf(entry.getValue())));
        return parameters;
    }

    private String safeSummary(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            message = failure.getClass().getSimpleName();
        }
        int limit = properties.getErrorSummaryMaxLength();
        if (limit <= 0) {
            limit = 1_024;
        }
        return message.length() <= limit ? message : message.substring(0, limit);
    }

    private String textOrRandom(String value) {
        return value == null || value.isBlank()
                ? UUID.randomUUID().toString().replace("-", "") : value;
    }

    private void recordSafely(JobExecutionRecord record) {
        for (JobLogService logService : logServices) {
            try {
                logService.record(record);
            } catch (RuntimeException exception) {
                LOGGER.error(
                        "任务执行日志扩展失败: jobName={}, executionId={}, extension={}",
                        record.getJobName(), record.getExecutionId(),
                        logService.getClass().getName(), exception);
            }
        }
    }
}
