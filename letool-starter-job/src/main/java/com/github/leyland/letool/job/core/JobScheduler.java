package com.github.leyland.letool.job.core;

import com.github.leyland.letool.job.config.JobProperties;
import com.github.leyland.letool.job.exception.JobException;
import com.github.leyland.letool.job.retry.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronExpression;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 任务调度器——任务调度模块的核心组件，负责任务的注册、调度、执行和生命周期管理.
 *
 * <p>提供完整的任务生命周期管理能力：</p>
 * <ul>
 *   <li><b>任务注册/注销</b> — 支持动态添加和移除任务定义</li>
 *   <li><b>Cron 调度</b> — 基于 Cron 表达式的自动调度执行</li>
 *   <li><b>手动触发</b> — 支持 API 手动触发任务立即执行</li>
 *   <li><b>暂停/恢复</b> — 运行时暂停和恢复任务调度</li>
 *   <li><b>失败重试</b> — 执行失败后自动重试，指数退避</li>
 *   <li><b>状态查询</b> — 查询任务运行状态和执行历史</li>
 * </ul>
 *
 * <h3>线程安全说明</h3>
 * <p>所有操作方法均为线程安全，使用 {@link ConcurrentHashMap}
 * 存储注册信息和运行状态. {@code register} 和 {@code unregister} 等方法
 * 内部使用同步块保证原子性.</p>
 *
 * @author leyland
 * @since 2.0.0
 * @see JobDefinition
 * @see JobHandler
 * @see JobLogService
 */
public class JobScheduler implements AutoCloseable {

    // ======================== 日志 ========================

    private static final Logger log = LoggerFactory.getLogger(JobScheduler.class);

    // ======================== 成员变量 ========================

    /**
     * 已注册的任务定义: 任务名 → JobDefinition.
     */
    private final ConcurrentHashMap<String, JobDefinition> jobs = new ConcurrentHashMap<>();

    /**
     * 正在执行中的任务: 任务名 → Future 集合（用于查询真实执行状态）.
     */
    private final ConcurrentHashMap<String, Set<Future<?>>> runningJobs = new ConcurrentHashMap<>();

    /**
     * 已安排的定时调度: 任务名 → ScheduledFuture（用于取消和暂停 Cron 调度）.
     */
    private final ConcurrentHashMap<String, ScheduledFuture<?>> scheduledJobs = new ConcurrentHashMap<>();

    /**
     * 已暂停的任务名称集合.
     */
    private final ConcurrentHashMap<String, Boolean> pausedJobs = new ConcurrentHashMap<>();

    /**
     * 任务执行线程池.
     */
    private final ScheduledThreadPoolExecutor executor;

    /**
     * 任务日志服务.
     */
    private final JobLogService logService;

    /**
     * 任务配置属性.
     */
    private final JobProperties properties;

    // ======================== 构造方法 ========================

    /**
     * 创建任务调度器.
     *
     * @param executor    任务执行线程池
     * @param logService  任务日志服务
     * @param properties  任务配置属性
     */
    public JobScheduler(ScheduledThreadPoolExecutor executor, JobLogService logService, JobProperties properties) {
        this.executor = executor;
        this.executor.setRemoveOnCancelPolicy(true);
        this.logService = logService;
        this.properties = properties;
    }

    // ======================== 任务注册/注销 ========================

    /**
     * 注册一个任务定义并启动调度.
     *
     * <p>如果任务已存在则抛出 {@link JobException}. 注册成功后，
     * 如果任务定义了 Cron 表达式，会自动创建定时调度.</p>
     *
     * @param job 任务定义
     * @throws JobException 如果任务名已存在或 Cron 表达式解析失败
     */
    public void register(JobDefinition job) {
        String jobName = job.getJobName();
        JobDefinition existing = jobs.putIfAbsent(jobName, job);
        if (existing != null) {
            throw new JobException("任务注册失败：任务名 '" + jobName + "' 已存在", jobName);
        }
        log.info("注册任务: {}, cron={}, shardTotal={}, maxRetries={}",
                jobName, job.getCron(), job.getShardTotal(), job.getMaxRetries());

        try {
            // 如果定义了 cron 表达式，则启动定时调度
            if (job.getCron() != null && !job.getCron().isEmpty()) {
                scheduleJob(job);
            }
        } catch (RuntimeException e) {
            jobs.remove(jobName, job);
            throw e;
        }
    }

