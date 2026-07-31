package com.github.leyland.letool.net.tcp;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * 将调用方主动结束结果的动作同步回请求生命周期的可控 Future。
 *
 * <p>调用方取消、手动完成或通过超时组合器结束根 Future 时，底层请求也会立即清理；
 * 客户端内部完成结果时则不会触发反向取消。该类型仅供模块内部使用。</p>
 *
 * @param <T> 结果类型
 */
final class RequestFuture<T> extends CompletableFuture<T> {

    /** 确保外部终止回调最多执行一次。 */
    private final AtomicBoolean externalTerminationNotified = new AtomicBoolean();

    /** 外部主动结束 Future 时执行的请求清理动作。 */
    private volatile Runnable externalTerminationAction;

    /**
     * 注册外部终止时的请求清理动作。
     *
     * @param action 清理动作
     */
    void onExternalTermination(Runnable action) {
        if (action == null) {
            throw new IllegalArgumentException("action 不能为空");
        }
        this.externalTerminationAction = action;
    }

    /**
     * 由客户端内部正常完成结果，不触发反向清理。
     *
     * @param value 响应值
     * @return 本次调用完成 Future 时返回 {@code true}
     */
    boolean completeFromClient(T value) {
        return super.complete(value);
    }

    /**
     * 由客户端内部以异常结束结果，不触发反向清理。
     *
     * @param exception 失败异常
     * @return 本次调用完成 Future 时返回 {@code true}
     */
    boolean failFromClient(Throwable exception) {
        return super.completeExceptionally(exception);
    }

    /**
     * 调用方主动完成结果时同步清理底层请求。
     *
     * @param value 调用方提供的结果
     * @return 本次调用完成 Future 时返回 {@code true}
     */
    @Override
    public boolean complete(T value) {
        boolean completed = super.complete(value);
        if (completed) {
            notifyExternalTermination();
        }
        return completed;
    }

    /**
     * 调用方主动以异常结束结果时同步清理底层请求。
     *
     * @param exception 调用方提供的异常
     * @return 本次调用完成 Future 时返回 {@code true}
     */
    @Override
    public boolean completeExceptionally(Throwable exception) {
        boolean completed = super.completeExceptionally(exception);
        if (completed) {
            notifyExternalTermination();
        }
        return completed;
    }

    /**
     * 调用方取消结果时同步清理底层请求。
     *
     * @param mayInterruptIfRunning {@link CompletableFuture} 不使用该标记中断任务
     * @return 本次调用取消 Future 时返回 {@code true}
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean cancelled = super.cancel(mayInterruptIfRunning);
        if (cancelled) {
            notifyExternalTermination();
        }
        return cancelled;
    }

    /**
     * 调用方异步完成结果时同步清理底层请求。
     *
     * @param supplier 异步结果提供器
     * @return 当前请求 Future
     */
    @Override
    public CompletableFuture<T> completeAsync(Supplier<? extends T> supplier) {
        return completeAsync(supplier, defaultExecutor());
    }

    /**
     * 调用方通过指定执行器异步完成结果时同步清理底层请求。
     *
     * @param supplier 异步结果提供器
     * @param executor 执行异步任务的执行器
     * @return 当前请求 Future
     */
    @Override
    public CompletableFuture<T> completeAsync(
            Supplier<? extends T> supplier,
            Executor executor) {
        if (supplier == null) {
            throw new IllegalArgumentException("supplier 不能为空");
        }
        if (executor == null) {
            throw new IllegalArgumentException("executor 不能为空");
        }
        executor.execute(() -> {
            try {
                complete(supplier.get());
            } catch (RuntimeException | Error exception) {
                completeExceptionally(exception);
            }
        });
        return this;
    }

    /**
     * 禁止绕过正常完成协议强制覆盖结果。
     *
     * @param value 强制覆盖值
     * @throws UnsupportedOperationException 始终抛出
     */
    @Override
    public void obtrudeValue(T value) {
        throw new UnsupportedOperationException("请求 Future 不支持强制覆盖结果");
    }

    /**
     * 禁止绕过正常完成协议强制覆盖异常。
     *
     * @param exception 强制覆盖异常
     * @throws UnsupportedOperationException 始终抛出
     */
    @Override
    public void obtrudeException(Throwable exception) {
        throw new UnsupportedOperationException("请求 Future 不支持强制覆盖异常");
    }

    /**
     * 执行一次外部终止清理动作。
     */
    private void notifyExternalTermination() {
        if (!externalTerminationNotified.compareAndSet(false, true)) {
            return;
        }
        Runnable action = externalTerminationAction;
        if (action != null) {
            action.run();
        }
    }
}
