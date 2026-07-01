package com.github.leyland.letool.thread.pool;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 可命名的线程工厂，创建的线程使用统一前缀 + 自增序号命名。
 *
 * <p>除了命名增强外，还确保所有创建的线程为守护线程（可配置）
 * 且优先级为 {@link Thread#NORM_PRIORITY}。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * ThreadFactory factory = new NamedThreadFactory("order-pool");
 * // 创建的线程名为: order-pool-1, order-pool-2, ...
 *
 * ThreadFactory daemonFactory = new NamedThreadFactory("bg-worker", true);
 * // 创建的守护线程: bg-worker-1, bg-worker-2, ...
 * }</pre>
 *
 * @author leyland
 * @since 2.0.0
 */
public class NamedThreadFactory implements ThreadFactory {

    /** 线程名前缀 */
    private final String prefix;

    /** 自增序号计数器 */
    private final AtomicInteger counter = new AtomicInteger(1);

    /** 是否创建守护线程 */
    private final boolean daemon;

    /**
     * 创建非守护线程的工厂。
     *
     * @param prefix 线程名前缀
     */
    public NamedThreadFactory(String prefix) {
        this(prefix, false);
    }

    /**
     * 创建可指定守护状态的线程工厂。
     *
     * @param prefix 线程名前缀
     * @param daemon 是否创建守护线程
     */
    public NamedThreadFactory(String prefix, boolean daemon) {
        this.prefix = prefix;
        this.daemon = daemon;
    }

    /**
     * 创建新线程。
     *
     * <p>线程名格式为 {@code prefix-N}，其中 N 为从 1 开始的自增整数。
     * 线程优先级统一设为 {@link Thread#NORM_PRIORITY}。</p>
     *
     * @param r 要执行的任务
     * @return 新创建的线程
     */
    @Override
    public Thread newThread(Runnable r) {
        Thread thread = new Thread(r, prefix + "-" + counter.getAndIncrement());
        thread.setDaemon(daemon);
        if (thread.getPriority() != Thread.NORM_PRIORITY) {
            thread.setPriority(Thread.NORM_PRIORITY);
        }
        return thread;
    }
}
