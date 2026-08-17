package io.github.leylaragg.letool.net.tcp;

import io.github.leylaragg.letool.net.protocol.LengthFieldFrameCodec;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * {@link TcpClientOptions} 配置契约测试。
 */
class TcpClientOptionsTest {

    /**
     * 验证构建器能够生成具有安全默认值的不可变配置。
     */
    @Test
    void shouldBuildOptionsWithSafeDefaults() {
        TcpClientOptions options = TcpClientOptions.builder()
                .host("127.0.0.1")
                .port(8080)
                .frameCodec(LengthFieldFrameCodec.int32())
                .build();

        assertThat(options.host()).isEqualTo("127.0.0.1");
        assertThat(options.port()).isEqualTo(8080);
        assertThat(options.connectionMode()).isEqualTo(ConnectionMode.PERSISTENT);
        assertThat(options.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(options.requestTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(options.maxConnections()).isEqualTo(1);
        assertThat(options.maxPendingRequests()).isEqualTo(1024);
        assertThat(options.maxFrameLength()).isEqualTo(8 * 1024 * 1024);
        assertThat(options.connectRetryPolicy().maxAttempts()).isEqualTo(3);
    }

    /**
     * 验证空主机、非法端口和非正超时会在构建阶段被拒绝。
     */
    @Test
    void shouldRejectInvalidEndpointAndTimeout() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validBuilder().host(" ").build())
                .withMessageContaining("host");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validBuilder().port(0).build())
                .withMessageContaining("port");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validBuilder().requestTimeout(Duration.ZERO).build())
                .withMessageContaining("requestTimeout");
    }

    /**
     * 验证持久连接只能使用一条底层连接，短连接和连接池模式可显式设置容量。
     */
    @Test
    void shouldValidateConnectionModeCapacity() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validBuilder()
                        .connectionMode(ConnectionMode.PERSISTENT)
                        .maxConnections(2)
                        .build())
                .withMessageContaining("maxConnections");

        TcpClientOptions pooled = validBuilder()
                .connectionMode(ConnectionMode.POOLED)
                .maxConnections(4)
                .build();
        TcpClientOptions shortConnection = validBuilder()
                .connectionMode(ConnectionMode.SHORT)
                .maxConnections(8)
                .build();

        assertThat(pooled.maxConnections()).isEqualTo(4);
        assertThat(shortConnection.maxConnections()).isEqualTo(8);
    }

    /**
     * 验证心跳间隔和连接重试策略会在构建阶段完成校验。
     */
    @Test
    void shouldValidateHeartbeatAndRetryPolicy() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validBuilder()
                        .heartbeatStrategy(new HeartbeatStrategy() {
                            /**
                             * 返回非法的零间隔。
                             *
                             * @return 零间隔
                             */
                            @Override
                            public Duration idleInterval() {
                                return Duration.ZERO;
                            }

                            /**
                             * 创建心跳载荷。
                             *
                             * @return 心跳字节
                             */
                            @Override
                            public byte[] heartbeatPayload() {
                                return new byte[]{1};
                            }

                            /**
                             * 判断心跳响应。
                             *
                             * @param response 完整响应载荷
                             * @return 始终返回 {@code false}
                             */
                            @Override
                            public boolean isHeartbeatResponse(byte[] response) {
                                return false;
                            }
                        })
                        .build())
                .withMessageContaining("idleInterval");

        AtomicBoolean customized = new AtomicBoolean();
        TcpClientOptions options = validBuilder()
                .connectRetryPolicy(ConnectRetryPolicy.noRetry())
                .pipelineCustomizer(pipeline -> customized.set(true))
                .build();

        options.pipelineCustomizer().customize(new io.netty.channel.embedded.EmbeddedChannel()
                .pipeline());
        assertThat(options.connectRetryPolicy().maxAttempts()).isEqualTo(1);
        assertThat(customized).isTrue();
    }

    /**
     * 验证心跳应答期限和连续漏答次数必须为生产可用的正数。
     */
    @Test
    void shouldValidateHeartbeatResponsePolicy() {
        HeartbeatStrategy invalidStrategy = new HeartbeatStrategy() {
            /**
             * 返回有效空闲间隔。
             *
             * @return 一秒
             */
            @Override
            public Duration idleInterval() {
                return Duration.ofSeconds(1);
            }

            /**
             * 返回非法零应答期限。
             *
             * @return 零时长
             */
            @Override
            public Duration responseTimeout() {
                return Duration.ZERO;
            }

            /**
             * 返回非法漏答次数。
             *
             * @return 零次
             */
            @Override
            public int maxMissedResponses() {
                return 0;
            }

            /**
             * 创建心跳载荷。
             *
             * @return 心跳字节
             */
            @Override
            public byte[] heartbeatPayload() {
                return new byte[]{1};
            }

            /**
             * 判断心跳应答。
             *
             * @param response 完整响应载荷
             * @return 始终返回 {@code false}
             */
            @Override
            public boolean isHeartbeatResponse(byte[] response) {
                return false;
            }
        };

        assertThatIllegalArgumentException()
                .isThrownBy(() -> validBuilder()
                        .heartbeatStrategy(invalidStrategy)
                        .build())
                .withMessageContaining("responseTimeout");
    }

    /**
     * 验证复用连接模式的总请求容量不能发生整数溢出。
     */
    @Test
    void shouldRejectOverflowedRequestCapacity() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> validBuilder()
                        .connectionMode(ConnectionMode.POOLED)
                        .maxConnections(Integer.MAX_VALUE)
                        .maxPendingRequests(1)
                        .build())
                .withMessageContaining("溢出");
    }

    /**
     * 验证长度字段无法表达配置上限时会在创建客户端前失败。
     */
    @Test
    void shouldRejectFrameLengthBeyondProtocolCapacity() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TcpClientOptions.builder()
                        .host("localhost")
                        .port(9000)
                        .frameCodec(LengthFieldFrameCodec.int16())
                        .maxFrameLength(65_536)
                        .build())
                .withMessageContaining("可表达范围");
    }

    /**
     * 验证公开规范构造器不能绕过构建器的配置校验。
     */
    @Test
    void shouldValidatePublicCanonicalConstructor() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TcpClientOptions(
                        " ",
                        0,
                        ConnectionMode.PERSISTENT,
                        LengthFieldFrameCodec.int32(),
                        Duration.ZERO,
                        Duration.ZERO,
                        Duration.ZERO,
                        2,
                        0,
                        0,
                        true,
                        true,
                        ConnectRetryPolicy.noRetry(),
                        null,
                        ChannelPipelineCustomizer.NONE,
                        ChannelPipelineCustomizer.NONE))
                .withMessageContaining("host");
    }

    /**
     * 创建包含必填参数的测试构建器。
     *
     * @return 可继续覆盖字段的配置构建器
     */
    private TcpClientOptions.Builder validBuilder() {
        return TcpClientOptions.builder()
                .host("localhost")
                .port(9000)
                .frameCodec(LengthFieldFrameCodec.int32());
    }
}
