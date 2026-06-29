package com.github.leyland.letool.monitor.metrics;

import com.github.leyland.letool.monitor.config.MonitorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * JVM 指标采集器.
 *
 * <p>基于 JDK {@link ManagementFactory} 的 JVM 运行时指标采集，包括：
 * <ul>
 *   <li>堆内存和非堆内存使用量</li>
 *   <li>线程数（活跃线程、守护线程）</li>
 *   <li>CPU 负载（操作系统级）</li>
 *   <li>GC 次数和总耗时</li>
 *   <li>进程运行时长</li>
 * </ul>
 * 通过 {@link ScheduledExecutorService} 定期采集，可从 {@link #getMetrics()} 获取最新快照。</p>
 *
 * <p>生命周期：调用 {@link #start()} 开始采集，调用 {@link #stop()} 停止。
 * 建议在 {@link org.springframework.context.annotation.Bean @Bean} 的 {@code @PostConstruct} 中启动。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public class JvmMetrics {

    // ======================== 日志 ========================

    private static final Logger log = LoggerFactory.getLogger(JvmMetrics.class);

    // ======================== 字段 ========================

    /** 配置属性 */
    private final MonitorProperties properties;

    /** 定时调度线程池 */
    private ScheduledExecutorService scheduler;

    /** 最新一次采集的快照 */
    private final AtomicReference<JvmMetricsSnapshot> currentSnapshot = new AtomicReference<>();

    /** 是否正在运行 */
    private volatile boolean running = false;

    // ======================== 构造方法 ========================

    /**
     * 创建 JVM 指标采集器.
     *
     * @param properties 监控模块配置属性
     */
    public JvmMetrics(MonitorProperties properties) {
        this.properties = properties;
    }

    // ======================== 生命周期 ========================

    /**
     * 启动 JVM 指标采集.
     *
     * <p>会根据配置的采集间隔周期性执行采集。调用前会先执行一次即时采集
     * 以确保首次调用 {@link #getMetrics()} 就能获取到数据。</p>
     */
    public void start() {
        if (running) {
            log.warn("[Monitor-JVM] JVM 指标采集已在运行中，忽略重复启动");
            return;
        }
        if (!properties.getJvm().isEnabled()) {
            log.info("[Monitor-JVM] JVM 指标采集未启用，跳过启动");
            return;
        }

        running = true;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "letool-jvm-metrics");
            t.setDaemon(true);
            return t;
        });

        // 启动时立即采集一次
        collectNow();

        // 解析采集间隔
        long intervalSeconds = parseDurationToSeconds(properties.getJvm().getCollectInterval());
        scheduler.scheduleAtFixedRate(this::collectNow, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);

        log.info("[Monitor-JVM] JVM 指标采集已启动，间隔 {}s", intervalSeconds);
    }

    /**
     * 停止 JVM 指标采集.
     *
     * <p>释放调度线程池资源。</p>
     */
    public void stop() {
        if (!running) return;
        running = false;

        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("[Monitor-JVM] JVM 指标采集已停止");
    }

    // ======================== 公共方法 ========================

    /**
     * 获取最新一次采集的 JVM 指标快照.
     *
     * @return JVM 指标快照，还未采集过则返回 {@code null}
     */
    public JvmMetricsSnapshot getMetrics() {
        return currentSnapshot.get();
    }

    /**
     * 立即执行一次 JVM 指标采集.
     */
    public void collectNow() {
        try {
            JvmMetricsSnapshot snapshot = collectMetrics();
            currentSnapshot.set(snapshot);
            if (log.isDebugEnabled()) {
                log.debug("[Monitor-JVM] {}", snapshot);
            }
        } catch (Exception e) {
            log.error("[Monitor-JVM] JVM 指标采集失败", e);
        }
    }

    // ======================== 内部方法 ========================

    /**
     * 实际执行 JVM 指标采集.
     *
     * @return 本次采集的快照
     */
    private JvmMetricsSnapshot collectMetrics() {
        // 内存
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();

        long heapUsed = heapUsage.getUsed();
        long heapMax = heapUsage.getMax();
        long nonHeapUsed = nonHeapUsage.getUsed();

        // 线程
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        int threadCount = threadMXBean.getThreadCount();
        int daemonCount = threadMXBean.getDaemonThreadCount();

        // CPU
        OperatingSystemMXBean osMXBean = ManagementFactory.getOperatingSystemMXBean();
        double cpuLoad = osMXBean.getSystemLoadAverage();
        if (cpuLoad < 0) {
            // Windows 下 getSystemLoadAverage() 返回 -1，尝试使用 com.sun.management API
            cpuLoad = getCpuLoad();
        }

        // 运行时长
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        long uptimeMs = runtimeMXBean.getUptime();

        // GC
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        long gcCount = 0;
        long gcTimeMs = 0;
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            gcCount += gcBean.getCollectionCount();
            gcTimeMs += gcBean.getCollectionTime();
        }

        return new JvmMetricsSnapshot(
                heapUsed, heapMax, nonHeapUsed,
                threadCount, daemonCount,
                cpuLoad, uptimeMs, gcCount, gcTimeMs
        );
    }

    /**
     * 获取 CPU 负载（兼容 Windows 环境）.
     *
     * <p>在 Windows 上 {@link OperatingSystemMXBean#getSystemLoadAverage()} 返回 -1，
     * 此时尝试使用 {@code com.sun.management.OperatingSystemMXBean} 获取进程 CPU 负载。</p>
     *
     * @return CPU 负载值，无法获取时返回 -1
     */
    private double getCpuLoad() {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
            double processCpuLoad = ((com.sun.management.OperatingSystemMXBean) osBean).getProcessCpuLoad();
            if (processCpuLoad >= 0) {
                return processCpuLoad;
            }
        }
        return -1;
    }

    /**
     * 解析类似 {@code 30s}、{@code 1m}、{@code 1h} 的时长字符串为秒数.
     *
     * @param duration 时长字符串
     * @return 秒数，解析失败时返回默认值 30
     */
    private long parseDurationToSeconds(String duration) {
        if (duration == null || duration.isEmpty()) return 30;
        try {
            duration = duration.trim().toLowerCase();
            if (duration.endsWith("ms")) {
                return Long.parseLong(duration.replace("ms", "")) / 1000;
            } else if (duration.endsWith("s")) {
                return Long.parseLong(duration.replace("s", ""));
            } else if (duration.endsWith("m")) {
                return Long.parseLong(duration.replace("m", "")) * 60;
            } else if (duration.endsWith("h")) {
                return Long.parseLong(duration.replace("h", "")) * 3600;
            } else if (duration.endsWith("d")) {
                return Long.parseLong(duration.replace("d", "")) * 86400;
            } else {
                return Long.parseLong(duration);
            }
        } catch (NumberFormatException e) {
            log.warn("[Monitor-JVM] 无法解析时长字符串: {}，使用默认值 30s", duration);
            return 30;
        }
    }
}
