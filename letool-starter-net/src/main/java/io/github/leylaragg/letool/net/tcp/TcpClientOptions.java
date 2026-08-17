package io.github.leylaragg.letool.net.tcp;

import io.github.leylaragg.letool.net.protocol.FrameCodec;

import java.time.Duration;

/**
 * 不可变的 TCP 客户端配置。
 *
 * <p>配置在构建阶段完成全部边界校验，使网络线程不会因无效参数延迟失败。</p>
 *
 * @param host 远程主机名或 IP
 * @param port 远程端口
 * @param connectionMode 连接复用模式
 * @param frameCodec 报文边界处理器
 * @param connectTimeout 单次建立连接超时
 * @param acquireTimeout 从连接池获取连接的超时
 * @param requestTimeout 完整请求响应超时
 * @param maxConnections 最大连接数
 * @param maxPendingRequests 最大等待请求数
 * @param maxFrameLength 最大业务报文长度
 * @param tcpNoDelay 是否禁用 Nagle 算法
 * @param keepAlive 是否启用 TCP KeepAlive
 * @param connectRetryPolicy 请求写出前的建连重试策略
 * @param heartbeatStrategy 可选应用层心跳策略
 * @param wirePipelineCustomizer 位于报文分帧器之前的线级流水线扩展器
 * @param pipelineCustomizer 位于报文分帧器之后的载荷级流水线扩展器
 */
