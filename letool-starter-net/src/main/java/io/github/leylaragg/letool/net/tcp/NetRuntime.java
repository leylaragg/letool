package io.github.leylaragg.letool.net.tcp;

import io.github.leylaragg.letool.net.exception.NetErrorCode;
import io.github.leylaragg.letool.net.exception.NetException;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.EventExecutor;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TCP 客户端共享的 Netty 线程运行时。
 *
 * <p>框架创建的线程组采用惰性初始化并由当前运行时关闭；用户传入的线程组只会被复用，
 * 不会越权关闭。</p>
 */
public final class NetRuntime implements AutoCloseable {

    private static final Duration DEFAULT_QUIET_PERIOD = Duration.ofMillis(100);
    private static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

    /** 框架创建线程组时使用的线程数量。 */
    private final int eventLoopThreads;

    /** 优雅关闭静默期。 */
    private final Duration quietPeriod;

    /** 优雅关闭最大等待时间。 */
    private final Duration shutdownTimeout;

    /** 是否拥有并负责关闭线程组。 */
    private final boolean owned;

    /** 运行时关闭状态。 */
    private final AtomicBoolean closed = new AtomicBoolean();

    /** 惰性创建或由用户提供的线程组。 */
    private volatile EventLoopGroup eventLoopGroup;

    /**
     * 创建使用默认关闭策略的惰性 NIO 运行时。
     *
     * @param eventLoopThreads 事件线程数量
     */
    public NetRuntime(int eventLoopThreads) {
        this(eventLoopThreads, DEFAULT_QUIET_PERIOD, DEFAULT_SHUTDOWN_TIMEOUT);
    }

    /**
     * 创建使用指定关闭策略的惰性 NIO 运行时。
     *
     * @param eventLoopThreads 事件线程数量
     * @param quietPeriod 优雅关闭静默期
     * @param shutdownTimeout 优雅关闭最大等待时间
     */
    public NetRuntime(
            int eventLoopThreads,
            Duration quietPeriod,
            Duration shutdownTimeout) {
        if (eventLoopThreads <= 0) {
            throw new IllegalArgumentException("eventLoopThreads 必须大于 0");
        }
        requireNonNegative(quietPeriod, "quietPeriod");
        requirePositive(shutdownTimeout, "shutdownTimeout");
        if (quietPeriod.compareTo(shutdownTimeout) > 0) {
            throw new IllegalArgumentException("quietPeriod 不能大于 shutdownTimeout");
        }
        this.eventLoopThreads = eventLoopThreads;
        this.quietPeriod = quietPeriod;
        this.shutdownTimeout = shutdownTimeout;
        this.owned = true;
    }

    /**
     * 包装由用户管理生命周期的线程组。
     *
     * @param eventLoopGroup 用户提供的线程组
     */
    public NetRuntime(EventLoopGroup eventLoopGroup) {
        if (eventLoopGroup == null) {
            throw new IllegalArgumentException("eventLoopGroup 不能为空");
        }
        this.eventLoopThreads = 0;
        this.quietPeriod = Duration.ZERO;
        this.shutdownTimeout = Duration.ZERO;
        this.owned = false;
        this.eventLoopGroup = eventLoopGroup;
    }

    /**
     * 获取线程组，首次调用时才创建框架拥有的线程。
     *
     * @return 可用事件线程组
     * @throws NetException 运行时已经关闭时抛出
     */
    public EventLoopGroup eventLoopGroup() {
        if (closed.get()) {
            throw NetException.of(NetErrorCode.RUNTIME_CLOSED);
        }
        EventLoopGroup current = eventLoopGroup;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (closed.get()) {
                throw NetException.of(NetErrorCode.RUNTIME_CLOSED);
            }
            if (eventLoopGroup == null) {
                eventLoopGroup = new NioEventLoopGroup(
                        eventLoopThreads,
                        new DefaultThreadFactory("letool-net", false));
            }
            return eventLoopGroup;
        }
    }

    /**
     * 判断当前线程是否属于该运行时的任一 EventLoop。
     *
     * @return 当前线程属于事件线程时返回 {@code true}
     */
    public boolean isEventLoopThread() {
        EventLoopGroup current = eventLoopGroup;
        if (current == null) {
            return false;
        }
        for (EventExecutor executor : current) {
            if (executor.inEventLoop()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断框架线程组是否已经创建。
     *
     * @return 已持有线程组时返回 {@code true}
     */
    public boolean isInitialized() {
        return eventLoopGroup != null;
    }

    /**
     * 判断运行时是否已经关闭。
     *
     * @return 已关闭时返回 {@code true}
     */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * 关闭运行时；只关闭由框架创建的线程组。
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        EventLoopGroup current = eventLoopGroup;
        if (owned && current != null) {
            boolean eventLoopThread = isCurrentEventLoopThread(current);
            io.netty.util.concurrent.Future<?> shutdownFuture =
                    current.shutdownGracefully(
                            quietPeriod.toMillis(),
                            shutdownTimeout.toMillis(),
                            TimeUnit.MILLISECONDS);
            if (!eventLoopThread) {
                shutdownFuture.syncUninterruptibly();
            }
        }
    }

    /**
     * 判断当前线程是否属于指定事件线程组。
     *
     * @param group 待检查事件线程组
     * @return 当前线程属于该线程组时返回 {@code true}
     */
    private boolean isCurrentEventLoopThread(EventLoopGroup group) {
        for (EventExecutor executor : group) {
            if (executor.inEventLoop()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 校验时长非负。
     *
     * @param value 待校验时长
     * @param fieldName 字段名称
     */
    private static void requireNonNegative(Duration value, String fieldName) {
        if (value == null || value.isNegative()) {
            throw new IllegalArgumentException(fieldName + " 不能为负数");
        }
        requireMillisConvertible(value, fieldName);
    }

    /**
     * 校验时长为正数。
     *
     * @param value 待校验时长
     * @param fieldName 字段名称
     */
    private static void requirePositive(Duration value, String fieldName) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(fieldName + " 必须大于 0");
        }
        requireMillisConvertible(value, fieldName);
    }

    /**
     * 校验时长能够安全转换为 Netty 关闭流程使用的毫秒数。
     *
     * @param value 待校验时长
     * @param fieldName 字段名称
     */
    private static void requireMillisConvertible(Duration value, String fieldName) {
        try {
            value.toMillis();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(fieldName + " 超出毫秒范围", exception);
        }
    }
}
