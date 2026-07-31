package com.github.leyland.letool.net.tcp;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RequestFuture} 外部完成与内部完成语义测试。
 */
class RequestFutureTest {

    /**
     * 验证调用方通过 completeAsync 结束结果时会触发底层请求清理。
     */
    @Test
    void shouldNotifyExternalTerminationWhenCompletedAsynchronously() {
        RequestFuture<String> future = new RequestFuture<>();
        AtomicInteger terminationCount = new AtomicInteger();
        future.onExternalTermination(terminationCount::incrementAndGet);

        future.completeAsync(() -> "manual", Runnable::run);

        assertThat(future.join()).isEqualTo("manual");
        assertThat(terminationCount).hasValue(1);
    }

    /**
     * 验证使用默认执行器的 completeAsync 重载同样会触发底层请求清理。
     */
    @Test
    void shouldNotifyExternalTerminationWithDefaultAsyncExecutor() {
        RequestFuture<String> future = new RequestFuture<>();
        AtomicInteger terminationCount = new AtomicInteger();
        future.onExternalTermination(terminationCount::incrementAndGet);

        future.completeAsync(() -> "default").join();

        assertThat(future.join()).isEqualTo("default");
        assertThat(terminationCount).hasValue(1);
    }

    /**
     * 验证客户端内部完成结果时不会反向触发请求清理。
     */
    @Test
    void shouldNotNotifyExternalTerminationForClientCompletion() {
        RequestFuture<String> future = new RequestFuture<>();
        AtomicInteger terminationCount = new AtomicInteger();
        future.onExternalTermination(terminationCount::incrementAndGet);

        assertThat(future.completeFromClient("response")).isTrue();

        assertThat(future.join()).isEqualTo("response");
        assertThat(terminationCount).hasValue(0);
    }
}
