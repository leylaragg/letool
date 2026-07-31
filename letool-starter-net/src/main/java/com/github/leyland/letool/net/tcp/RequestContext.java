package com.github.leyland.letool.net.tcp;

import io.netty.channel.Channel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ScheduledFuture;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 单次 TCP 请求从受理到响应结束的统一生命周期状态。
 *
 * <p>上下文登记当前可安全取消的建连、退避或写入任务，使客户端关闭和绝对期限能够
 * 终止尚未完成的网络动作。连接池获取由池自身排空，不能直接取消。该类型仅供模块
 * 内部使用。</p>
 *
 * @param <RESP> 响应对象类型
 */
final class RequestContext<RESP> {

    /** 防御性复制后的请求载荷。 */
    private final byte[] payload;

    /** 用户可见响应结果。 */
    private final RequestFuture<RESP> responseFuture;

    /** 使用 {@link System#nanoTime()} 记录的请求受理时刻。 */
    private final long acceptedAtNanos;

    /** 从请求受理开始计算的总超时纳秒数。 */
    private final long timeoutNanos;

    /** 确保关闭、超时、响应和断连只有一个终止路径。 */
    private final AtomicBoolean completed = new AtomicBoolean();

    /** 标记业务报文是否已经进入通道写出流程。 */
    private final AtomicBoolean writeStarted = new AtomicBoolean();

    /** 当前可安全取消的建连、退避或写入任务。 */
    private final AtomicReference<Future<?>> operationFuture = new AtomicReference<>();

    /** 请求绝对期限任务。 */
    private final AtomicReference<ScheduledFuture<?>> deadlineFuture =
            new AtomicReference<>();

    /** 当前请求独占的连接。 */
    private final AtomicReference<Channel> channel = new AtomicReference<>();

    /** 当前连接尝试序号，仅在事件线程绑定新连接时更新。 */
    private volatile int connectionAttempt;

    /**
     * 创建请求生命周期上下文。
     *
     * @param payload 已编码请求载荷
     * @param responseFuture 用户可见且可反向取消请求的响应结果
     * @param acceptedAtNanos 请求受理时刻
     * @param timeoutNanos 总超时纳秒数
     */
    RequestContext(
            byte[] payload,
            RequestFuture<RESP> responseFuture,
            long acceptedAtNanos,
            long timeoutNanos) {
        this.payload = Arrays.copyOf(payload, payload.length);
        this.responseFuture = responseFuture;
        this.acceptedAtNanos = acceptedAtNanos;
        this.timeoutNanos = timeoutNanos;
    }

    /**
     * 获取只供当前请求内部写出的载荷。
     *
     * @return 请求私有载荷
     */
    byte[] payload() {
        return payload;
    }

    /**
     * 获取用户可见响应结果。
     *
     * @return 响应结果
     */
    RequestFuture<RESP> responseFuture() {
        return responseFuture;
    }

    /**
     * 计算距绝对期限剩余的纳秒数。
     *
     * @return 正数表示尚有剩余时间，非正数表示已经到期
     */
    long remainingNanos() {
        long elapsedNanos = System.nanoTime() - acceptedAtNanos;
        return timeoutNanos - elapsedNanos;
    }

    /**
     * 判断请求是否已经终止。
     *
     * @return 已终止时返回 {@code true}
     */
    boolean isCompleted() {
        return completed.get();
    }

    /**
     * 尝试取得请求的唯一终止权。
     *
     * @return 首个终止路径返回 {@code true}
     */
    boolean claimCompletion() {
        return completed.compareAndSet(false, true);
    }

    /**
     * 标记业务报文已经开始写出。
     */
    void markWriteStarted() {
        writeStarted.set(true);
    }

    /**
     * 判断业务报文是否已经进入写出流程。
     *
     * @return 已开始写出时返回 {@code true}
     */
    boolean hasWriteStarted() {
        return writeStarted.get();
    }

    /**
     * 登记当前可安全取消的网络任务，并取消被替换的未完成任务。
     *
     * <p>固定连接池的 acquire Future 不得传入该方法；它必须由监听器排空并归还
     * 晚到连接。</p>
     *
     * @param future 当前网络任务
     */
    void trackOperation(Future<?> future) {
        Future<?> previous = operationFuture.getAndSet(future);
        if (previous != null && previous != future && !previous.isDone()) {
            previous.cancel(false);
        }
        if (completed.get() && !future.isDone()) {
            future.cancel(false);
        }
    }

    /**
     * 登记绝对期限任务。
     *
     * @param future 期限任务
     */
    void trackDeadline(ScheduledFuture<?> future) {
        ScheduledFuture<?> previous = deadlineFuture.getAndSet(future);
        if (previous != null && previous != future && !previous.isDone()) {
            previous.cancel(false);
        }
        if (completed.get() && !future.isDone()) {
            future.cancel(false);
        }
    }

    /**
     * 让当前请求取得一条独占连接。
     *
     * @param newChannel 新取得连接
     * @return 连接登记成功时返回 {@code true}
     */
    boolean attachChannel(Channel newChannel) {
        return channel.compareAndSet(null, newChannel);
    }

    /**
     * 记录当前连接尝试序号。
     *
     * @param attempt 连接尝试序号
     */
    void connectionAttempt(int attempt) {
        this.connectionAttempt = attempt;
    }

    /**
     * 获取当前连接尝试序号。
     *
     * @return 连接尝试序号
     */
    int connectionAttempt() {
        return connectionAttempt;
    }

    /**
     * 获取当前独占连接但不转移所有权。
     *
     * @return 当前连接；尚未取得连接时返回 {@code null}
     */
    Channel channel() {
        return channel.get();
    }

    /**
     * 原子移交当前独占连接的释放权。
     *
     * @return 当前连接；已经移交或尚未取得时返回 {@code null}
     */
    Channel detachChannel() {
        return channel.getAndSet(null);
    }

    /**
     * 取消期限以及尚未完成的网络任务。
     */
    void cancelPendingOperations() {
        ScheduledFuture<?> deadline = deadlineFuture.getAndSet(null);
        if (deadline != null && !deadline.isDone()) {
            deadline.cancel(false);
        }
        Future<?> operation = operationFuture.getAndSet(null);
        if (operation != null && !operation.isDone()) {
            operation.cancel(false);
        }
    }
}
