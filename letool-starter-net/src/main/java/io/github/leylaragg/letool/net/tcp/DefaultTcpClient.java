package io.github.leylaragg.letool.net.tcp;

import io.github.leylaragg.letool.net.exception.NetErrorCode;
import io.github.leylaragg.letool.net.exception.NetException;
import io.github.leylaragg.letool.net.protocol.PayloadCodec;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.ChannelGroupFuture;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.pool.AbstractChannelPoolHandler;
import io.netty.channel.pool.ChannelHealthChecker;
import io.netty.channel.pool.FixedChannelPool;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.concurrent.ScheduledFuture;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于 Netty 原生异步模型实现的 TCP 请求响应客户端。
 *
 * <p>客户端为每条连接提供单请求独占语义，并使用统一请求上下文覆盖编码、建连、连接
 * 获取、退避、写入和等待响应的完整生命周期。业务报文一旦交给通道写出便不会自动
 * 重放，避免非幂等请求被重复执行。</p>
 *
 * @param <REQ> 请求对象类型
 * @param <RESP> 响应对象类型
 */
final class DefaultTcpClient<REQ, RESP> implements TcpClient<REQ, RESP> {

    private static final String HEARTBEAT_HANDLER_NAME = "letoolHeartbeatHandler";
    private static final String RESPONSE_HANDLER_NAME = "letoolResponseHandler";

    /** 通道当前独占请求，用于响应匹配和异常清理。 */
    private static final AttributeKey<RequestContext<?>> CURRENT_REQUEST =
            AttributeKey.valueOf(DefaultTcpClient.class, "currentRequest");

    /** 所有客户端共享的网络线程运行时。 */
    private final NetRuntime runtime;

    /** 已完成边界校验的不可变客户端配置。 */
    private final TcpClientOptions options;

    /** 业务对象与完整报文载荷之间的编解码器。 */
    private final PayloadCodec<REQ, RESP> payloadCodec;

    /** 创建短连接或连接池底层通道的启动器。 */
    private final Bootstrap bootstrap;

    /** 客户端持有的全部通道；关闭后新加入的通道也会被立即关闭。 */
    private final ChannelGroup channels =
            new DefaultChannelGroup(GlobalEventExecutor.INSTANCE, true);

    /** 持久连接和连接池模式使用的有界连接池。 */
    private final FixedChannelPool channelPool;

    /** 已受理但尚未结束的请求数量。 */
    private final AtomicInteger outstandingRequests = new AtomicInteger();

    /** 客户端关闭状态。 */
    private final AtomicBoolean closed = new AtomicBoolean();

    /** 尚未结束的请求上下文集合。 */
    private final Set<RequestContext<RESP>> requests = ConcurrentHashMap.newKeySet();

    /**
     * 线性化关闭、超时、通道绑定和实际写出，防止终止后继续发送业务报文。
     */
    private final Object lifecycleMonitor = new Object();

    /**
     * 创建 TCP 客户端。
     *
     * @param runtime 共享网络运行时
     * @param options 已校验客户端配置
     * @param payloadCodec 线程安全的业务载荷编解码器
     */
    DefaultTcpClient(
            NetRuntime runtime,
            TcpClientOptions options,
            PayloadCodec<REQ, RESP> payloadCodec) {
        if (runtime == null) {
            throw new IllegalArgumentException("runtime 不能为空");
        }
        if (options == null) {
            throw new IllegalArgumentException("options 不能为空");
        }
        if (payloadCodec == null) {
            throw new IllegalArgumentException("payloadCodec 不能为空");
        }
        this.runtime = runtime;
        this.options = options;
        this.payloadCodec = payloadCodec;
        this.bootstrap = createBootstrap();
        this.channelPool = options.connectionMode() == ConnectionMode.SHORT
                ? null
                : createChannelPool();
    }

