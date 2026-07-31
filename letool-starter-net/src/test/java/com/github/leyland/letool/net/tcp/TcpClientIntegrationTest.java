package com.github.leyland.letool.net.tcp;

import com.github.leyland.letool.net.exception.NetErrorCode;
import com.github.leyland.letool.net.exception.NetException;
import com.github.leyland.letool.net.protocol.LengthFieldFrameCodec;
import com.github.leyland.letool.net.protocol.PayloadCodec;
import com.github.leyland.letool.net.protocol.PayloadCodecs;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPromise;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.util.ReferenceCountUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 基于真实本地 TCP 服务的客户端集成测试。
 */
class TcpClientIntegrationTest {

    private final AtomicBoolean respond = new AtomicBoolean(true);
    private final AtomicBoolean respondHeartbeat = new AtomicBoolean(true);
    private final AtomicInteger heartbeatCount = new AtomicInteger();
    private final AtomicInteger heartbeatResponseDelayMillis = new AtomicInteger();
    private final AtomicInteger connectionCount = new AtomicInteger();
    private EventLoopGroup serverGroup;
    private Channel serverChannel;
    private Channel secondaryServerChannel;
    private NetRuntime runtime;
    private TcpClientFactory clientFactory;

    /**
     * 启动使用四字节长度字段的本地回显服务。
     */
    @BeforeEach
    void setUp() throws InterruptedException {
        serverGroup = new NioEventLoopGroup(1);
        serverChannel = bindEchoServer(0);
        runtime = new NetRuntime(1);
        clientFactory = new TcpClientFactory(runtime);
    }

