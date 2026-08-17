package io.github.leylaragg.letool.net.tcp;

import io.netty.channel.EventLoopGroup;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * {@link NetRuntime} 生命周期测试。
 */
class NetRuntimeTest {

    /**
     * 验证事件线程内部关闭运行时不会等待自身终止而死锁。
     */
    @Test
    void shouldCloseFromEventLoopWithoutDeadlock() throws Exception {
        NetRuntime runtime = new NetRuntime(
                1,
                Duration.ZERO,
                Duration.ofSeconds(1));

        EventLoopGroup eventLoopGroup = runtime.eventLoopGroup();
        eventLoopGroup
                .submit(runtime::close)
                .get(2, TimeUnit.SECONDS);

        assertThat(runtime.isClosed()).isTrue();
        eventLoopGroup.terminationFuture()
                .awaitUninterruptibly(2, TimeUnit.SECONDS);
    }

    /**
     * 验证关闭时长超出毫秒范围时在构造阶段立即失败。
     */
    @Test
    void shouldRejectShutdownDurationBeyondMillisRange() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new NetRuntime(
                        1,
                        Duration.ZERO,
                        Duration.ofSeconds(Long.MAX_VALUE)))
                .withMessageContaining("shutdownTimeout");
    }
}
