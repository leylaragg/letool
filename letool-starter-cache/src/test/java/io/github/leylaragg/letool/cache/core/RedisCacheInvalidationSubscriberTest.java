package io.github.leylaragg.letool.cache.core;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Redis 失效订阅的异步恢复与生命周期测试。 */
class RedisCacheInvalidationSubscriberTest {

    @Test
    void shouldRetryAfterInitialConnectionFailure() throws Exception {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisMessageListenerContainer failedContainer = mock(RedisMessageListenerContainer.class);
        RedisMessageListenerContainer recoveredContainer = mock(RedisMessageListenerContainer.class);
        doThrow(new IllegalStateException("redis unavailable")).when(failedContainer).start();
        AtomicInteger containerCreations = new AtomicInteger();
        RedisCacheInvalidationSubscriber subscriber = new RedisCacheInvalidationSubscriber(
                connectionFactory,
                mock(RedisCacheInvalidationListener.class),
                "letool:test:invalidation",
                Duration.ofMillis(10),
                () -> containerCreations.incrementAndGet() == 1
                        ? failedContainer : recoveredContainer);

        try {
            subscriber.start();

            assertEventually(subscriber::isSubscribed, Duration.ofSeconds(1));
            assertThat(containerCreations).hasValue(2);
            verify(failedContainer).destroy();
            verify(recoveredContainer).start();
        } finally {
            subscriber.close();
        }
    }

    @Test
    void shouldStopRetryingAfterClose() throws Exception {
        RedisMessageListenerContainer failedContainer = mock(RedisMessageListenerContainer.class);
        doThrow(new IllegalStateException("redis unavailable")).when(failedContainer).start();
        AtomicInteger containerCreations = new AtomicInteger();
        RedisCacheInvalidationSubscriber subscriber = new RedisCacheInvalidationSubscriber(
                mock(RedisConnectionFactory.class),
                mock(RedisCacheInvalidationListener.class),
                "letool:test:invalidation",
                Duration.ofMillis(20),
                () -> {
                    containerCreations.incrementAndGet();
                    return failedContainer;
                });

        subscriber.start();
        assertEventually(() -> containerCreations.get() > 0, Duration.ofSeconds(1));
        subscriber.close();
        int attemptsAfterClose = containerCreations.get();

        Thread.sleep(80L);
        assertThat(containerCreations).hasValue(attemptsAfterClose);
        assertThat(subscriber.isRunning()).isFalse();
        assertThat(subscriber.isSubscribed()).isFalse();
    }

    /**
     * 等待异步状态进入预期值，不使用固定长等待掩盖线程调度问题。
     *
     * @param condition 完成条件
     * @param timeout 最大等待时间
     */
    private static void assertEventually(BooleanSupplier condition, Duration timeout)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }
}