    /**
     * 在指定端口启动测试回显服务。
     *
     * @param port 监听端口；传入 0 时由操作系统分配
     * @return 已绑定服务端通道
     * @throws InterruptedException 等待端口绑定时线程被中断
     */
    private Channel bindEchoServer(int port) throws InterruptedException {
        return new ServerBootstrap()
                .group(serverGroup)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    /**
                     * 为测试连接安装与客户端一致的长度字段协议。
                     *
                     * @param channel 服务端连接
                     */
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        channel.pipeline()
                                .addLast(new LengthFieldBasedFrameDecoder(
                                        1024 * 1024,
                                        0,
                                        Integer.BYTES,
                                        0,
                                        Integer.BYTES))
                                .addLast(new LengthFieldPrepender(Integer.BYTES))
                                .addLast(new EchoHandler(
                                        respond,
                                        respondHeartbeat,
                                        heartbeatCount,
                                        heartbeatResponseDelayMillis,
                                        connectionCount));
                    }
                })
                .bind(port)
                .sync()
                .channel();
    }

    /**
     * 关闭测试客户端运行时和服务端线程资源。
     */
    @AfterEach
    void tearDown() {
        if (runtime != null) {
            runtime.close();
        }
        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }
        if (secondaryServerChannel != null) {
            secondaryServerChannel.close().syncUninterruptibly();
        }
        if (serverGroup != null) {
            serverGroup.shutdownGracefully(0, 1, TimeUnit.SECONDS)
                    .syncUninterruptibly();
        }
    }

    /**
     * 验证单条持久连接上的并发请求会被安全串行化，响应不会串包。
     */
    @Test
    void shouldSerializeConcurrentRequestsOnPersistentConnection() {
        try (TcpClient<byte[], byte[]> client = clientFactory.create(options(
                ConnectionMode.PERSISTENT,
                Duration.ofSeconds(3)))) {
            List<CompletableFuture<byte[]>> requests = new ArrayList<>();
            IntStream.range(0, 50).forEach(index -> requests.add(
                    client.request(bytes("message-" + index)).toCompletableFuture()));

            CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).join();

            for (int index = 0; index < requests.size(); index++) {
                assertThat(text(requests.get(index).join())).isEqualTo("message-" + index);
            }
        }
    }

    /**
     * 验证连接池模式允许多连接并发，同时维持每条连接单请求独占。
     */
    @Test
    void shouldUseBoundedConnectionPool() {
        TcpClientOptions options = optionsBuilder(ConnectionMode.POOLED, Duration.ofSeconds(3))
                .maxConnections(4)
                .maxPendingRequests(8)
                .build();

        try (TcpClient<byte[], byte[]> client = clientFactory.create(
                options,
                PayloadCodecs.bytes())) {
            List<CompletableFuture<byte[]>> requests = IntStream.range(0, 12)
                    .mapToObj(index -> client.request(bytes("pool-" + index)).toCompletableFuture())
                    .toList();

            CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).join();

            assertThat(requests)
                    .extracting(future -> text(future.join()))
                    .containsExactlyElementsOf(IntStream.range(0, 12)
                            .mapToObj(index -> "pool-" + index)
                            .toList());
        }
    }

    /**
     * 验证取消等待连接池获取结果的请求后，迟到连接会被排空并归还，池容量不会泄漏。
     *
     * @throws Exception 等待异步连接和请求结果失败
     */
    @Test
    void shouldDrainCancelledPendingPoolAcquireWithoutLeakingCapacity()
            throws Exception {
        respond.set(false);
        TcpClientOptions options = optionsBuilder(
                ConnectionMode.POOLED,
                Duration.ofSeconds(3))
                .maxConnections(1)
                .maxPendingRequests(4)
                .build();

        try (TcpClient<byte[], byte[]> client = clientFactory.create(options)) {
            CompletableFuture<byte[]> first = client.request(bytes("first"))
                    .toCompletableFuture();
            waitUntilAtLeast(connectionCount, 1, Duration.ofSeconds(2));

            CompletableFuture<byte[]> cancelled = client.request(bytes("cancelled"))
                    .toCompletableFuture();
            Thread.sleep(100);
            assertThat(cancelled.cancel(false)).isTrue();

            assertThat(first.cancel(false)).isTrue();
            respond.set(true);

            assertThat(text(client.request(bytes("third"))
                    .toCompletableFuture()
                    .get(2, TimeUnit.SECONDS))).isEqualTo("third");
        }
    }

    /**
     * 验证请求超时会返回结构化异常，且不会把迟到响应交给下一次请求。
     */
    @Test
    void shouldFailRequestWithStructuredTimeout() {
        respond.set(false);

        try (TcpClient<byte[], byte[]> client = clientFactory.create(options(
                ConnectionMode.PERSISTENT,
                Duration.ofMillis(100)))) {
            assertThatThrownBy(() -> client.request(bytes("timeout"))
                    .toCompletableFuture()
                    .join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(NetException.class)
                    .satisfies(throwable -> assertThat(((NetException) throwable.getCause()).getCode())
                            .isEqualTo(NetErrorCode.REQUEST_TIMEOUT.getCode()));
        }
    }

    /**
     * 验证同步门面会拒绝在 Netty EventLoop 中阻塞等待。
     */
    @Test
    void shouldRejectBlockingRequestOnEventLoop() throws Exception {
        try (BlockingTcpClient<byte[], byte[]> client = clientFactory.createBlocking(options(
                ConnectionMode.SHORT,
                Duration.ofSeconds(2)))) {
            NetException exception = runtime.eventLoopGroup()
                    .submit(() -> {
                        try {
                            client.request(bytes("blocked"));
                            return null;
                        } catch (NetException caught) {
                            return caught;
                        }
                    })
                    .get(2, TimeUnit.SECONDS);

            assertThat(exception).isNotNull();
            assertThat(exception.getCode())
                    .isEqualTo(NetErrorCode.BLOCKING_ON_EVENT_LOOP.getCode());
        }
    }

    /**
     * 验证用户定义心跳会在连接空闲时发送并被独立消费，不污染业务响应。
     */
    @Test
    void shouldSupportCustomHeartbeatOnPersistentConnection() throws Exception {
        HeartbeatStrategy heartbeatStrategy = new HeartbeatStrategy() {
            /**
             * 设置较短空闲间隔以便测试。
             *
             * @return 二十毫秒空闲间隔
             */
            @Override
            public Duration idleInterval() {
                return Duration.ofMillis(20);
            }

            /**
             * 创建测试心跳报文。
             *
             * @return 心跳字节
             */
            @Override
            public byte[] heartbeatPayload() {
                return bytes("__PING__");
            }

            /**
             * 识别服务端原样返回的心跳响应。
             *
             * @param response 完整响应载荷
             * @return 匹配心跳文本时返回 {@code true}
             */
            @Override
            public boolean isHeartbeatResponse(byte[] response) {
                return "__PING__".equals(text(response));
            }
        };
        TcpClientOptions options = optionsBuilder(
                ConnectionMode.PERSISTENT,
                Duration.ofSeconds(2))
                .heartbeatStrategy(heartbeatStrategy)
                .pipelineCustomizer(pipeline -> pipeline.addLast(
                        new MarkerPayloadHandler()))
                .build();

        try (TcpClient<byte[], byte[]> client = clientFactory.create(options)) {
            assertThat(text(client.request(bytes("first")).toCompletableFuture().join()))
                    .isEqualTo("first");

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (heartbeatCount.get() == 0 && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }

            assertThat(heartbeatCount.get()).isPositive();
            assertThat(text(client.request(bytes("second")).toCompletableFuture().join()))
                    .isEqualTo("second");
        }
    }

    /**
     * 验证响应回调可以关闭客户端而不会阻塞当前 EventLoop。
     */
    @Test
    void shouldCloseClientFromResponseCallbackWithoutDeadlock() throws Exception {
        TcpClient<byte[], byte[]> client = clientFactory.create(options(
                ConnectionMode.PERSISTENT,
                Duration.ofSeconds(2)));

        client.request(bytes("close"))
                .thenRun(client::close)
                .toCompletableFuture()
                .get(2, TimeUnit.SECONDS);

        assertThat(client.isClosed()).isTrue();
    }

    /**
     * 验证短连接并发数量严格受 maxConnections 限制。
     */
    @Test
    void shouldRejectShortConnectionsBeyondConfiguredCapacity() {
        respond.set(false);
        TcpClientOptions options = optionsBuilder(
                ConnectionMode.SHORT,
                Duration.ofSeconds(2))
                .maxConnections(2)
                .build();

        try (TcpClient<byte[], byte[]> client = clientFactory.create(options)) {
            client.request(bytes("first"));
            client.request(bytes("second"));

            assertThatThrownBy(() -> client.request(bytes("third"))
                    .toCompletableFuture()
                    .join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(NetException.class)
                    .satisfies(throwable -> assertThat(
                            ((NetException) throwable.getCause()).getCode())
                            .isEqualTo(NetErrorCode.REQUEST_OVERLOADED.getCode()));
        }
    }

    /**
     * 验证关闭客户端会立即终止处于建连退避阶段的请求。
     */
    @Test
    void shouldCompletePendingRetryImmediatelyWhenClientCloses() throws Exception {
        int unusedPort = findUnusedPort();
        TcpClientOptions options = TcpClientOptions.builder()
                .host("127.0.0.1")
                .port(unusedPort)
                .connectionMode(ConnectionMode.SHORT)
                .frameCodec(LengthFieldFrameCodec.int32())
                .connectTimeout(Duration.ofMillis(100))
                .requestTimeout(Duration.ofSeconds(5))
                .connectRetryPolicy(new ConnectRetryPolicy(
                        3,
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(2),
                        0))
                .build();
        TcpClient<byte[], byte[]> client = clientFactory.create(options);
        CompletableFuture<byte[]> response = client.request(bytes("close-during-retry"))
                .toCompletableFuture();

        Thread.sleep(150);
        assertThat(response).isNotDone();
        client.close();

        assertThatThrownBy(() -> response.get(500, TimeUnit.MILLISECONDS))
                .hasCauseInstanceOf(NetException.class)
                .satisfies(throwable -> assertThat(
                        ((NetException) throwable.getCause()).getCode())
                        .isEqualTo(NetErrorCode.CLIENT_CLOSED.getCode()));
    }

    /**
     * 验证请求绝对期限覆盖首次建连和重试退避，而不是从连接成功后才开始计时。
     */
    @Test
    void shouldApplyRequestDeadlineWhileConnectingAndBackingOff() throws Exception {
        int unusedPort = findUnusedPort();
        TcpClientOptions options = TcpClientOptions.builder()
                .host("127.0.0.1")
                .port(unusedPort)
                .connectionMode(ConnectionMode.SHORT)
                .frameCodec(LengthFieldFrameCodec.int32())
                .connectTimeout(Duration.ofMillis(100))
                .requestTimeout(Duration.ofMillis(100))
                .connectRetryPolicy(new ConnectRetryPolicy(
                        3,
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(2),
                        0))
                .build();

        try (TcpClient<byte[], byte[]> client = clientFactory.create(options)) {
            assertThatThrownBy(() -> client.request(bytes("deadline"))
                    .toCompletableFuture()
                    .get(1, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(NetException.class)
                    .satisfies(throwable -> assertThat(
                            ((NetException) throwable.getCause()).getCode())
                            .isEqualTo(NetErrorCode.REQUEST_TIMEOUT.getCode()));
        }
    }

    /**
     * 验证同步门面只等待核心请求期限，返回超时后不会在后续重试中补发报文。
     */
    @Test
    void shouldNotSendAfterBlockingRequestHasTimedOut() throws Exception {
        int unusedPort = findUnusedPort();
        TcpClientOptions options = TcpClientOptions.builder()
                .host("127.0.0.1")
                .port(unusedPort)
                .connectionMode(ConnectionMode.SHORT)
                .frameCodec(LengthFieldFrameCodec.int32())
                .connectTimeout(Duration.ofMillis(100))
                .requestTimeout(Duration.ofMillis(100))
                .connectRetryPolicy(new ConnectRetryPolicy(
                        3,
                        Duration.ofMillis(500),
                        Duration.ofMillis(500),
                        0))
                .build();

        try (BlockingTcpClient<byte[], byte[]> client =
                     clientFactory.createBlocking(options)) {
            assertThatThrownBy(() -> client.request(bytes("must-not-be-sent")))
                    .isInstanceOf(NetException.class)
                    .satisfies(throwable -> assertThat(
                            ((NetException) throwable).getCode())
                            .isEqualTo(NetErrorCode.REQUEST_TIMEOUT.getCode()));

            secondaryServerChannel = bindEchoServer(unusedPort);
            Thread.sleep(700);

            assertThat(connectionCount.get()).isZero();
        }
    }

    /**
     * 验证调用方取消公开 Future 会终止底层退避任务，不会继续建立连接。
     */
    @Test
    void shouldCancelNetworkWorkWhenResponseFutureIsCancelled() throws Exception {
        int unusedPort = findUnusedPort();
        TcpClientOptions options = TcpClientOptions.builder()
                .host("127.0.0.1")
                .port(unusedPort)
                .connectionMode(ConnectionMode.SHORT)
                .frameCodec(LengthFieldFrameCodec.int32())
                .connectTimeout(Duration.ofMillis(100))
                .requestTimeout(Duration.ofSeconds(2))
                .connectRetryPolicy(new ConnectRetryPolicy(
                        3,
                        Duration.ofMillis(500),
                        Duration.ofMillis(500),
                        0))
                .build();

        try (TcpClient<byte[], byte[]> client = clientFactory.create(options)) {
            CompletableFuture<byte[]> response = client.request(bytes("cancelled"))
                    .toCompletableFuture();
            Thread.sleep(100);
            assertThat(response.cancel(false)).isTrue();

            secondaryServerChannel = bindEchoServer(unusedPort);
            Thread.sleep(700);

            assertThat(connectionCount.get()).isZero();
        }
    }

    /**
     * 验证请求上下文会复制自定义编解码器返回的可变数组。
     */
    @Test
    void shouldDefensivelyCopyEncodedPayloadBeforeRetry() throws Exception {
        int unusedPort = findUnusedPort();
        TcpClientOptions options = TcpClientOptions.builder()
                .host("127.0.0.1")
                .port(unusedPort)
                .connectionMode(ConnectionMode.SHORT)
                .frameCodec(LengthFieldFrameCodec.int32())
                .connectTimeout(Duration.ofMillis(100))
                .requestTimeout(Duration.ofSeconds(2))
                .connectRetryPolicy(new ConnectRetryPolicy(
                        3,
                        Duration.ofMillis(300),
                        Duration.ofMillis(300),
                        0))
                .build();
        PayloadCodec<byte[], byte[]> mutableCodec = new PayloadCodec<>() {
            /**
             * 故意返回调用方数组，用于验证客户端自身的防御性复制。
             *
             * @param request 请求数组
             * @return 原始可变数组
             */
            @Override
            public byte[] encode(byte[] request) {
                return request;
            }

            /**
             * 直接返回测试响应。
             *
             * @param response 响应数组
             * @return 响应数组
             */
            @Override
            public byte[] decode(byte[] response) {
                return response;
            }
        };
        byte[] request = bytes("original");

        try (TcpClient<byte[], byte[]> client =
                     clientFactory.create(options, mutableCodec)) {
            CompletableFuture<byte[]> response = client.request(request)
                    .toCompletableFuture();
            java.util.Arrays.fill(request, (byte) 'x');
            Thread.sleep(100);
            secondaryServerChannel = bindEchoServer(unusedPort);

            assertThat(text(response.get(2, TimeUnit.SECONDS)))
                    .isEqualTo("original");
        }
    }

    /**
     * 验证连续心跳漏答会淘汰连接，后续业务请求会建立新连接。
     */
    @Test
    void shouldReplaceConnectionAfterHeartbeatResponseTimeout() throws Exception {
        respondHeartbeat.set(false);
        HeartbeatStrategy heartbeatStrategy = new HeartbeatStrategy() {
            /**
             * 设置较短写空闲间隔。
             *
             * @return 二十毫秒
             */
            @Override
            public Duration idleInterval() {
                return Duration.ofMillis(20);
            }

            /**
             * 设置心跳应答期限。
             *
             * @return 三十毫秒
             */
            @Override
            public Duration responseTimeout() {
                return Duration.ofMillis(30);
            }

            /**
             * 连续两次漏答后淘汰连接。
             *
             * @return 两次
             */
            @Override
            public int maxMissedResponses() {
                return 2;
            }

            /**
             * 创建测试心跳。
             *
             * @return 心跳载荷
             */
            @Override
            public byte[] heartbeatPayload() {
                return bytes("__PING__");
            }

            /**
             * 识别测试心跳应答。
             *
             * @param response 完整响应载荷
             * @return 匹配心跳文本时返回 {@code true}
             */
            @Override
            public boolean isHeartbeatResponse(byte[] response) {
                return "__PING__".equals(text(response));
            }
        };
        TcpClientOptions options = optionsBuilder(
                ConnectionMode.PERSISTENT,
                Duration.ofSeconds(2))
                .heartbeatStrategy(heartbeatStrategy)
                .build();

        try (TcpClient<byte[], byte[]> client = clientFactory.create(options)) {
            assertThat(text(client.request(bytes("first")).toCompletableFuture().join()))
                    .isEqualTo("first");

            waitUntilAtLeast(heartbeatCount, 1, Duration.ofSeconds(2));

            respondHeartbeat.set(true);
            assertThat(text(client.request(bytes("second"))
                    .toCompletableFuture()
                    .get(2, TimeUnit.SECONDS))).isEqualTo("second");
            assertThat(connectionCount.get()).isGreaterThanOrEqualTo(2);
        }
    }

    /**
     * 验证迟到心跳应答仍由心跳处理器消费，等待中的业务请求不会收到错误响应。
     *
     * @throws Exception 等待心跳和业务响应失败
     */
    @Test
    void shouldKeepBusinessRequestIsolatedUntilDelayedHeartbeatAck()
            throws Exception {
        heartbeatResponseDelayMillis.set(150);
        HeartbeatStrategy heartbeatStrategy = new HeartbeatStrategy() {
            /**
             * 设置较短写空闲间隔以触发心跳。
             *
             * @return 二十毫秒
             */
            @Override
            public Duration idleInterval() {
                return Duration.ofMillis(20);
            }

            /**
             * 设置单个应答等待窗口。
             *
             * @return 一百毫秒
             */
            @Override
            public Duration responseTimeout() {
                return Duration.ofMillis(100);
            }

            /**
             * 允许迟到应答跨越多个等待窗口。
             *
             * @return 三个窗口
             */
            @Override
            public int maxMissedResponses() {
                return 3;
            }

            /**
             * 创建测试心跳载荷。
             *
             * @return 心跳字节
             */
            @Override
            public byte[] heartbeatPayload() {
                return bytes("__PING__");
            }

            /**
             * 识别测试心跳应答。
             *
             * @param response 完整应答载荷
             * @return 心跳文本匹配时返回 {@code true}
             */
            @Override
            public boolean isHeartbeatResponse(byte[] response) {
                return "__PING__".equals(text(response));
            }
        };
        TcpClientOptions options = optionsBuilder(
                ConnectionMode.PERSISTENT,
                Duration.ofSeconds(2))
                .heartbeatStrategy(heartbeatStrategy)
                .build();

        try (TcpClient<byte[], byte[]> client = clientFactory.create(options)) {
            assertThat(text(client.request(bytes("first"))
                    .toCompletableFuture()
                    .get(2, TimeUnit.SECONDS))).isEqualTo("first");
            waitUntilAtLeast(heartbeatCount, 1, Duration.ofSeconds(2));

            assertThat(text(client.request(bytes("second"))
                    .toCompletableFuture()
                    .get(2, TimeUnit.SECONDS))).isEqualTo("second");
            assertThat(connectionCount.get()).isEqualTo(1);
        }
    }

    /**
     * 验证空心跳载荷会使连接快速失效，不会发送无语义的零字节探测。
     *
     * @throws Exception 等待连接失效和业务响应失败
     */
    @Test
    void shouldRejectEmptyHeartbeatPayload() throws Exception {
        HeartbeatStrategy heartbeatStrategy = new HeartbeatStrategy() {
            /**
             * 设置较短写空闲间隔以触发心跳。
             *
             * @return 二十毫秒
             */
            @Override
            public Duration idleInterval() {
                return Duration.ofMillis(20);
            }

            /**
             * 设置心跳应答期限。
             *
             * @return 一百毫秒
             */
            @Override
            public Duration responseTimeout() {
                return Duration.ofMillis(100);
            }

            /**
             * 创建非法的空心跳载荷。
             *
             * @return 空字节数组
             */
            @Override
            public byte[] heartbeatPayload() {
                return new byte[0];
            }

            /**
             * 将空响应识别为心跳应答，用于区分旧实现行为。
             *
             * @param response 完整应答载荷
             * @return 响应为空时返回 {@code true}
             */
            @Override
            public boolean isHeartbeatResponse(byte[] response) {
                return response.length == 0;
            }
        };
        TcpClientOptions options = optionsBuilder(
                ConnectionMode.PERSISTENT,
                Duration.ofSeconds(2))
                .heartbeatStrategy(heartbeatStrategy)
                .build();

        try (TcpClient<byte[], byte[]> client = clientFactory.create(options)) {
            assertThat(text(client.request(bytes("first"))
                    .toCompletableFuture()
                    .get(2, TimeUnit.SECONDS))).isEqualTo("first");
            Thread.sleep(150);

            assertThat(text(client.request(bytes("second"))
                    .toCompletableFuture()
                    .get(2, TimeUnit.SECONDS))).isEqualTo("second");
            assertThat(connectionCount.get()).isGreaterThanOrEqualTo(2);
        }
    }

    /**
     * 验证线级扩展点位于分帧器之前，载荷级扩展点位于分帧器之后。
     */
    @Test
    void shouldInstallPipelineCustomizersAtDocumentedAnchors() {
        AtomicBoolean wireSawFrameCodec = new AtomicBoolean();
        AtomicBoolean payloadSawFrameCodec = new AtomicBoolean();
        TcpClientOptions options = optionsBuilder(
                ConnectionMode.PERSISTENT,
                Duration.ofSeconds(2))
                .wirePipelineCustomizer(pipeline -> wireSawFrameCodec.set(
                        pipeline.names().contains("letoolLengthFieldDecoder")))
                .pipelineCustomizer(pipeline -> payloadSawFrameCodec.set(
                        pipeline.names().contains("letoolLengthFieldDecoder")))
                .build();

        try (TcpClient<byte[], byte[]> client = clientFactory.create(options)) {
            assertThat(text(client.request(bytes("anchors"))
                    .toCompletableFuture()
                    .join())).isEqualTo("anchors");
        }

        assertThat(wireSawFrameCodec).isFalse();
        assertThat(payloadSawFrameCodec).isTrue();
    }

    /**
     * 创建测试使用的客户端配置。
     *
     * @param mode 连接模式
     * @param requestTimeout 请求超时
     * @return 完整客户端配置
     */
    private TcpClientOptions options(ConnectionMode mode, Duration requestTimeout) {
        return optionsBuilder(mode, requestTimeout).build();
    }

    /**
     * 创建可继续覆盖连接池参数的客户端配置构建器。
     *
     * @param mode 连接模式
     * @param requestTimeout 请求超时
     * @return 客户端配置构建器
     */
    private TcpClientOptions.Builder optionsBuilder(
            ConnectionMode mode,
            Duration requestTimeout) {
        return TcpClientOptions.builder()
                .host("127.0.0.1")
                .port(((java.net.InetSocketAddress) serverChannel.localAddress()).getPort())
                .connectionMode(mode)
                .frameCodec(LengthFieldFrameCodec.int32())
                .connectTimeout(Duration.ofSeconds(1))
                .acquireTimeout(Duration.ofSeconds(1))
                .requestTimeout(requestTimeout);
    }

    /**
     * 等待原子计数器达到期望下限。
     *
     * @param counter 待观察计数器
     * @param expected 期望下限
     * @param timeout 最大等待时间
     * @throws InterruptedException 等待线程被中断
     */
    private static void waitUntilAtLeast(
            AtomicInteger counter,
            int expected,
            Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (counter.get() < expected && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(counter.get()).isGreaterThanOrEqualTo(expected);
    }

    /**
     * 将测试文本编码为字节数组。
     *
     * @param value 测试文本
     * @return UTF-8 字节数组
     */
    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 将响应字节数组转换为测试文本。
     *
     * @param value 响应字节数组
     * @return UTF-8 文本
     */
    private String text(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }

    /**
     * 获取当前未监听的本地端口，仅用于构造确定性的连接失败。
     *
     * @return 暂未使用的本地端口
     * @throws IOException 无法创建临时服务端套接字时抛出
     */
    private int findUnusedPort() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        }
    }

    /**
     * 根据开关决定是否回显报文的服务端处理器。
     */
    private static final class EchoHandler extends SimpleChannelInboundHandler<ByteBuf> {

        private final AtomicBoolean respond;
        private final AtomicBoolean respondHeartbeat;
        private final AtomicInteger heartbeatCount;
        private final AtomicInteger heartbeatResponseDelayMillis;
        private final AtomicInteger connectionCount;

        /**
         * 创建测试回显处理器。
         *
         * @param respond 是否返回响应的共享开关
         * @param respondHeartbeat 是否返回心跳应答
         * @param heartbeatCount 收到的心跳数量
         * @param heartbeatResponseDelayMillis 心跳应答延迟毫秒数
         * @param connectionCount 建立的客户端连接数量
         */
        private EchoHandler(
                AtomicBoolean respond,
                AtomicBoolean respondHeartbeat,
                AtomicInteger heartbeatCount,
                AtomicInteger heartbeatResponseDelayMillis,
                AtomicInteger connectionCount) {
            this.respond = respond;
            this.respondHeartbeat = respondHeartbeat;
            this.heartbeatCount = heartbeatCount;
            this.heartbeatResponseDelayMillis = heartbeatResponseDelayMillis;
            this.connectionCount = connectionCount;
        }

        /**
         * 记录成功建立的客户端连接。
         *
         * @param context 通道上下文
         */
        @Override
        public void channelActive(ChannelHandlerContext context) {
            connectionCount.incrementAndGet();
            context.fireChannelActive();
        }

        /**
         * 在允许响应时保留并原样写回报文。
         *
         * @param context 通道上下文
         * @param message 已完成分帧的业务报文
         */
        @Override
        protected void channelRead0(ChannelHandlerContext context, ByteBuf message) {
            boolean heartbeat = message.toString(StandardCharsets.UTF_8)
                    .endsWith("__PING__");
            if (heartbeat) {
                heartbeatCount.incrementAndGet();
            }
            if (respond.get() && (!heartbeat || respondHeartbeat.get())) {
                int delayMillis = heartbeat
                        ? heartbeatResponseDelayMillis.get()
                        : 0;
                if (delayMillis > 0) {
                    ByteBuf retainedMessage = message.retain();
                    context.executor().schedule(
                            () -> context.writeAndFlush(retainedMessage),
                            delayMillis,
                            TimeUnit.MILLISECONDS);
                } else {
                    context.writeAndFlush(message.retain());
                }
            }
        }
    }

    /**
     * 为完整载荷增加并移除标记的双向处理器。
     *
     * <p>测试处理器用于证明业务报文和心跳都从通道尾部写出，并经过载荷级扩展器。</p>
     */
    private static final class MarkerPayloadHandler extends ChannelDuplexHandler {

        private static final byte MARKER = (byte) '!';

        /**
         * 在出站完整载荷前增加一个测试标记。
         *
         * @param context 通道上下文
         * @param message 出站消息
         * @param promise 写出结果
         */
        @Override
        public void write(
                ChannelHandlerContext context,
                Object message,
                ChannelPromise promise) {
            if (!(message instanceof ByteBuf buffer)) {
                context.write(message, promise);
                return;
            }
            ByteBuf marked = context.alloc().buffer(buffer.readableBytes() + 1);
            marked.writeByte(MARKER);
            marked.writeBytes(
                    buffer,
                    buffer.readerIndex(),
                    buffer.readableBytes());
            ReferenceCountUtil.release(message);
            context.write(marked, promise);
        }

        /**
         * 从入站完整载荷移除服务端原样回显的测试标记。
         *
         * @param context 通道上下文
         * @param message 入站消息
         */
        @Override
        public void channelRead(ChannelHandlerContext context, Object message) {
            if (!(message instanceof ByteBuf buffer)
                    || !buffer.isReadable()
                    || buffer.getByte(buffer.readerIndex()) != MARKER) {
                context.fireChannelRead(message);
                return;
            }
            ByteBuf unmarked = buffer.retainedSlice(
                    buffer.readerIndex() + 1,
                    buffer.readableBytes() - 1);
            ReferenceCountUtil.release(message);
            context.fireChannelRead(unmarked);
        }
    }
}
