package com.github.leyland.letool.thread.monitor;

import java.util.concurrent.ThreadPoolExecutor;

public class ThreadPoolMetrics {

    private final String poolName;
    private final int corePoolSize;
    private final int maxPoolSize;
    private final int activeCount;
    private final int poolSize;
    private final long completedTaskCount;
    private final long taskCount;
    private final int queueSize;
    private final int queueRemainingCapacity;

    public ThreadPoolMetrics(String poolName, ThreadPoolExecutor executor) {
        this.poolName = poolName;
        this.corePoolSize = executor.getCorePoolSize();
        this.maxPoolSize = executor.getMaximumPoolSize();
        this.activeCount = executor.getActiveCount();
        this.poolSize = executor.getPoolSize();
        this.completedTaskCount = executor.getCompletedTaskCount();
        this.taskCount = executor.getTaskCount();
        this.queueSize = executor.getQueue().size();
        this.queueRemainingCapacity = executor.getQueue().remainingCapacity();
    }

    public String getPoolName() { return poolName; }
    public int getCorePoolSize() { return corePoolSize; }
    public int getMaxPoolSize() { return maxPoolSize; }
    public int getActiveCount() { return activeCount; }
    public int getPoolSize() { return poolSize; }
    public long getCompletedTaskCount() { return completedTaskCount; }
    public long getTaskCount() { return taskCount; }
    public int getQueueSize() { return queueSize; }
    public int getQueueRemainingCapacity() { return queueRemainingCapacity; }

    public double getUtilizationRate() {
        return maxPoolSize > 0 ? (double) activeCount / maxPoolSize : 0;
    }

    public long getPendingTaskCount() {
        return taskCount - completedTaskCount;
    }
}
