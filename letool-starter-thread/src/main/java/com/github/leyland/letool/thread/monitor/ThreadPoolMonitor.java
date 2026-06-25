package com.github.leyland.letool.thread.monitor;

import com.github.leyland.letool.thread.pool.ThreadPoolManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

public class ThreadPoolMonitor {

    private final ThreadPoolManager manager;
    private final boolean enabled;

    public ThreadPoolMonitor(ThreadPoolManager manager, boolean enabled) {
        this.manager = manager;
        this.enabled = enabled;
    }

    public List<ThreadPoolMetrics> getAllMetrics() {
        return manager.getPools().entrySet().stream()
                .map(e -> new ThreadPoolMetrics(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    public ThreadPoolMetrics getMetrics(String poolName) {
        ThreadPoolExecutor executor = manager.get(poolName);
        if (executor == null) return null;
        return new ThreadPoolMetrics(poolName, executor);
    }

    public boolean isEnabled() {
        return enabled;
    }
}
