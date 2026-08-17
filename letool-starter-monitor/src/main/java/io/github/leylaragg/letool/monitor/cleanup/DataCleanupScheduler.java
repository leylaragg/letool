package io.github.leylaragg.letool.monitor.cleanup;

import io.github.leylaragg.letool.monitor.exception.MonitorErrorCode;
import io.github.leylaragg.letool.monitor.exception.MonitorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于 Spring CronTrigger 的生产级数据清理调度器。
 *
 * <p>调度器不包含任何数据库删除实现，只负责用户 {@link CleanupTask} 的参数校验、
 * 单线程调度、防重入、失败隔离、执行报告和优雅关闭。</p>
 */
public final class DataCleanupScheduler {

    /** 数据清理日志。 */
    private static final Logger log =
            LoggerFactory.getLogger(DataCleanupScheduler.class);

    /** 生命周期互斥锁。 */
    private final Object lifecycleMonitor = new Object();

    /** 校验并冻结后的任务列表。 */
    private final List<RegisteredTask> tasks;

    /** 真实 Cron 触发器。 */
    private final CronTrigger cronTrigger;

    /** 调度和执行使用的时钟。 */
    private final Clock clock;

    /** 调度器拥有的单线程 Spring 任务调度器。 */
    private final ThreadPoolTaskScheduler taskScheduler;

    /** 防止手动触发和定时触发重叠。 */
    private final AtomicBoolean executionInProgress = new AtomicBoolean();

    /** 最近一次实际执行报告。 */
    private final AtomicReference<CleanupRunReport> lastReport =
            new AtomicReference<>();

    /** 当前 Cron 调度句柄。 */
    private volatile ScheduledFuture<?> scheduledFuture;

    /** Spring 调度器是否已经初始化。 */
    private volatile boolean schedulerInitialized;

    /** 当前是否已经启动 Cron 调度。 */
    private volatile boolean running;

    /** 调度器是否已经永久关闭。 */
    private volatile boolean closed;

    /**
     * 创建拥有独立单线程资源的数据清理调度器。
     *
     * @param tasks 用户提供的清理任务
     * @param cron Spring 六字段 Cron 表达式
     * @param zoneId Cron 解析时区
     * @param shutdownTimeout 优雅关闭最大等待时间
     */
    public DataCleanupScheduler(
            List<CleanupTask> tasks,
            String cron,
            ZoneId zoneId,
            Duration shutdownTimeout) {
        this(
                tasks,
                cron,
                zoneId,
                shutdownTimeout,
                Clock.system(zoneId == null
                        ? ZoneId.systemDefault()
                        : zoneId),
                new ThreadPoolTaskScheduler());
    }

    /**
     * 创建可注入时钟和 Spring 调度器的实例。
     *
     * <p>该构造器仅供同包测试使用，生产代码使用公开构造器并由当前实例拥有调度资源。</p>
     *
     * @param tasks 用户提供的清理任务
     * @param cron Spring 六字段 Cron 表达式
     * @param zoneId Cron 解析时区
     * @param shutdownTimeout 优雅关闭最大等待时间
     * @param clock 执行时钟
     * @param taskScheduler 未初始化的单线程调度器
     */
    DataCleanupScheduler(
            List<CleanupTask> tasks,
            String cron,
            ZoneId zoneId,
            Duration shutdownTimeout,
            Clock clock,
            ThreadPoolTaskScheduler taskScheduler) {
        this.clock = requireClock(clock);
        this.tasks = validateTasks(tasks, this.clock);
        this.taskScheduler = requireTaskScheduler(taskScheduler);
        this.cronTrigger = createCronTrigger(cron, zoneId);
        configureTaskScheduler(shutdownTimeout);
    }

    /**
     * 启动真实 Cron 调度。
     *
     * <p>重复启动是幂等操作；已经停止并永久关闭的实例不能再次启动。</p>
     *
     * @throws MonitorException 调度器已关闭或底层调度启动失败时抛出
     */
    public void start() {
        synchronized (lifecycleMonitor) {
            if (running) {
                return;
            }
            if (closed) {
                throw MonitorException.of(
                        MonitorErrorCode.CLEANUP_SCHEDULE_FAILED);
            }
            try {
                taskScheduler.initialize();
                schedulerInitialized = true;
                ScheduledFuture<?> future = taskScheduler.schedule(
                        this::runScheduled,
                        cronTrigger);
                if (future == null) {
                    throw new IllegalStateException("CronTrigger 没有下一次执行时间");
                }
                scheduledFuture = future;
                running = true;
                log.info(
                        "[Monitor-Cleanup] 数据清理调度器已启动，cron={}, zone={}",
                        cronTrigger.getExpression(),
                        taskScheduler.getClock().getZone());
            } catch (RuntimeException exception) {
                shutdownSchedulerAfterStartFailure();
                throw MonitorException.causedBy(
                        MonitorErrorCode.CLEANUP_SCHEDULE_FAILED,
                        exception);
            }
        }
    }

