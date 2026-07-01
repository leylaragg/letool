package com.github.leyland.letool.thread.monitor;

import com.github.leyland.letool.thread.pool.ThreadPoolManager;

import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

/**
 * 线程池监控器，提供所有已注册线程池的运行指标快照。
 *
 * <p>通过 {@link #getAllMetrics()} 一次性获取所有线程池的指标，
 * 或 {@link #getMetrics(String)} 按名称查询单个线程池。
 * 当 {@code enabled=false} 时监控器实例仍存在但调用方应自行判断是否采集。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public class ThreadPoolMonitor {

    private final ThreadPoolManager manager;
    private final boolean enabled;

    /**
     * 构造监控器。
     *
     * @param manager 线程池管理器
     * @param enabled 是否启用监控
     */
    public ThreadPoolMonitor(ThreadPoolManager manager, boolean enabled) {
        this.manager = manager;
        this.enabled = enabled;
    }

    /**
     * 获取所有已注册线程池的指标快照。
     *
     * <p>仅返回平台线程池的指标，纯虚拟线程池不包含在内。</p>
     *
     * @return 线程池指标列表，无注册线程池时返回空列表
     */
    public List<ThreadPoolMetrics> getAllMetrics() {
        return manager.getPools().entrySet().stream()
                .map(e -> new ThreadPoolMetrics(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * 按名称获取单个线程池的指标。
     *
     * @param poolName 线程池名称
     * @return 线程池指标，未找到时返回 {@code null}
     */
    public ThreadPoolMetrics getMetrics(String poolName) {
        ThreadPoolExecutor executor = manager.get(poolName);
        if (executor == null) return null;
        return new ThreadPoolMetrics(poolName, executor);
    }

    /** @return 监控是否启用 */
    public boolean isEnabled() {
        return enabled;
    }
}
