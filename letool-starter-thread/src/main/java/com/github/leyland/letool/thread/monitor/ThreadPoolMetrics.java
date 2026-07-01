package com.github.leyland.letool.thread.monitor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池运行指标快照，封装 {@link ThreadPoolExecutor} 的关键状态数据。
 *
 * <p>该对象在创建时从 ThreadPoolExecutor 采样一次，之后不再更新，
 * 属于不可变快照。包含以下维度：</p>
 * <ul>
 *   <li><b>配置维度</b> — corePoolSize / maxPoolSize</li>
 *   <li><b>活跃度</b> — activeCount / poolSize / utilizationRate</li>
 *   <li><b>吞吐量</b> — completedTaskCount / taskCount / pendingTaskCount</li>
 *   <li><b>队列</b> — queueSize / queueRemainingCapacity</li>
 * </ul>
 *
 * @author leyland
 * @since 2.0.0
 */
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

    /**
     * 从 ThreadPoolExecutor 采样创建指标快照。
     *
     * @param poolName 线程池名称
     * @param executor ThreadPoolExecutor 实例
     */
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

    /** @return 核心线程数 */
    public int getCorePoolSize() { return corePoolSize; }

    /** @return 最大线程数 */
    public int getMaxPoolSize() { return maxPoolSize; }

    /** @return 当前活跃（执行任务中）的线程数 */
    public int getActiveCount() { return activeCount; }

    /** @return 当前池中线程总数（含空闲） */
    public int getPoolSize() { return poolSize; }

    /** @return 已完成任务总数 */
    public long getCompletedTaskCount() { return completedTaskCount; }

    /** @return 已提交任务总数（含队列中等待的） */
    public long getTaskCount() { return taskCount; }

    /** @return 等待队列中的任务数 */
    public int getQueueSize() { return queueSize; }

    /** @return 等待队列剩余容量 */
    public int getQueueRemainingCapacity() { return queueRemainingCapacity; }

    /**
     * 计算线程池利用率。
     *
     * @return activeCount / maxPoolSize，maxPoolSize 为 0 时返回 0
     */
    public double getUtilizationRate() {
        return maxPoolSize > 0 ? (double) activeCount / maxPoolSize : 0;
    }

    /**
     * 计算等待中的任务数。
     *
     * @return taskCount - completedTaskCount（包括正在执行和排队的）
     */
    public long getPendingTaskCount() {
        return taskCount - completedTaskCount;
    }
}