    /**
     * 注销（移除）指定任务.
     *
     * <p>注销前先取消正在运行的调度和正在执行的任务.
     * 如果任务不存在则静默忽略.</p>
     *
     * @param jobName 任务名称
     */
    public void unregister(String jobName) {
        // 取消定时调度
        cancelSchedule(jobName);
        // 移除记录
        jobs.remove(jobName);
        pausedJobs.remove(jobName);
        log.info("注销任务: {}", jobName);
    }

    // ======================== 任务控制 ========================

    /**
     * 手动触发一次任务执行.
     *
     * <p>无论任务是否有 Cron 调度，均可手动触发. 已暂停的任务也可手动触发.
     * 执行结果通过 {@link JobLogService#record(JobResult)} 记录.</p>
     *
     * @param jobName 任务名称
     * @return 本次执行的 JobResult
     * @throws JobException 如果任务未注册
     */
    public JobResult trigger(String jobName) {
        JobDefinition job = jobs.get(jobName);
        if (job == null) {
            throw new JobException("任务 '" + jobName + "' 未注册", jobName);
        }
        JobContext context = new JobContext(jobName, job.getShardIndex(), job.getShardTotal(), job.getParams());
        JobResult result = new JobResult(context);
        log.info("手动触发任务: {}, executionId={}", jobName, result.getExecutionId());

        submitExecution(job, context, result);
        return result;
    }

    /**
     * 暂停指定任务的调度.
     *
     * <p>暂停后，定时调度不再触发，但正在执行中的任务不受影响.
     * 手动触发（trigger）仍然可用.</p>
     *
     * @param jobName 任务名称
     * @throws JobException 如果任务未注册
     */
    public void pause(String jobName) {
        if (!jobs.containsKey(jobName)) {
            throw new JobException("任务 '" + jobName + "' 未注册", jobName);
        }
        pausedJobs.put(jobName, true);
        cancelSchedule(jobName);
        log.info("暂停任务调度: {}", jobName);
    }

    /**
     * 恢复指定任务的调度.
     *
     * <p>如果任务定义了 Cron 表达式，重新创建定时调度.</p>
     *
     * @param jobName 任务名称
     * @throws JobException 如果任务未注册
     */
    public void resume(String jobName) {
        JobDefinition job = jobs.get(jobName);
        if (job == null) {
            throw new JobException("任务 '" + jobName + "' 未注册", jobName);
        }
        pausedJobs.remove(jobName);
        log.info("恢复任务调度: {}", jobName);

        if (job.getCron() != null && !job.getCron().isEmpty()) {
            scheduleJob(job);
        }
    }

    // ======================== 状态查询 ========================

    /**
     * 检查指定任务是否正在执行中.
     *
     * @param jobName 任务名称
     * @return {@code true} 如果任务正在执行
     */
    public boolean isRunning(String jobName) {
        Set<Future<?>> futures = runningJobs.get(jobName);
        return futures != null && futures.stream().anyMatch(this::isActive);
    }

    /**
     * 检查指定任务是否处于暂停状态.
     *
     * @param jobName 任务名称
     * @return {@code true} 如果任务已暂停
     */
    public boolean isPaused(String jobName) {
        return pausedJobs.containsKey(jobName);
    }

    /**
     * 获取所有已注册任务的定义列表.
     *
     * @return 任务定义列表（拷贝）
     */
    public List<JobDefinition> getAllJobs() {
        return new ArrayList<>(jobs.values());
    }

    /**
     * 获取指定任务的定义信息.
     *
     * @param jobName 任务名称
     * @return 任务定义，不存在返回 {@code null}
     */
    public JobDefinition getJob(String jobName) {
        return jobs.get(jobName);
    }

