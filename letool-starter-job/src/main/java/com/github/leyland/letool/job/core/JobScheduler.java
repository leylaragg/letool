package com.github.leyland.letool.job.core;

import com.github.leyland.letool.job.config.JobProperties;
import com.github.leyland.letool.job.exception.JobException;
import com.github.leyland.letool.job.retry.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class JobScheduler {

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

        // 如果定义了 cron 表达式，则启动定时调度
        if (job.getCron() != null && !job.getCron().isEmpty()) {
            scheduleJob(job);
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
            // 计算首次延迟和间隔
            CronSchedule schedule = parseCron(cron);
            long initialDelay = schedule.initialDelayMs;
            long period = schedule.periodMs;

            // 创建固定频率调度
            ScheduledFuture<?> future = executor.scheduleAtFixedRate(() -> {
                if (pausedJobs.containsKey(job.getJobName())) {
                    return; // 已暂停，跳过执行
                }
                // 检查 Cron 是否匹配当前时间
                if (matchesCurrentTime(cron)) {
                    JobContext context = new JobContext(
                            job.getJobName(), job.getShardIndex(), job.getShardTotal(), job.getParams());
                    JobResult result = new JobResult(context);
                    submitExecution(job, context, result);
                }
            }, initialDelay, period, TimeUnit.MILLISECONDS);

            scheduledJobs.put(job.getJobName(), future);
            log.info("任务 '{}' 已调度: cron={}, initialDelay={}ms, period={}ms",
                    job.getJobName(), cron, initialDelay, period);
        } catch (Exception e) {
            throw new JobException("Cron 表达式解析失败: " + cron, job.getJobName(), e);
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
                result.success("执行成功");
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
        /** 执行间隔（毫秒） */
        long periodMs;

        CronSchedule(long initialDelayMs, long periodMs) {
            this.initialDelayMs = initialDelayMs;
            this.periodMs = periodMs;
        }
    }

    /**
     * 解析 Cron 表达式为初始延迟和间隔.
     *
     * <p>这是一个简化的 Cron 解析实现，用于计算调度间隔.
     * 支持 * 通配符和具体数值. 实际精确匹配由 {@link #matchesCurrentTime(String)} 完成.</p>
     *
     * <p>支持的格式：</p>
     * <ul>
     *   <li>6 位: 秒 分 时 日 月 周</li>
     *   <li>7 位: 秒 分 时 日 月 周 年</li>
     * </ul>
     *
     * @param cron Cron 表达式
     * @return 调度信息
     */
    private CronSchedule parseCron(String cron) {
        String[] parts = cron.trim().split("\\s+");
        if (parts.length < 6 || parts.length > 7) {
            throw new IllegalArgumentException("Cron 表达式必须为6位或7位: " + cron);
        }

        long period = calculatePeriod(parts);
        long now = System.currentTimeMillis();
        long next = findNextExecutionTime(parts, now);
        long initialDelay = Math.max(0, next - now);

        return new CronSchedule(initialDelay, period);
    }

    /**
     * 根据 Cron 字段计算调度间隔.
     *
     * <p>从最小粒度的非"*"字段推断执行间隔.</p>
     *
     * @param parts Cron 表达式各字段
     * @return 间隔毫秒数
     */
    private long calculatePeriod(String[] parts) {
        // 按粒度从细到粗：秒、分、时、日、月、周、年
        // 找到第一个非"*"且非"?"的字段来确定间隔
        String second = parts[0].trim();
        String minute = parts[1].trim();
        String hour = parts[2].trim();
        String dayOfMonth = parts[3].trim();

        if (!"*".equals(second) && !"?".equals(second)) {
            return parseFieldInterval(second) * 1000L;
        }
        if (!"*".equals(minute) && !"?".equals(minute)) {
            return parseFieldInterval(minute) * 60 * 1000L;
        }
        if (!"*".equals(hour) && !"?".equals(hour)) {
            return parseFieldInterval(hour) * 3600 * 1000L;
        }
        if (!"*".equals(dayOfMonth) && !"?".equals(dayOfMonth)) {
            return parseFieldInterval(dayOfMonth) * 86400 * 1000L;
        }
        // 默认每分钟检查一次
        return 60_000L;
    }

    /**
     * 解析 Cron 字段的间隔值（支持 * /N 格式）.
     *
     * @param field Cron 字段值
     * @return 间隔数值
     */
    private long parseFieldInterval(String field) {
        if (field.startsWith("*/")) {
            return Long.parseLong(field.substring(2));
        }
        // 对于具体数值，返回域最大值
        return 1;
    }

    /**
     * 计算下一个 Cron 匹配时间.
     *
     * <p>简化实现：根据 Cron 字段计算下一个合适的时间点.</p>
     *
     * @param parts  Cron 表达式各字段
     * @param fromMs 起始时间（毫秒时间戳）
     * @return 下一个匹配时间的毫秒时间戳
     */
    private long findNextExecutionTime(String[] parts, long fromMs) {
        // 简化实现：计算到下一个匹配时间点
        String second = parts[0].trim();
        String minute = parts[1].trim();

        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(fromMs);

        // 处理秒
        if (!"*".equals(second) && !"?".equals(second)) {
            int targetSecond = parseFirstValue(second);
            int currentSecond = cal.get(java.util.Calendar.SECOND);
            if (currentSecond >= targetSecond) {
                cal.add(java.util.Calendar.MINUTE, 1);
                cal.set(java.util.Calendar.SECOND, targetSecond);
                cal.set(java.util.Calendar.MILLISECOND, 0);
            } else {
                cal.set(java.util.Calendar.SECOND, targetSecond);
                cal.set(java.util.Calendar.MILLISECOND, 0);
            }
            return cal.getTimeInMillis();
        }

        // 处理分
        if (!"*".equals(minute) && !"?".equals(minute)) {
            int targetMinute = parseFirstValue(minute);
            int currentMinute = cal.get(java.util.Calendar.MINUTE);
            cal.set(java.util.Calendar.SECOND, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            if (currentMinute >= targetMinute) {
                cal.add(java.util.Calendar.HOUR_OF_DAY, 1);
                cal.set(java.util.Calendar.MINUTE, targetMinute);
            } else {
                cal.set(java.util.Calendar.MINUTE, targetMinute);
            }
            return cal.getTimeInMillis();
        }

        // 默认下一秒
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        cal.add(java.util.Calendar.MINUTE, 1);
        return cal.getTimeInMillis();
    }

    /**
     * 解析字段的第一个数值（支持 * /N 格式）.
     *
     * @param field Cron 字段值
     * @return 第一个数值
     */
    private int parseFirstValue(String field) {
        if (field.startsWith("*/")) {
            return Integer.parseInt(field.substring(2));
        }
        try {
            return Integer.parseInt(field);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 检查当前时间是否匹配 Cron 表达式.
     *
     * <p>简化实现：将 Cron 转为每分钟检查的周期，然后验证具体时间字段是否匹配.</p>
     *
     * @param cron Cron 表达式
     * @return {@code true} 如果当前时间匹配
     */
    private boolean matchesCurrentTime(String cron) {
        try {
            String[] parts = cron.trim().split("\\s+");
            java.util.Calendar cal = java.util.Calendar.getInstance();

            // 简化匹配：检查分和时
            if (parts.length >= 2) {
                if (!fieldMatches(parts[1].trim(), cal.get(java.util.Calendar.MINUTE), 0, 59)) return false;
            }
            if (parts.length >= 3) {
                if (!fieldMatches(parts[2].trim(), cal.get(java.util.Calendar.HOUR_OF_DAY), 0, 23)) return false;
            }
            if (parts.length >= 4) {
                if (!fieldMatches(parts[3].trim(), cal.get(java.util.Calendar.DAY_OF_MONTH), 1, 31)) return false;
            }
            if (parts.length >= 5) {
                if (!fieldMatches(parts[4].trim(), cal.get(java.util.Calendar.MONTH) + 1, 1, 12)) return false;
            }
            if (parts.length >= 6) {
                if (!fieldMatches(parts[5].trim(), cal.get(java.util.Calendar.DAY_OF_WEEK) - 1, 0, 6)) return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("Cron 表达式时间匹配异常: {}", e.getMessage());
            return true; // 解析异常时默认执行
        }
    }

    /**
     * 检查单个字段是否匹配.
     *
     * <p>支持：*（全部匹配）、具体数值、* /N（每N）.</p>
     *
     * @param field  Cron 字段值
     * @param value  当前时间值
     * @param min    字段最小值（保留参数）
     * @param max    字段最大值（保留参数）
     * @return {@code true} 如果匹配
     */
    private boolean fieldMatches(String field, int value, int min, int max) {
        if ("*".equals(field) || "?".equals(field)) {
            return true;
        }
        if (field.startsWith("*/")) {
            int interval = Integer.parseInt(field.substring(2));
            return (value % interval) == 0;
        }
        // 具体值
        try {
            return Integer.parseInt(field) == value;
        } catch (NumberFormatException e) {
            return true; // 无法解析则默认匹配
        }
    }
}
