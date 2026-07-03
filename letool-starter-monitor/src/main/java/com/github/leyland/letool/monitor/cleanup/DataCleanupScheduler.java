package com.github.leyland.letool.monitor.cleanup;

import com.github.leyland.letool.monitor.config.MonitorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 数据残留定期清理调度器.
 *
 * <p>管理多个 {@link CleanupTask}，根据 Cron 表达式或固定间隔周期性执行数据清理。
 * 内部使用 {@link ScheduledExecutorService} 驱动调度。</p>
 *
 * <h3>显式启用后的行为</h3>
 * <p>该调度器仅在 {@code letool.monitor.data-retention.enabled=true} 时由自动配置创建。
 * 启动后会按照 {@code letool.monitor.data-retention} 中配置的保留天数，自动注册以下清理任务：
 * <ul>
 *   <li>{@code monitor_audit_log} —— 审计日志</li>
 *   <li>{@code monitor_request_log} —— 请求日志</li>
 *   <li>{@code monitor_api_stats} —— API 统计数据</li>
 *   <li>{@code monitor_api_error} —— API 错误记录</li>
 * </ul></p>
 *
 * <p>当前内置 {@link CleanupTask} 只记录日志，不执行真实 SQL 删除。
 * 可通过 {@link #registerTask(CleanupTask)} 注册自定义清理任务。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public class DataCleanupScheduler {

    // ======================== 日志 ========================

    private static final Logger log = LoggerFactory.getLogger(DataCleanupScheduler.class);

    // ======================== 字段 ========================

    /** 配置属性 */
    private final MonitorProperties properties;

    /** 注册的清理任务列表（线程安全） */
    private final List<CleanupTask> tasks = new CopyOnWriteArrayList<>();

    /** 定时调度线程池 */
    private ScheduledExecutorService scheduler;

    /** 上次清理时间 */
    private final AtomicReference<LocalDateTime> lastCleanupTime = new AtomicReference<>();

    /** 是否正在运行 */
    private volatile boolean running = false;

    // ======================== 构造方法 ========================

    /**
     * 创建数据清理调度器.
     *
     * @param properties 监控模块配置属性
     */
    public DataCleanupScheduler(MonitorProperties properties) {
        this.properties = properties;
    }

    // ======================== 生命周期 ========================

    /**
     * 启动定时清理调度.
     *
     * <p>根据配置的 Cron 表达式设置调度频率，并自动注册默认的清理任务。</p>
     */
    public void start() {
        if (running) {
            log.warn("[Monitor-Cleanup] 数据清理调度器已在运行中");
            return;
        }

        running = true;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "letool-data-cleanup");
            t.setDaemon(true);
            return t;
        });

        // 注册默认清理任务
        registerDefaultTasks();

        // 计算 Cron 对应的初始延迟和间隔
        String cron = properties.getDataRetention().getCleanCron();
        long intervalSeconds = parseCronToIntervalSeconds(cron);

        scheduler.scheduleAtFixedRate(this::cleanup, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);

        log.info("[Monitor-Cleanup] 数据清理调度器已启动，间隔 {}s，已注册 {} 个清理任务",
                intervalSeconds, tasks.size());
    }

    /**
     * 停止调度器.
     */
    public void stop() {
        if (!running) return;
        running = false;

        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("[Monitor-Cleanup] 数据清理调度器已停止");
    }

    // ======================== 公共方法 ========================

    /**
     * 手动触发一次全量清理.
     *
     * <p>遍历所有注册的清理任务，逐个执行清理操作。</p>
     */
    public void cleanup() {
        log.info("[Monitor-Cleanup] 开始执行全量数据清理...");
        LocalDateTime startTime = LocalDateTime.now();

        int successCount = 0;
        int skipCount = 0;
        int failCount = 0;

        for (CleanupTask task : tasks) {
            try {
                if (task.shouldCleanup()) {
                    long deletedRows = task.execute();
                    log.info("[Monitor-Cleanup] 任务 \"{}\" 完成，清理 {} 条记录", task.getTableName(), deletedRows);
                    successCount++;
                } else {
                    skipCount++;
                }
            } catch (Exception e) {
                log.error("[Monitor-Cleanup] 任务 \"{}\" 执行失败: {}", task.getTableName(), e.getMessage(), e);
                failCount++;
            }
        }

        lastCleanupTime.set(LocalDateTime.now());
        log.info("[Monitor-Cleanup] 全量清理完成: 成功 {} 个, 跳过 {} 个, 失败 {} 个, 耗时 {}ms",
                successCount, skipCount, failCount,
                Duration.between(startTime, LocalDateTime.now()).toMillis());
    }

    /**
     * 注册一个清理任务.
     *
     * <p>如果同表名的任务已存在，则不会重复注册。</p>
     *
     * @param task 清理任务实例
     */
    public void registerTask(CleanupTask task) {
        if (task == null) return;
        boolean exists = tasks.stream().anyMatch(t -> t.getTableName().equals(task.getTableName()));
        if (!exists) {
            tasks.add(task);
            log.info("[Monitor-Cleanup] 注册清理任务: {}", task.getTableName());
        }
    }

    /**
     * 获取所有已注册的清理任务.
     *
     * @return 清理任务的不可变列表
     */
    public List<CleanupTask> getTasks() {
        return Collections.unmodifiableList(tasks);
    }

    /**
     * 获取上次清理时间.
     *
     * @return 上次清理时间的 {@link Optional}，从未执行过则为空
     */
    public Optional<LocalDateTime> getLastCleanupTime() {
        return Optional.ofNullable(lastCleanupTime.get());
    }

    // ======================== 内部方法 ========================

    /**
     * 注册默认的清理任务（基于配置属性）.
     */
    private void registerDefaultTasks() {
        MonitorProperties.DataRetention retention = properties.getDataRetention();

        registerTask(new CleanupTask("monitor_audit_log", parseDays(retention.getAuditLog())));
        registerTask(new CleanupTask("monitor_request_log", parseDays(retention.getRequestLog())));
        registerTask(new CleanupTask("monitor_api_stats", parseDays(retention.getApiStats())));
        registerTask(new CleanupTask("monitor_api_error", parseDays(retention.getApiError())));
    }

    /**
     * 从配置字符串（如 {@code "90d"}）中提取天数.
     *
     * @param retention 保留字段，如 {@code "90d"}、{@code "7"}
     * @return 天数，解析失败返回默认值 30
     */
    private int parseDays(String retention) {
        if (retention == null || retention.isEmpty()) return 30;
        try {
            String cleaned = retention.trim().toLowerCase().replace("d", "");
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException e) {
            log.warn("[Monitor-Cleanup] 无法解析保留天数: {}，使用默认值 30d", retention);
            return 30;
        }
    }

    /**
     * 从 Cron 表达式估算执行间隔秒数.
     *
     * <p>简化实现：不支持完整 Cron 解析，仅识别常见的日期级调度模式。
     * 对不支持的表达式，回退到默认间隔（24 小时）。</p>
     *
     * @param cron Cron 表达式
     * @return 间隔秒数
     */
    private long parseCronToIntervalSeconds(String cron) {
        if (cron == null || cron.isEmpty()) {
            return 24 * 60 * 60; // 默认每天一次
        }

        // 简化 Cron 解析：仅识别 "*" 和具体数字
        // 复杂 Cron 回退到 24 小时
        try {
            String[] parts = cron.trim().split("\\s+");
            if (parts.length < 6) {
                return 24 * 60 * 60;
            }

            // 检查是否为每天固定时间执行（如 "0 0 3 * * ?"）
            // 第 4 位 = 月份 → "*" 表示每月
            // 第 5 位 = 星期 → "*" 或 "?" 表示每天
            String month = parts[3];
            String dayOfMonth = parts[2];
            String dayOfWeek = parts[4];

            if ("*".equals(month) && ("*".equals(dayOfWeek) || "?".equals(dayOfWeek))) {
                if ("*".equals(dayOfMonth)) {
                    return 24 * 60 * 60; // 每天
                }
                return 24 * 60 * 60;
            }

            // 无法精确匹配，回退到 24h
            return 24 * 60 * 60;
        } catch (Exception e) {
            log.warn("[Monitor-Cleanup] 无法解析 Cron 表达式: {}，使用默认间隔 24h", cron);
            return 24 * 60 * 60;
        }
    }
}