    /**
     * 获取当前正在运行的任务名称列表.
     *
     * @return 运行中任务名称列表
     */
    public List<String> getRunningJobs() {
        return runningJobs.entrySet().stream()
                .filter(e -> e.getValue().stream().anyMatch(this::isActive))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * 获取已暂停的任务名称列表.
     *
     * @return 已暂停的任务名称列表
     */
    public List<String> getPausedJobs() {
        return new ArrayList<>(pausedJobs.keySet());
    }

    /**
     * 获取已注册任务总数.
     *
     * @return 任务数量
     */
    public int getJobCount() {
        return jobs.size();
    }

    /**
     * 关闭调度器并释放本地调度资源。
     *
     * <p>该方法会取消所有 Cron 调度、清理运行状态索引，并关闭底层执行线程池。
     * 已经进入业务处理器的任务不会被强制中断，适合作为 Spring Bean destroy method
     * 或独立工具使用时的显式生命周期出口。</p>
     */
    public void shutdown() {
        scheduledJobs.forEach((jobName, future) -> future.cancel(false));
        scheduledJobs.clear();
        runningJobs.clear();
        pausedJobs.clear();
        executor.shutdown();
        log.info("任务调度器已关闭");
    }

    /**
     * AutoCloseable 适配，便于 try-with-resources 或通用资源关闭流程调用。
     */
    @Override
    public void close() {
        shutdown();
    }

    // ======================== 内部方法 - 调度 ========================

    /**
     * 解析 Cron 表达式并创建定时调度.
     *
     * <p>使用简单的 Cron 解析算法：支持 6 位（秒 分 时 日 月 周）和 7 位（秒 分 时 日 月 周 年）格式.
     * Cron 表达式各字段的取值范围与标准 Cron 一致.</p>
     *
     * @param job 任务定义
     */
    private void scheduleJob(JobDefinition job) {
        String cron = job.getCron();
        try {
            CronSchedule schedule = parseCron(cron);
            scheduleNextExecution(job, schedule.expression, schedule.initialDelayMs);
            log.info("任务 '{}' 已调度: cron={}, nextExecutionTime={}, initialDelay={}ms",
                    job.getJobName(), cron, schedule.nextExecutionTime, schedule.initialDelayMs);
        } catch (Exception e) {
            throw new JobException("Cron 表达式解析失败: " + cron, job.getJobName(), e);
        }
    }

    /**
     * 根据 Cron 表达式安排下一次单次触发，并在触发后继续计算后续触发点.
     *
     * <p>使用 Spring 的 {@link CronExpression} 负责完整 Cron 语义，避免内置调度器只按固定
     * period 轮询而错过具体分钟、小时、列表或范围表达式。该方法只维护本地生命周期和重新调度。</p>
     *
     * @param job            任务定义
     * @param cronExpression 已解析的 Cron 表达式
     * @param delayMs        距离下一次触发的延迟毫秒数
     */
    private void scheduleNextExecution(JobDefinition job, CronExpression cronExpression, long delayMs) {
        ScheduledFuture<?> future = executor.schedule(() -> {
            scheduledJobs.remove(job.getJobName());
            if (!jobs.containsKey(job.getJobName()) || pausedJobs.containsKey(job.getJobName())) {
                return;
            }

            JobContext context = new JobContext(
                    job.getJobName(), job.getShardIndex(), job.getShardTotal(), job.getParams());
            JobResult result = new JobResult(context);
            submitExecution(job, context, result);

            if (jobs.containsKey(job.getJobName()) && !pausedJobs.containsKey(job.getJobName())
                    && !executor.isShutdown()) {
                CronSchedule nextSchedule = createCronSchedule(cronExpression);
                scheduleNextExecution(job, cronExpression, nextSchedule.initialDelayMs);
            }
        }, Math.max(0, delayMs), TimeUnit.MILLISECONDS);

        ScheduledFuture<?> previous = scheduledJobs.put(job.getJobName(), future);
        if (previous != null) {
            previous.cancel(false);
        }
    }

    /**
     * 取消指定任务的定时调度.
     *
     * @param jobName 任务名称
     */
    private void cancelSchedule(String jobName) {
        ScheduledFuture<?> future = scheduledJobs.remove(jobName);
        if (future != null) {
            future.cancel(false);
            log.debug("取消任务调度: {}", jobName);
        }
    }

    // ======================== 内部方法 - 执行 ========================

    /**
     * 提交一次任务执行并跟踪其运行状态。
     *
     * <p>{@code runningJobs} 只保存真实执行中的任务，而 Cron 调度本身保存在
     * {@code scheduledJobs} 中。这样 {@link #isRunning(String)} 不会把“已安排定时调度”
     * 误判为“任务正在执行”。</p>
     *
     * @param job     任务定义
     * @param context 执行上下文
     * @param result  执行结果
     */
    private void submitExecution(JobDefinition job, JobContext context, JobResult result) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Runnable task = () -> {
            try {
                executeJob(job, context, result);
                future.complete(null);
            } catch (RuntimeException | Error ex) {
                future.completeExceptionally(ex);
                throw ex;
            } finally {
                removeRunningFuture(job.getJobName(), future);
            }
        };
        runningJobs.computeIfAbsent(job.getJobName(), key -> ConcurrentHashMap.newKeySet()).add(future);
        try {
            executor.execute(task);
        } catch (RuntimeException ex) {
            removeRunningFuture(job.getJobName(), future);
            future.completeExceptionally(ex);
            throw ex;
        }
    }