public record TcpClientOptions(
        String host,
        int port,
        ConnectionMode connectionMode,
        FrameCodec frameCodec,
        Duration connectTimeout,
        Duration acquireTimeout,
        Duration requestTimeout,
        int maxConnections,
        int maxPendingRequests,
        int maxFrameLength,
        boolean tcpNoDelay,
        boolean keepAlive,
        ConnectRetryPolicy connectRetryPolicy,
        HeartbeatStrategy heartbeatStrategy,
        ChannelPipelineCustomizer wirePipelineCustomizer,
        ChannelPipelineCustomizer pipelineCustomizer) {

    /**
     * 校验公开规范构造器的全部参数。
     *
     * <p>构建器和直接构造共享同一个校验入口，避免调用方绕过生产安全边界。</p>
     *
     * @param host 远程主机名或 IP
     * @param port 远程端口
     * @param connectionMode 连接复用模式
     * @param frameCodec 报文边界处理器
     * @param connectTimeout 单次建立连接超时
     * @param acquireTimeout 从连接池获取连接的超时
     * @param requestTimeout 完整请求响应超时
     * @param maxConnections 最大连接数
     * @param maxPendingRequests 最大等待请求数
     * @param maxFrameLength 最大业务报文长度
     * @param tcpNoDelay 是否禁用 Nagle 算法
     * @param keepAlive 是否启用 TCP KeepAlive
     * @param connectRetryPolicy 建连重试策略
     * @param heartbeatStrategy 可选心跳策略
     * @param wirePipelineCustomizer 线级流水线扩展器
     * @param pipelineCustomizer 载荷级流水线扩展器
     */
    public TcpClientOptions {
        requireText(host, "host");
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("port 必须在 1 到 65535 之间");
        }
        requireNonNull(connectionMode, "connectionMode");
        requireNonNull(frameCodec, "frameCodec");
        requirePositiveMillis(connectTimeout, "connectTimeout");
        requirePositiveMillis(acquireTimeout, "acquireTimeout");
        requirePositiveMillis(requestTimeout, "requestTimeout");
        requireNanosConvertible(requestTimeout, "requestTimeout");
        if (connectTimeout.toMillis() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("connectTimeout 不能超过整数毫秒范围");
        }
        requirePositive(maxConnections, "maxConnections");
        requirePositive(maxPendingRequests, "maxPendingRequests");
        requirePositive(maxFrameLength, "maxFrameLength");
        frameCodec.validateMaxFrameLength(maxFrameLength);
        requireNonNull(connectRetryPolicy, "connectRetryPolicy");
        requireNonNull(wirePipelineCustomizer, "wirePipelineCustomizer");
        requireNonNull(pipelineCustomizer, "pipelineCustomizer");
        if (connectionMode == ConnectionMode.PERSISTENT && maxConnections != 1) {
            throw new IllegalArgumentException(
                    "PERSISTENT 模式的 maxConnections 必须为 1");
        }
        if (connectionMode != ConnectionMode.SHORT) {
            try {
                Math.addExact(maxConnections, maxPendingRequests);
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException(
                        "maxConnections 与 maxPendingRequests 之和溢出",
                        exception);
            }
        }
        if (heartbeatStrategy != null) {
            requirePositiveMillis(
                    heartbeatStrategy.idleInterval(),
                    "heartbeatStrategy.idleInterval");
            requirePositiveMillis(
                    heartbeatStrategy.responseTimeout(),
                    "heartbeatStrategy.responseTimeout");
            if (heartbeatStrategy.maxMissedResponses() <= 0) {
                throw new IllegalArgumentException(
                        "heartbeatStrategy.maxMissedResponses 必须大于 0");
            }
        }
        host = host.trim();
    }

    /**
     * 创建 TCP 配置构建器。
     *
     * @return 使用生产安全默认值的构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * TCP 客户端配置构建器。
     */
    public static final class Builder {

        private String host;
        private int port;
        private ConnectionMode connectionMode = ConnectionMode.PERSISTENT;
        private FrameCodec frameCodec;
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration acquireTimeout = Duration.ofSeconds(5);
        private Duration requestTimeout = Duration.ofSeconds(30);
        private int maxConnections = 1;
        private int maxPendingRequests = 1024;
        private int maxFrameLength = 8 * 1024 * 1024;
        private boolean tcpNoDelay = true;
        private boolean keepAlive = true;
        private ConnectRetryPolicy connectRetryPolicy =
                ConnectRetryPolicy.productionDefault();
        private HeartbeatStrategy heartbeatStrategy;
        private ChannelPipelineCustomizer wirePipelineCustomizer =
                ChannelPipelineCustomizer.NONE;
        private ChannelPipelineCustomizer pipelineCustomizer =
                ChannelPipelineCustomizer.NONE;

        private Builder() {
        }

        /**
         * 设置远程主机。
         *
         * @param host 主机名或 IP
         * @return 当前构建器
         */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        /**
         * 设置远程端口。
         *
         * @param port 端口号
         * @return 当前构建器
         */
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /**
         * 设置连接复用模式。
         *
         * @param connectionMode 连接模式
         * @return 当前构建器
         */
        public Builder connectionMode(ConnectionMode connectionMode) {
            this.connectionMode = connectionMode;
            return this;
        }

        /**
         * 设置报文边界处理器。
         *
         * @param frameCodec 分帧器
         * @return 当前构建器
         */
        public Builder frameCodec(FrameCodec frameCodec) {
            this.frameCodec = frameCodec;
            return this;
        }

        /**
         * 设置建立连接超时。
         *
         * @param connectTimeout 正数时长
         * @return 当前构建器
         */
        public Builder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        /**
         * 设置连接获取超时。
         *
         * @param acquireTimeout 正数时长
         * @return 当前构建器
         */
        public Builder acquireTimeout(Duration acquireTimeout) {
            this.acquireTimeout = acquireTimeout;
            return this;
        }

        /**
         * 设置完整请求响应超时。
         *
         * @param requestTimeout 正数时长
         * @return 当前构建器
         */
        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        /**
         * 设置最大连接数。
         *
         * @param maxConnections 正整数
         * @return 当前构建器
         */
        public Builder maxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
            return this;
        }

        /**
         * 设置最大等待请求数。
         *
         * @param maxPendingRequests 正整数
         * @return 当前构建器
         */
        public Builder maxPendingRequests(int maxPendingRequests) {
            this.maxPendingRequests = maxPendingRequests;
            return this;
        }

        /**
         * 设置最大业务报文长度。
         *
         * @param maxFrameLength 正整数，单位为字节
         * @return 当前构建器
         */
        public Builder maxFrameLength(int maxFrameLength) {
            this.maxFrameLength = maxFrameLength;
            return this;
        }

        /**
         * 设置是否禁用 Nagle 算法。
         *
         * @param tcpNoDelay {@code true} 表示小报文立即发送
         * @return 当前构建器
         */
        public Builder tcpNoDelay(boolean tcpNoDelay) {
            this.tcpNoDelay = tcpNoDelay;
            return this;
        }

        /**
         * 设置是否启用操作系统 TCP KeepAlive。
         *
         * @param keepAlive 是否启用
         * @return 当前构建器
         */
        public Builder keepAlive(boolean keepAlive) {
            this.keepAlive = keepAlive;
            return this;
        }

        /**
         * 设置请求写出前的建连重试策略。
         *
         * @param connectRetryPolicy 建连重试策略
         * @return 当前构建器
         */
        public Builder connectRetryPolicy(ConnectRetryPolicy connectRetryPolicy) {
            this.connectRetryPolicy = connectRetryPolicy;
            return this;
        }

        /**
         * 设置应用层心跳策略。
         *
         * @param heartbeatStrategy 心跳策略；不需要心跳时可为 {@code null}
         * @return 当前构建器
         */
        public Builder heartbeatStrategy(HeartbeatStrategy heartbeatStrategy) {
            this.heartbeatStrategy = heartbeatStrategy;
            return this;
        }

        /**
         * 设置位于分帧器之前的线级流水线扩展器。
         *
         * <p>该扩展点适合安装 TLS 等直接处理线上字节流的处理器。</p>
         *
         * @param wirePipelineCustomizer 线级流水线扩展器
         * @return 当前构建器
         */
        public Builder wirePipelineCustomizer(
                ChannelPipelineCustomizer wirePipelineCustomizer) {
            this.wirePipelineCustomizer = wirePipelineCustomizer;
            return this;
        }

        /**
         * 设置位于分帧器之后的载荷级流水线扩展器。
         *
         * <p>该扩展点适合安装针对完整载荷的压缩、加密或协议适配处理器。</p>
         *
         * @param pipelineCustomizer 载荷级流水线扩展器
         * @return 当前构建器
         */
        public Builder pipelineCustomizer(
                ChannelPipelineCustomizer pipelineCustomizer) {
            this.pipelineCustomizer = pipelineCustomizer;
            return this;
        }

        /**
         * 校验全部参数并构建不可变配置。
         *
         * @return 不可变 TCP 客户端配置
         * @throws IllegalArgumentException 任一参数不合法时抛出
         */
        public TcpClientOptions build() {
            return new TcpClientOptions(
                    host,
                    port,
                    connectionMode,
                    frameCodec,
                    connectTimeout,
                    acquireTimeout,
                    requestTimeout,
                    maxConnections,
                    maxPendingRequests,
                    maxFrameLength,
                    tcpNoDelay,
                    keepAlive,
                    connectRetryPolicy,
                    heartbeatStrategy,
                    wirePipelineCustomizer,
                    pipelineCustomizer);
        }
    }

    /**
     * 校验字符串非空白。
     *
     * @param value 待校验值
     * @param fieldName 字段名称
     */
    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
    }

    /**
     * 校验对象不为空。
     *
     * @param value 待校验值
     * @param fieldName 字段名称
     */
    private static void requireNonNull(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " 不能为空");
        }
    }

    /**
     * 校验时长为正数且能够安全转换为毫秒。
     *
     * @param value 待校验时长
     * @param fieldName 字段名称
     */
    private static void requirePositiveMillis(Duration value, String fieldName) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(fieldName + " 必须大于 0");
        }
        try {
            if (value.toMillis() <= 0) {
                throw new IllegalArgumentException(fieldName + " 必须至少为 1 毫秒");
            }
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(fieldName + " 超出毫秒范围", exception);
        }
    }

    /**
     * 校验时长能够转换为纳秒，保证请求期限计算不会溢出。
     *
     * @param value 待校验时长
     * @param fieldName 字段名称
     */
    private static void requireNanosConvertible(Duration value, String fieldName) {
        try {
            value.toNanos();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(fieldName + " 超出纳秒范围", exception);
        }
    }

    /**
     * 校验整数为正数。
     *
     * @param value 待校验值
     * @param fieldName 字段名称
     */
    private static void requirePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException(fieldName + " 必须大于 0");
        }
    }
}