    /**
     * 受理异步请求并启动完整生命周期计时。
     *
     * @param request 请求对象
     * @return 最终响应或结构化网络异常
     */
    @Override
    public CompletionStage<RESP> request(REQ request) {
        if (closed.get()) {
            return CompletableFuture.failedFuture(
                    NetException.of(NetErrorCode.CLIENT_CLOSED));
        }
        if (!reserveRequestCapacity()) {
            return CompletableFuture.failedFuture(
                    NetException.of(NetErrorCode.REQUEST_OVERLOADED));
        }

        long acceptedAtNanos = System.nanoTime();
        RequestFuture<RESP> responseFuture = new RequestFuture<>();
        byte[] payload;
        try {
            payload = payloadCodec.encode(request);
            if (payload == null) {
                throw new IllegalArgumentException("PayloadCodec.encode 返回了 null");
            }
            if (payload.length > options.maxFrameLength()) {
                responseFuture.failFromClient(NetException.of(
                        NetErrorCode.FRAME_TOO_LARGE,
                        options.maxFrameLength()));
                outstandingRequests.decrementAndGet();
                return responseFuture;
            }
        } catch (NetException exception) {
            responseFuture.failFromClient(exception);
            outstandingRequests.decrementAndGet();
            return responseFuture;
        } catch (RuntimeException exception) {
            responseFuture.failFromClient(NetException.causedBy(
                    NetErrorCode.ENCODE_FAILED,
                    exception));
            outstandingRequests.decrementAndGet();
            return responseFuture;
        }

        RequestContext<RESP> context = new RequestContext<>(
                payload,
                responseFuture,
                acceptedAtNanos,
                options.requestTimeout().toNanos());
        responseFuture.whenComplete((ignoredResponse, ignoredFailure) -> {
            requests.remove(context);
            outstandingRequests.decrementAndGet();
        });
        responseFuture.onExternalTermination(() -> finishFailure(
                context,
                NetException.of(NetErrorCode.CHANNEL_CLOSED),
                true));

        synchronized (lifecycleMonitor) {
            if (closed.get()) {
                finishFailure(
                        context,
                        NetException.of(NetErrorCode.CLIENT_CLOSED),
                        true);
                return responseFuture;
            }
            requests.add(context);
        }

        if (!scheduleRequestDeadline(context)) {
            return responseFuture;
        }
        if (options.connectionMode() == ConnectionMode.SHORT) {
            connectShortChannel(context, 1);
        } else {
            acquirePooledChannel(context, 1);
        }
        return responseFuture;
    }

    /**
     * 获取客户端配置。
     *
     * @return 不可变客户端配置
     */
    @Override
    public TcpClientOptions options() {
        return options;
    }

    /**
     * 判断客户端是否已经关闭。
     *
     * @return 已关闭时返回 {@code true}
     */
    @Override
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * 原子关闭客户端并终止全部未完成请求。
     */
    @Override
    public void close() {
        ArrayList<RequestContext<RESP>> pendingContexts;
        synchronized (lifecycleMonitor) {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            pendingContexts = new ArrayList<>(requests);
        }

        for (RequestContext<RESP> context : pendingContexts) {
            finishFailure(
                    context,
                    NetException.of(NetErrorCode.CLIENT_CLOSED),
                    true);
        }

        boolean eventLoopThread = runtime.isEventLoopThread();
        Future<Void> poolCloseFuture =
                channelPool == null ? null : channelPool.closeAsync();
        ChannelGroupFuture channelCloseFuture = channels.close();
        if (!eventLoopThread) {
            if (poolCloseFuture != null) {
                poolCloseFuture.awaitUninterruptibly();
            }
            channelCloseFuture.awaitUninterruptibly();
        }
    }