    /**
     * 移除一次已结束或提交失败的执行记录。
     *
     * @param jobName 任务名称
     * @param future  本次执行的 Future
     */
    private void removeRunningFuture(String jobName, Future<?> future) {
        Set<Future<?>> futures = runningJobs.get(jobName);
        if (futures == null) {
            return;
        }
        futures.remove(future);
        if (futures.isEmpty()) {
            runningJobs.remove(jobName, futures);
        }
    }

    /**
     * 判断一次任务执行是否仍处于活动状态。
     *
     * @param future 任务执行 Future
     * @return {@code true} 如果该执行尚未完成且未取消
     */
    private boolean isActive(Future<?> future) {
        return !future.isDone() && !future.isCancelled();
    }

    /**
     * 执行任务（含重试逻辑）.
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li>记录日志（开始执行）</li>
     *   <li>调用 handler.execute(context)</li>
     *   <li>如果成功 → 记录成功日志</li>
     *   <li>如果失败 → 根据重试策略决定是否重试（指数退避）</li>
     *   <li>重试耗尽 → 记录失败日志</li>
     * </ol>
     *
     * @param job     任务定义
     * @param context 执行上下文
     * @param result  执行结果
     */
    private void executeJob(JobDefinition job, JobContext context, JobResult result) {
        int retry = 0;
        while (true) {
            try {
                log.debug("执行任务: {}, executionId={}, retry={}",
                        job.getJobName(), context.getExecutionId(), retry);
                job.getHandler().execute(context);
                result.success("执行成功", retry);
                log.info("任务执行成功: {}, executionId={}, durationMs={}",
                        job.getJobName(), result.getExecutionId(), result.getDurationMs());
                break;
            } catch (Exception e) {
                log.error("任务执行失败: {}, executionId={}, retry={}, error={}",
                        job.getJobName(), context.getExecutionId(), retry, e.getMessage());

                if (RetryPolicy.shouldRetry(retry, job.getMaxRetries())) {
                    long delay = RetryPolicy.getBackoffDelay(
                            retry, job.getBackoffMs(), job.getBackoffMultiplier());
                    retry++;
                    log.warn("任务 {} 将在 {}ms 后进行第 {} 次重试",
                            job.getJobName(), delay, retry);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        result.fail("重试被中断: " + ie.getMessage(), retry);
                        if (properties.getLog().isEnabled()) {
                            logService.record(result);
                        }
                        return;
                    }
                } else {
                    result.fail(e.getMessage(), retry);
                    log.error("任务执行彻底失败: {}, executionId={}, totalRetries={}",
                            job.getJobName(), result.getExecutionId(), retry);
                    break;
                }
            }
        }

        // 记录执行日志
        if (properties.getLog().isEnabled()) {
            logService.record(result);
        }
    }

    // ======================== 内部方法 - Cron 解析 ========================

    /**
     * Cron 内部调度信息.
     */
    private static class CronSchedule {
        /** 首次执行延迟（毫秒） */
        long initialDelayMs;
        /** 下一次触发时间 */
        LocalDateTime nextExecutionTime;
        /** Spring Cron 表达式 */
        CronExpression expression;

        CronSchedule(long initialDelayMs, LocalDateTime nextExecutionTime, CronExpression expression) {
            this.initialDelayMs = initialDelayMs;
            this.nextExecutionTime = nextExecutionTime;
            this.expression = expression;
        }
    }

    /**
     * 解析 Cron 表达式并计算首次触发信息.
     *
     * <p>这里委托 Spring {@link CronExpression} 处理完整 Cron 语义，支持列表、范围、步进、
     * 问号占位等标准写法，避免本地调度器自行解析造成语义偏差。</p>
     *
     * @param cron Cron 表达式
     * @return 调度信息
     */
    private CronSchedule parseCron(String cron) {
        CronExpression expression = CronExpression.parse(cron);
        return createCronSchedule(expression);
    }

    /**
     * 根据已解析 Cron 表达式计算下一次触发信息.
     *
     * @param expression 已解析的 Spring Cron 表达式
     * @return 包含下一次触发时间和延迟的调度信息
     */
    private CronSchedule createCronSchedule(CronExpression expression) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = expression.next(now);
        if (next == null) {
            throw new IllegalArgumentException("Cron 表达式没有可计算的下一次触发时间");
        }
        long initialDelay = Math.max(0, Duration.between(now, next).toMillis());
        return new CronSchedule(initialDelay, next, expression);
    }
}