    /**
     * 手动执行一轮完整清理。
     *
     * <p>所有任务共享同一个触发时间。单任务失败会记录到报告并继续后续任务；
     * 已有清理运行时立即返回重入跳过报告。</p>
     *
     * @return 不可变执行报告
     */
    public CleanupRunReport runOnce() {
        Instant requestedAt = clock.instant();
        if (!executionInProgress.compareAndSet(false, true)) {
            return CleanupRunReport.overlapSkipped(requestedAt);
        }

        Instant startedAt = clock.instant();
        List<CleanupExecution> executions = new ArrayList<>(tasks.size());
        try {
            for (RegisteredTask task : tasks) {
                executions.add(executeTask(task, startedAt));
            }
            CleanupRunReport report = new CleanupRunReport(
                    startedAt,
                    clock.instant(),
                    false,
                    executions);
            lastReport.set(report);
            log.info(
                    "[Monitor-Cleanup] 本轮完成：成功 {} 个，失败 {} 个，影响 {} 条记录",
                    report.successCount(),
                    report.failureCount(),
                    report.totalAffectedRows());
            return report;
        } finally {
            executionInProgress.set(false);
        }
    }

    /**
     * 获取最近一次实际执行报告。
     *
     * @return 尚未执行时为空
     */
    public Optional<CleanupRunReport> lastReport() {
        return Optional.ofNullable(lastReport.get());
    }

    /**
     * 停止调度并优雅关闭专用线程池。
     *
     * <p>重复停止是幂等操作；停止后的实例不可重新启动。</p>
     */
    public void stop() {
        synchronized (lifecycleMonitor) {
            if (closed) {
                return;
            }
            closed = true;
            running = false;
            ScheduledFuture<?> future = scheduledFuture;
            scheduledFuture = null;
            if (future != null) {
                future.cancel(false);
            }
            if (schedulerInitialized) {
                taskScheduler.shutdown();
                schedulerInitialized = false;
            }
            log.info("[Monitor-Cleanup] 数据清理调度器已停止");
        }
    }

    /**
     * 判断 Cron 调度是否正在运行。
     *
     * @return 已启动且未停止时返回 {@code true}
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * 执行定时触发并确保异常不会终止 Spring 后续调度。
     */
    private void runScheduled() {
        try {
            runOnce();
        } catch (RuntimeException exception) {
            log.error("[Monitor-Cleanup] 定时清理基础设施执行失败", exception);
        }
    }

    /**
     * 执行单个清理任务并生成结果。
     *
     * @param task 已校验任务
     * @param triggeredAt 本轮统一触发时间
     * @return 单任务执行结果
     */
    private CleanupExecution executeTask(
            RegisteredTask task,
            Instant triggeredAt) {
        Instant taskStartedAt = clock.instant();
        try {
            CleanupContext context = new CleanupContext(
                    task.name(),
                    triggeredAt,
                    triggeredAt.minus(task.retention()));
            long affectedRows = task.delegate().cleanup(context);
            if (affectedRows < 0) {
                throw new IllegalStateException("清理任务返回了负数影响记录");
            }
            return CleanupExecution.success(
                    task.name(),
                    taskStartedAt,
                    clock.instant(),
                    affectedRows);
        } catch (RuntimeException exception) {
            log.error(
                    "[Monitor-Cleanup] 清理任务执行失败：{}",
                    task.name(),
                    exception);
            return CleanupExecution.failed(
                    task.name(),
                    taskStartedAt,
                    clock.instant(),
                    exception);
        }
    }