    /**
     * 创建配置了端点和套接字参数的 Netty 启动器。
     *
     * @return 可供短连接和连接池复制使用的启动器
     */
    private Bootstrap createBootstrap() {
        int connectTimeoutMillis = Math.toIntExact(options.connectTimeout().toMillis());
        return new Bootstrap()
                .group(runtime.eventLoopGroup())
                .channel(NioSocketChannel.class)
                .remoteAddress(new InetSocketAddress(options.host(), options.port()))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis)
                .option(ChannelOption.TCP_NODELAY, options.tcpNoDelay())
                .option(ChannelOption.SO_KEEPALIVE, options.keepAlive())
                .handler(new ChannelInitializer<Channel>() {
                    /**
                     * 为短连接安装完整流水线。
                     *
                     * @param channel 新建立的短连接
                     */
                    @Override
                    protected void initChannel(Channel channel) {
                        initializeChannel(channel);
                    }
                });
    }

    /**
     * 创建持久连接或多连接模式使用的有界连接池。
     *
     * @return 使用获取超时和健康检查的固定容量连接池
     */
    private FixedChannelPool createChannelPool() {
        return new FixedChannelPool(
                bootstrap,
                new AbstractChannelPoolHandler() {
                    /**
                     * 为连接池创建的新连接安装完整流水线。
                     *
                     * @param channel 新连接
                     */
                    @Override
                    public void channelCreated(Channel channel) {
                        initializeChannel(channel);
                    }
                },
                ChannelHealthChecker.ACTIVE,
                FixedChannelPool.AcquireTimeoutAction.FAIL,
                options.acquireTimeout().toMillis(),
                options.maxConnections(),
                options.maxPendingRequests(),
                true,
                true);
    }

    /**
     * 为请求保留一个有界容量名额。
     *
     * @return 成功保留名额时返回 {@code true}
     */
    private boolean reserveRequestCapacity() {
        int capacity = options.connectionMode() == ConnectionMode.SHORT
                ? options.maxConnections()
                : Math.addExact(
                        options.maxConnections(),
                        options.maxPendingRequests());
        while (true) {
            int current = outstandingRequests.get();
            if (current >= capacity) {
                return false;
            }
            if (outstandingRequests.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    /**
     * 从请求受理时刻开始安排绝对期限。
     *
     * @param context 请求上下文
     * @return 已成功安排或请求仍由终止路径接管时返回 {@code true}
     */
    private boolean scheduleRequestDeadline(RequestContext<RESP> context) {
        long remainingNanos = context.remainingNanos();
        if (remainingNanos <= 0) {
            finishRequestTimeout(context);
            return false;
        }
        try {
            ScheduledFuture<?> deadlineFuture = runtime.eventLoopGroup()
                    .next()
                    .schedule(
                            () -> finishRequestTimeout(context),
                            remainingNanos,
                            TimeUnit.NANOSECONDS);
            context.trackDeadline(deadlineFuture);
            return !context.isCompleted();
        } catch (RuntimeException exception) {
            finishFailure(
                    context,
                    mapRuntimeFailure(exception),
                    true);
            return false;
        }
    }

    /**
     * 为短连接模式发起一次连接。
     *
     * @param context 请求上下文
     * @param attempt 当前尝试序号，从 1 开始
     */
    private void connectShortChannel(RequestContext<RESP> context, int attempt) {
        if (shouldStopBeforeNetworkOperation(context)) {
            return;
        }
        ChannelFuture connectFuture;
        try {
            connectFuture = bootstrap.connect();
            context.trackOperation(connectFuture);
        } catch (RuntimeException exception) {
            handleConnectFailure(context, attempt, exception, false);
            return;
        }

        connectFuture.addListener(completed -> {
            if (!completed.isSuccess()) {
                if (!context.isCompleted()) {
                    handleConnectFailure(
                            context,
                            attempt,
                            safeCause(completed.cause()),
                            false);
                }
                return;
            }

            Channel channel = connectFuture.channel();
            if (context.isCompleted() || closed.get()) {
                releaseChannel(channel, true);
                return;
            }
            beginRequest(channel, context, attempt);
        });
    }

    /**
     * 从固定连接池获取一条独占连接。
     *
     * @param context 请求上下文
     * @param attempt 当前尝试序号，从 1 开始
     */
    private void acquirePooledChannel(RequestContext<RESP> context, int attempt) {
        if (shouldStopBeforeNetworkOperation(context)) {
            return;
        }
        Future<Channel> acquireFuture;
        try {
            acquireFuture = channelPool.acquire();
        } catch (RuntimeException exception) {
            handleConnectFailure(context, attempt, exception, true);
            return;
        }

        acquireFuture.addListener(completed -> {
            if (!completed.isSuccess()) {
                if (!context.isCompleted()) {
                    handleConnectFailure(
                            context,
                            attempt,
                            safeCause(completed.cause()),
                            true);
                }
                return;
            }

            Channel channel = acquireFuture.getNow();
            if (context.isCompleted() || closed.get()) {
                releaseChannel(channel, closed.get());
                return;
            }
            beginRequest(channel, context, attempt);
        });
    }

    /**
     * 在业务报文写出前处理连接或连接获取失败。
     *
     * @param context 请求上下文
     * @param attempt 已失败的尝试序号
     * @param cause 底层失败原因
     * @param pooledFailure 是否来自连接池获取
     */
    private void handleConnectFailure(
            RequestContext<RESP> context,
            int attempt,
            Throwable cause,
            boolean pooledFailure) {
        if (context.isCompleted()) {
            return;
        }
        if (closed.get()) {
            finishFailure(
                    context,
                    NetException.of(NetErrorCode.CLIENT_CLOSED),
                    true);
            return;
        }
        if (isRetriableConnectFailure(cause)
                && scheduleConnectRetry(
                        context,
                        attempt,
                        pooledFailure
                                ? () -> acquirePooledChannel(context, attempt + 1)
                                : () -> connectShortChannel(context, attempt + 1))) {
            return;
        }

        NetException exception = pooledFailure
                ? mapAcquireFailure(cause)
                : NetException.causedBy(
                        NetErrorCode.CONNECT_FAILED,
                        cause,
                        options.host(),
                        options.port());
        finishFailure(context, exception, true);
    }

    /**
     * 判断失败是否适合在业务写出前重试。
     *
     * @param cause 底层失败原因
     * @return 连接类瞬时故障时返回 {@code true}
     */
    private boolean isRetriableConnectFailure(Throwable cause) {
        return cause instanceof IOException
                || cause instanceof ClosedChannelException
                || cause instanceof TimeoutException;
    }

    /**
     * 按退避策略安排下一次建连或连接获取。
     *
     * @param context 请求上下文
     * @param failedAttempt 已失败的尝试序号
     * @param retryAction 下一次尝试动作
     * @return 已安排重试或将由请求期限终止时返回 {@code true}
     */
    private boolean scheduleConnectRetry(
            RequestContext<RESP> context,
            int failedAttempt,
            Runnable retryAction) {
        if (failedAttempt >= options.connectRetryPolicy().maxAttempts()) {
            return false;
        }

        Duration delay = options.connectRetryPolicy().delayAfterFailure(failedAttempt);
        long delayMillis = delay.toMillis();
        long remainingNanos = context.remainingNanos();
        if (remainingNanos <= 0) {
            finishRequestTimeout(context);
            return true;
        }
        if (TimeUnit.MILLISECONDS.toNanos(delayMillis) >= remainingNanos) {
            // 绝对期限任务会更早结束请求，无需安排一个必然不会执行的重试。
            return true;
        }

        try {
            ScheduledFuture<?> retryFuture = runtime.eventLoopGroup()
                    .next()
                    .schedule(() -> {
                        if (context.isCompleted()) {
                            return;
                        }
                        if (closed.get()) {
                            finishFailure(
                                    context,
                                    NetException.of(NetErrorCode.CLIENT_CLOSED),
                                    true);
                            return;
                        }
                        if (context.remainingNanos() <= 0) {
                            finishRequestTimeout(context);
                            return;
                        }
                        retryAction.run();
                    }, delayMillis, TimeUnit.MILLISECONDS);
            context.trackOperation(retryFuture);
            return true;
        } catch (RuntimeException exception) {
            finishFailure(
                    context,
                    mapRuntimeFailure(exception),
                    true);
            return true;
        }
    }

    /**
     * 将连接交给当前请求并等待心跳交互结束后写出。
     *
     * @param channel 已取得的独占连接
     * @param context 请求上下文
     * @param attempt 当前连接尝试序号
     */
    private void beginRequest(
            Channel channel,
            RequestContext<RESP> context,
            int attempt) {
        if (!channel.isActive()) {
            releaseChannel(channel, true);
            if (!scheduleConnectRetry(
                    context,
                    attempt,
                    options.connectionMode() == ConnectionMode.SHORT
                            ? () -> connectShortChannel(context, attempt + 1)
                            : () -> acquirePooledChannel(context, attempt + 1))) {
                finishFailure(
                        context,
                        NetException.of(NetErrorCode.CHANNEL_CLOSED),
                        true);
            }
            return;
        }

        NetException failure = null;
        synchronized (lifecycleMonitor) {
            if (closed.get()) {
                failure = NetException.of(NetErrorCode.CLIENT_CLOSED);
            } else if (context.isCompleted()) {
                failure = NetException.of(NetErrorCode.CHANNEL_CLOSED);
            } else if (context.remainingNanos() <= 0) {
                failure = requestTimeoutException();
            } else if (!context.attachChannel(channel)) {
                failure = NetException.of(NetErrorCode.INTERNAL_STATE_ERROR);
            } else {
                context.connectionAttempt(attempt);
                Attribute<RequestContext<?>> attribute = channel.attr(CURRENT_REQUEST);
                if (!attribute.compareAndSet(null, context)) {
                    failure = NetException.of(NetErrorCode.INTERNAL_STATE_ERROR);
                }
            }
        }
        if (failure != null) {
            if (context.channel() == null) {
                releaseChannel(channel, true);
            }
            finishFailure(context, failure, true);
            return;
        }

        HeartbeatHandler heartbeatHandler = (HeartbeatHandler) channel.pipeline()
                .get(HEARTBEAT_HANDLER_NAME);
        Runnable writeAction = () -> writeRequest(channel, context);
        if (heartbeatHandler == null) {
            writeAction.run();
        } else {
            heartbeatHandler.runWhenReady(writeAction);
        }
    }

    /**
     * 在关闭和绝对期限的同一线性化边界内写出业务报文。
     *
     * @param channel 当前请求独占连接
     * @param context 请求上下文
     */
    private void writeRequest(Channel channel, RequestContext<RESP> context) {
        NetException failure = null;
        ChannelFuture writeFuture = null;
        synchronized (lifecycleMonitor) {
            if (closed.get()) {
                failure = NetException.of(NetErrorCode.CLIENT_CLOSED);
            } else if (context.isCompleted()) {
                return;
            } else if (context.remainingNanos() <= 0) {
                failure = requestTimeoutException();
            } else if (!channel.isActive()
                    || channel.attr(CURRENT_REQUEST).get() != context) {
                failure = NetException.of(NetErrorCode.CHANNEL_CLOSED);
            } else {
                try {
                    // 从此刻起底层处理器可能已经观察到报文，任何失败都不得自动重放。
                    context.markWriteStarted();
                    writeFuture = channel.writeAndFlush(
                            Unpooled.wrappedBuffer(context.payload()));
                    context.trackOperation(writeFuture);
                } catch (RuntimeException exception) {
                    failure = NetException.causedBy(
                            NetErrorCode.WRITE_FAILED,
                            exception);
                }
            }
        }
        if (failure != null) {
            finishFailure(context, failure, true);
            return;
        }

        ChannelFuture trackedWriteFuture = writeFuture;
        trackedWriteFuture.addListener(completed -> {
            if (!completed.isSuccess()) {
                finishFailure(
                        context,
                        NetException.causedBy(
                                NetErrorCode.WRITE_FAILED,
                                safeCause(completed.cause())),
                        true);
            }
        });
    }

    /**
     * 初始化分层明确的通道流水线。
     *
     * @param channel 新建立连接
     */
    private void initializeChannel(Channel channel) {
        ChannelPipeline pipeline = channel.pipeline();
        if (pipeline.get(RESPONSE_HANDLER_NAME) != null) {
            return;
        }

        channels.add(channel);
        options.wirePipelineCustomizer().customize(pipeline);
        options.frameCodec().configure(pipeline, options.maxFrameLength());
        options.pipelineCustomizer().customize(pipeline);
        HeartbeatStrategy heartbeatStrategy = options.heartbeatStrategy();
        if (heartbeatStrategy != null) {
            pipeline.addLast(new IdleStateHandler(
                    0,
                    heartbeatStrategy.idleInterval().toMillis(),
                    0,
                    TimeUnit.MILLISECONDS));
            pipeline.addLast(
                    HEARTBEAT_HANDLER_NAME,
                    new HeartbeatHandler(
                            heartbeatStrategy,
                            options.maxFrameLength()));
        }
        pipeline.addLast(RESPONSE_HANDLER_NAME, new ResponseHandler());
    }

    /**
     * 完成一个成功请求。
     *
     * @param context 请求上下文
     * @param response 已解码响应
     */
    private void finishSuccess(RequestContext<RESP> context, RESP response) {
        Channel channel;
        synchronized (lifecycleMonitor) {
            if (!context.claimCompletion()) {
                return;
            }
            context.cancelPendingOperations();
            channel = detachContextChannel(context);
        }
        if (channel != null) {
            releaseChannel(channel, false);
        }
        context.responseFuture().completeFromClient(response);
    }

    /**
     * 以结构化异常结束请求。
     *
     * @param context 请求上下文
     * @param exception 对外异常
     * @param brokenChannel 是否应丢弃当前连接
     */
    private void finishFailure(
            RequestContext<RESP> context,
            NetException exception,
            boolean brokenChannel) {
        Channel channel;
        synchronized (lifecycleMonitor) {
            if (!context.claimCompletion()) {
                return;
            }
            context.cancelPendingOperations();
            channel = detachContextChannel(context);
        }
        if (channel != null) {
            releaseChannel(channel, brokenChannel);
        }
        context.responseFuture().failFromClient(exception);
    }

    /**
     * 从请求上下文原子移交通道并清理通道属性。
     *
     * @param context 请求上下文
     * @return 需要释放的通道；没有已绑定通道时返回 {@code null}
     */
    private Channel detachContextChannel(RequestContext<RESP> context) {
        Channel channel = context.detachChannel();
        if (channel != null) {
            channel.attr(CURRENT_REQUEST).compareAndSet(context, null);
        }
        return channel;
    }

    /**
     * 结束已经到达绝对期限的请求。
     *
     * @param context 请求上下文
     */
    private void finishRequestTimeout(RequestContext<RESP> context) {
        finishFailure(context, requestTimeoutException(), true);
    }

    /**
     * 心跳在业务报文写出前失败时淘汰旧连接并安全重试当前请求。
     *
     * @param context 请求上下文
     * @param heartbeatFailure 心跳失败异常
     * @return 已接管失败并安排重试时返回 {@code true}
     */
    private boolean retryAfterPreWriteHeartbeatFailure(
            RequestContext<RESP> context,
            NetException heartbeatFailure) {
        if (context.hasWriteStarted() || context.isCompleted()) {
            return false;
        }

        Channel channel;
        synchronized (lifecycleMonitor) {
            if (context.hasWriteStarted() || context.isCompleted()) {
                return false;
            }
            channel = detachContextChannel(context);
        }
        if (channel != null) {
            releaseChannel(channel, true);
        }

        int failedAttempt = context.connectionAttempt();
        boolean scheduled = scheduleConnectRetry(
                context,
                failedAttempt,
                options.connectionMode() == ConnectionMode.SHORT
                        ? () -> connectShortChannel(context, failedAttempt + 1)
                        : () -> acquirePooledChannel(context, failedAttempt + 1));
        if (!scheduled) {
            finishFailure(context, heartbeatFailure, true);
        }
        return true;
    }

    /**
     * 创建包含配置时长的请求超时异常。
     *
     * @return 请求超时异常
     */
    private NetException requestTimeoutException() {
        return NetException.of(
                NetErrorCode.REQUEST_TIMEOUT,
                options.requestTimeout().toMillis());
    }

    /**
     * 判断是否应在发起下一项网络动作前终止。
     *
     * @param context 请求上下文
     * @return 不应继续操作时返回 {@code true}
     */
    private boolean shouldStopBeforeNetworkOperation(RequestContext<RESP> context) {
        if (context.isCompleted()) {
            return true;
        }
        if (closed.get()) {
            finishFailure(
                    context,
                    NetException.of(NetErrorCode.CLIENT_CLOSED),
                    true);
            return true;
        }
        if (context.remainingNanos() <= 0) {
            finishRequestTimeout(context);
            return true;
        }
        return false;
    }

    /**
     * 按连接模式释放或关闭独占连接。
     *
     * @param channel 待释放连接
     * @param broken 是否必须丢弃连接
     */
    private void releaseChannel(Channel channel, boolean broken) {
        if (options.connectionMode() == ConnectionMode.SHORT || channelPool == null) {
            channel.close();
            return;
        }
        if (broken || closed.get() || !channel.isActive()) {
            channel.close().addListener(ignored -> releasePooledChannel(channel));
            return;
        }
        releasePooledChannel(channel);
    }

    /**
     * 将连接归还固定连接池，归还失败时关闭连接。
     *
     * @param channel 待归还连接
     */
    private void releasePooledChannel(Channel channel) {
        try {
            channelPool.release(channel).addListener(completed -> {
                if (!completed.isSuccess()) {
                    channel.close();
                }
            });
        } catch (RuntimeException exception) {
            // 连接池关闭或内部状态异常时仍保证底层通道不会泄漏。
            channel.close();
        }
    }

    /**
     * 将连接池获取失败映射为稳定异常。
     *
     * @param cause 底层失败原因
     * @return 结构化网络异常
     */
    private NetException mapAcquireFailure(Throwable cause) {
        if (cause instanceof TimeoutException
                || cause.getClass().getSimpleName().contains("AcquireTimeout")) {
            return NetException.causedBy(
                    NetErrorCode.ACQUIRE_TIMEOUT,
                    cause);
        }
        return NetException.causedBy(
                NetErrorCode.CONNECT_FAILED,
                cause,
                options.host(),
                options.port());
    }

    /**
     * 将运行时调度失败映射为稳定异常。
     *
     * @param exception 调度阶段运行时异常
     * @return 结构化网络异常
     */
    private NetException mapRuntimeFailure(RuntimeException exception) {
        if (exception instanceof NetException netException) {
            return netException;
        }
        return NetException.causedBy(NetErrorCode.CHANNEL_CLOSED, exception);
    }

    /**
     * 为缺失底层原因的失败构造可诊断原因，避免异常工厂收到空值。
     *
     * @param cause Netty Future 提供的失败原因
     * @return 非空失败原因
     */
    private static Throwable safeCause(Throwable cause) {
        return cause == null
                ? new IllegalStateException("Netty Future 失败但未提供 cause")
                : cause;
    }

    /**
     * 接收完整业务报文并完成当前独占请求。
     */
    private final class ResponseHandler extends SimpleChannelInboundHandler<ByteBuf> {

        /**
         * 解码完整响应并结束当前请求。
         *
         * @param context 通道上下文
         * @param message 已完成分帧的业务报文
         */
        @Override
        protected void channelRead0(ChannelHandlerContext context, ByteBuf message) {
            RequestContext<RESP> requestContext = currentRequest(context.channel());
            if (requestContext == null) {
                context.close();
                return;
            }

            byte[] payload = ByteBufUtil.getBytes(
                    message,
                    message.readerIndex(),
                    message.readableBytes(),
                    true);
            try {
                RESP response = payloadCodec.decode(payload);
                finishSuccess(requestContext, response);
            } catch (NetException exception) {
                finishFailure(requestContext, exception, true);
            } catch (RuntimeException exception) {
                finishFailure(
                        requestContext,
                        NetException.causedBy(
                                NetErrorCode.DECODE_FAILED,
                                exception),
                        true);
            }
        }

        /**
         * 在连接提前关闭时结束当前请求。
         *
         * @param context 通道上下文
         */
        @Override
        public void channelInactive(ChannelHandlerContext context) {
            RequestContext<RESP> requestContext = currentRequest(context.channel());
            if (requestContext != null) {
                finishFailure(
                        requestContext,
                        NetException.of(closed.get()
                                ? NetErrorCode.CLIENT_CLOSED
                                : NetErrorCode.CHANNEL_CLOSED),
                        true);
            }
            context.fireChannelInactive();
        }

        /**
         * 将流水线异常转换为稳定网络异常。
         *
         * @param context 通道上下文
         * @param cause 流水线异常
         */
        @Override
        public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
            RequestContext<RESP> requestContext = currentRequest(context.channel());
            if (requestContext != null) {
                NetException exception;
                if (cause instanceof NetException netException) {
                    exception = netException;
                } else if (containsTooLongFrame(cause)) {
                    exception = NetException.causedBy(
                            NetErrorCode.FRAME_TOO_LARGE,
                            cause,
                            options.maxFrameLength());
                } else {
                    exception = NetException.causedBy(
                            NetErrorCode.CHANNEL_CLOSED,
                            cause);
                }
                if ((exception.getCode().equals(
                        NetErrorCode.HEARTBEAT_FAILED.getCode())
                        || exception.getCode().equals(
                        NetErrorCode.HEARTBEAT_TIMEOUT.getCode()))
                        && retryAfterPreWriteHeartbeatFailure(
                                requestContext,
                                exception)) {
                    context.close();
                    return;
                }
                finishFailure(requestContext, exception, true);
            }
            context.close();
        }
    }

    /**
     * 处理应用层心跳、应答期限和业务请求写出互斥。
     */
    private static final class HeartbeatHandler
            extends io.netty.channel.ChannelInboundHandlerAdapter {

        /** 当前连接的用户心跳策略。 */
        private final HeartbeatStrategy strategy;

        /** 单个心跳载荷允许使用的最大字节数。 */
        private final int maxFrameLength;

        /** 当前心跳是否仍在等待应答。 */
        private boolean awaitingResponse;

        /** 连续未收到应答的心跳次数。 */
        private int missedResponses;

        /** 心跳交互结束后待执行的业务写出动作。 */
        private Runnable readyAction;

        /** 当前心跳应答期限任务。 */
        private ScheduledFuture<?> responseTimeoutFuture;

        /**
         * 创建心跳处理器。
         *
         * @param strategy 用户心跳策略
         * @param maxFrameLength 最大业务报文长度
         */
        private HeartbeatHandler(
                HeartbeatStrategy strategy,
                int maxFrameLength) {
            this.strategy = strategy;
            this.maxFrameLength = maxFrameLength;
        }

        /**
         * 在没有未完成心跳交互时执行一次业务写出。
         *
         * @param action 业务写出动作
         */
        private void runWhenReady(Runnable action) {
            if (awaitingResponse) {
                readyAction = action;
                return;
            }
            action.run();
        }

        /**
         * 识别并消费心跳应答，其他报文继续交给响应处理器。
         *
         * @param context 通道上下文
         * @param message 入站消息
         */
        @Override
        public void channelRead(ChannelHandlerContext context, Object message) {
            if (!(message instanceof ByteBuf buffer) || !awaitingResponse) {
                context.fireChannelRead(message);
                return;
            }

            byte[] payload = ByteBufUtil.getBytes(
                    buffer,
                    buffer.readerIndex(),
                    buffer.readableBytes(),
                    true);
            boolean heartbeatResponse;
            try {
                heartbeatResponse = strategy.isHeartbeatResponse(payload);
            } catch (RuntimeException exception) {
                ReferenceCountUtil.release(message);
                failHeartbeat(
                        context,
                        NetException.causedBy(
                                NetErrorCode.HEARTBEAT_FAILED,
                                exception));
                return;
            }
            if (!heartbeatResponse) {
                context.fireChannelRead(message);
                return;
            }

            ReferenceCountUtil.release(message);
            acknowledgeHeartbeat();
        }

        /**
         * 在连接写空闲且没有业务请求时发送应用层心跳。
         *
         * @param context 通道上下文
         * @param event 用户事件
         */
        @Override
        public void userEventTriggered(ChannelHandlerContext context, Object event) {
            if (event instanceof IdleStateEvent idleStateEvent
                    && idleStateEvent.state() == IdleState.WRITER_IDLE) {
                if (!awaitingResponse
                        && context.channel().attr(CURRENT_REQUEST).get() == null) {
                    sendHeartbeat(context);
                }
                return;
            }
            context.fireUserEventTriggered(event);
        }

        /**
         * 创建并从通道尾部写出心跳，使自定义出站处理器能够生效。
         *
         * @param context 通道上下文
         */
        private void sendHeartbeat(ChannelHandlerContext context) {
            byte[] heartbeatPayload;
            try {
                heartbeatPayload = strategy.heartbeatPayload();
                if (heartbeatPayload == null || heartbeatPayload.length == 0) {
                    throw new IllegalArgumentException(
                            "HeartbeatStrategy.heartbeatPayload 不能为空");
                }
                if (heartbeatPayload.length > maxFrameLength) {
                    throw new IllegalArgumentException(
                            "心跳载荷超过 maxFrameLength");
                }
            } catch (RuntimeException exception) {
                failHeartbeat(
                        context,
                        NetException.causedBy(
                                NetErrorCode.HEARTBEAT_FAILED,
                                exception));
                return;
            }

            awaitingResponse = true;
            ChannelPromise promise = context.channel().newPromise();
            context.channel().writeAndFlush(
                    Unpooled.copiedBuffer(heartbeatPayload),
                    promise);
            promise.addListener(completed -> {
                if (!completed.isSuccess()) {
                    awaitingResponse = false;
                    failHeartbeat(
                            context,
                            NetException.causedBy(
                                    NetErrorCode.HEARTBEAT_FAILED,
                                    safeCause(completed.cause())));
                    return;
                }
                if (awaitingResponse) {
                    scheduleHeartbeatResponseTimeout(context);
                }
            });
        }

        /**
         * 安排当前心跳的应答期限。
         *
         * @param context 通道上下文
         */
        private void scheduleHeartbeatResponseTimeout(ChannelHandlerContext context) {
            cancelHeartbeatResponseTimeout();
            try {
                responseTimeoutFuture = context.executor().schedule(() -> {
                    if (!awaitingResponse) {
                        return;
                    }
                    missedResponses++;
                    if (missedResponses >= strategy.maxMissedResponses()) {
                        failHeartbeat(
                                context,
                                NetException.of(
                                        NetErrorCode.HEARTBEAT_TIMEOUT,
                                        missedResponses));
                    } else {
                        /*
                         * 无关联标识的协议不能并行发送下一次心跳，也不能解除隔离；
                         * 继续等待同一个 ACK，避免迟到 ACK 被业务响应处理器消费。
                         */
                        scheduleHeartbeatResponseTimeout(context);
                    }
                }, strategy.responseTimeout().toMillis(), TimeUnit.MILLISECONDS);
            } catch (RuntimeException exception) {
                failHeartbeat(
                        context,
                        NetException.causedBy(
                                NetErrorCode.HEARTBEAT_FAILED,
                                exception));
            }
        }

        /**
         * 接收有效应答并恢复可能等待中的业务写出。
         */
        private void acknowledgeHeartbeat() {
            cancelHeartbeatResponseTimeout();
            awaitingResponse = false;
            missedResponses = 0;
            Runnable action = readyAction;
            readyAction = null;
            if (action != null) {
                action.run();
            }
        }

        /**
         * 传播心跳异常并关闭连接。
         *
         * @param context 通道上下文
         * @param exception 结构化心跳异常
         */
        private void failHeartbeat(
                ChannelHandlerContext context,
                NetException exception) {
            cancelHeartbeatResponseTimeout();
            awaitingResponse = false;
            readyAction = null;
            context.fireExceptionCaught(exception);
            context.close();
        }

        /**
         * 取消当前心跳应答期限。
         */
        private void cancelHeartbeatResponseTimeout() {
            ScheduledFuture<?> timeoutFuture = responseTimeoutFuture;
            responseTimeoutFuture = null;
            if (timeoutFuture != null && !timeoutFuture.isDone()) {
                timeoutFuture.cancel(false);
            }
        }

        /**
         * 通道关闭时清理心跳期限。
         *
         * @param context 通道上下文
         */
        @Override
        public void channelInactive(ChannelHandlerContext context) {
            cancelHeartbeatResponseTimeout();
            readyAction = null;
            context.fireChannelInactive();
        }

        /**
         * 处理器被移除时清理心跳期限。
         *
         * @param context 通道上下文
         */
        @Override
        public void handlerRemoved(ChannelHandlerContext context) {
            cancelHeartbeatResponseTimeout();
            readyAction = null;
        }
    }

    /**
     * 读取通道当前请求并恢复泛型类型。
     *
     * @param channel 当前通道
     * @return 当前请求；通道空闲时返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    private RequestContext<RESP> currentRequest(Channel channel) {
        return (RequestContext<RESP>) channel.attr(CURRENT_REQUEST).get();
    }

    /**
     * 判断异常链中是否包含超长报文异常。
     *
     * @param cause 异常链起点
     * @return 包含超长报文异常时返回 {@code true}
     */
    private boolean containsTooLongFrame(Throwable cause) {
        Throwable current = cause;
        while (current != null) {
            if (current instanceof TooLongFrameException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
