package com.github.leyland.letool.net.tcp;

import com.github.leyland.letool.net.exception.NetErrorCode;
import com.github.leyland.letool.net.exception.NetException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * 面向传统同步业务代码的 TCP 便捷门面。
 *
 * <p>该门面禁止在 Netty EventLoop 线程中使用，核心通信仍由异步客户端完成。</p>
 *
 * @param <REQ> 请求对象类型
 * @param <RESP> 响应对象类型
 */
public final class BlockingTcpClient<REQ, RESP> implements AutoCloseable {

    /** 底层异步客户端。 */
    private final TcpClient<REQ, RESP> delegate;

    /** 用于识别事件线程的共享运行时。 */
    private final NetRuntime runtime;

    /**
     * 创建同步门面。
     *
     * @param delegate 底层异步客户端
     * @param runtime 客户端使用的运行时
     */
    BlockingTcpClient(
            TcpClient<REQ, RESP> delegate,
            NetRuntime runtime) {
        this.delegate = delegate;
        this.runtime = runtime;
    }

    /**
     * 同步发送请求并等待响应。
     *
     * @param request 请求对象
     * @return 响应对象
     * @throws NetException 网络失败、等待超时或事件线程误用时抛出
     */
    public RESP request(REQ request) {
        if (runtime.isEventLoopThread()) {
            throw NetException.of(NetErrorCode.BLOCKING_ON_EVENT_LOOP);
        }
        CompletableFuture<RESP> responseFuture = delegate.request(request)
                .toCompletableFuture();
        try {
            return responseFuture.get();
        } catch (InterruptedException exception) {
            responseFuture.cancel(false);
            Thread.currentThread().interrupt();
            throw NetException.causedBy(NetErrorCode.CHANNEL_CLOSED, exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof NetException netException) {
                throw netException;
            }
            throw NetException.causedBy(NetErrorCode.CHANNEL_CLOSED, cause);
        }
    }

    /**
     * 判断底层客户端是否已经关闭。
     *
     * @return 已关闭时返回 {@code true}
     */
    public boolean isClosed() {
        return delegate.isClosed();
    }

    /**
     * 关闭底层异步客户端。
     */
    @Override
    public void close() {
        delegate.close();
    }
}