    /**
     * 校验并冻结任务定义。
     *
     * @param sourceTasks 原始任务列表
     * @param clock 校验截止时间计算使用的时钟
     * @return 不可变已注册任务列表
     */
    private static List<RegisteredTask> validateTasks(
            List<CleanupTask> sourceTasks,
            Clock clock) {
        if (sourceTasks == null || sourceTasks.isEmpty()) {
            throw MonitorException.of(MonitorErrorCode.CLEANUP_TASK_MISSING);
        }
        List<RegisteredTask> validated = new ArrayList<>(sourceTasks.size());
        Set<String> names = new HashSet<>();
        for (CleanupTask task : sourceTasks) {
            if (task == null) {
                throw configurationInvalid("CleanupTask 不能为空");
            }
            String name;
            Duration retention;
            try {
                name = task.name();
                retention = task.retention();
            } catch (RuntimeException exception) {
                throw MonitorException.causedBy(
                        MonitorErrorCode.CONFIGURATION_INVALID,
                        exception,
                        "读取 CleanupTask 定义失败");
            }
            if (name == null || name.isBlank()) {
                throw configurationInvalid("CleanupTask.name 不能为空");
            }
            String normalizedName = name.trim();
            if (!names.add(normalizedName)) {
                throw MonitorException.of(
                        MonitorErrorCode.CLEANUP_TASK_DUPLICATED,
                        normalizedName);
            }
            if (retention == null
                    || retention.isZero()
                    || retention.isNegative()) {
                throw configurationInvalid(
                        "CleanupTask.retention 必须为正数：" + normalizedName);
            }
            try {
                clock.instant().minus(retention);
            } catch (DateTimeException | ArithmeticException exception) {
                throw MonitorException.causedBy(
                        MonitorErrorCode.CONFIGURATION_INVALID,
                        exception,
                        "CleanupTask.retention 超出时间范围：" + normalizedName);
            }
            validated.add(new RegisteredTask(
                    normalizedName,
                    retention,
                    task));
        }
        return List.copyOf(validated);
    }

    /**
     * 创建并校验真实 Cron 触发器。
     *
     * @param cron Cron 表达式
     * @param zoneId Cron 时区
     * @return Cron 触发器
     */
    private static CronTrigger createCronTrigger(
            String cron,
            ZoneId zoneId) {
        if (cron == null || cron.isBlank()) {
            throw configurationInvalid("cleanCron 不能为空");
        }
        if (zoneId == null) {
            throw configurationInvalid("zoneId 不能为空");
        }
        try {
            return new CronTrigger(cron.trim(), zoneId);
        } catch (IllegalArgumentException exception) {
            throw MonitorException.causedBy(
                    MonitorErrorCode.CONFIGURATION_INVALID,
                    exception,
                    "cleanCron 不合法：" + cron);
        }
    }

    /**
     * 配置专用 Spring 调度器。
     *
     * @param shutdownTimeout 优雅关闭期限
     */
    private void configureTaskScheduler(Duration shutdownTimeout) {
        if (shutdownTimeout == null
                || shutdownTimeout.isZero()
                || shutdownTimeout.isNegative()) {
            throw configurationInvalid("shutdownTimeout 必须为正数");
        }
        long timeoutMillis;
        try {
            timeoutMillis = shutdownTimeout.toMillis();
        } catch (ArithmeticException exception) {
            throw MonitorException.causedBy(
                    MonitorErrorCode.CONFIGURATION_INVALID,
                    exception,
                    "shutdownTimeout 超出毫秒范围");
        }
        if (timeoutMillis <= 0) {
            throw configurationInvalid("shutdownTimeout 不能小于一毫秒");
        }
        taskScheduler.setPoolSize(1);
        taskScheduler.setThreadNamePrefix("letool-monitor-cleanup-");
        taskScheduler.setRemoveOnCancelPolicy(true);
        taskScheduler.setWaitForTasksToCompleteOnShutdown(true);
        taskScheduler.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        taskScheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        taskScheduler.setAwaitTerminationMillis(timeoutMillis);
        taskScheduler.setClock(clock);
    }

    /**
     * 在启动失败后释放已经初始化的调度资源。
     */
    private void shutdownSchedulerAfterStartFailure() {
        running = false;
        closed = true;
        if (schedulerInitialized) {
            taskScheduler.shutdown();
            schedulerInitialized = false;
        }
    }

    /**
     * 校验执行时钟。
     *
     * @param clock 原始时钟
     * @return 非空时钟
     */
    private static Clock requireClock(Clock clock) {
        if (clock == null) {
            throw configurationInvalid("clock 不能为空");
        }
        return clock;
    }

    /**
     * 校验 Spring 任务调度器。
     *
     * @param taskScheduler 原始任务调度器
     * @return 非空任务调度器
     */
    private static ThreadPoolTaskScheduler requireTaskScheduler(
            ThreadPoolTaskScheduler taskScheduler) {
        if (taskScheduler == null) {
            throw configurationInvalid("taskScheduler 不能为空");
        }
        return taskScheduler;
    }

    /**
     * 创建配置异常。
     *
     * @param reason 配置错误原因
     * @return 结构化监控异常
     */
    private static MonitorException configurationInvalid(String reason) {
        return MonitorException.of(
                MonitorErrorCode.CONFIGURATION_INVALID,
                reason);
    }

    /**
     * 校验后冻结的用户清理任务定义。
     *
     * @param name 稳定任务名称
     * @param retention 数据保留时长
     * @param delegate 用户任务实现
     */
    private record RegisteredTask(
            String name,
            Duration retention,
            CleanupTask delegate) {
    }
}
