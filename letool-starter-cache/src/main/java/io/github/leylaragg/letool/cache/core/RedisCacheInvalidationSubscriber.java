package io.github.leylaragg.letool.cache.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Letool 私有的 Redis 缓存失效订阅生命周期。
 *
 * <p>内部监听容器不会作为 Spring Bean 暴露，因此业务项目已有零个、一个或多个
 * {@link RedisMessageListenerContainer} 都不会改变 Letool 的订阅所有权，也不会增加业务按类型注入候选。</p>
 *
 * <p>订阅建立在线程池中执行，Redis 暂时不可用不会阻断 Spring Context 启动。</p>
 */
public final class RedisCacheInvalidationSubscriber implements SmartLifecycle, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheInvalidationSubscriber.class);

    private final RedisConnectionFactory connectionFactory;
    private final RedisCacheInvalidationListener listener;
    private final String channel;
    private final Duration retryInterval;
    private final Supplier<RedisMessageListenerContainer> containerFactory;
    private final ScheduledExecutorService executor;
    private final Object lifecycleMonitor = new Object();
    private final AtomicBoolean active = new AtomicBoolean();
    private final AtomicBoolean failureLogged = new AtomicBoolean();
    private volatile RedisMessageListenerContainer container;
    private volatile boolean subscribed;

    /**
     * 创建失效订阅生命周期。
     *
     * @param connectionFactory 业务项目提供的唯一 Redis 连接工厂
     * @param listener Letool 失效消息监听器
     * @param channel Redis Pub/Sub 通道
     */
    public RedisCacheInvalidationSubscriber(
            RedisConnectionFactory connectionFactory,
            RedisCacheInvalidationListener listener,
            String channel) {
        this(connectionFactory, listener, channel, Duration.ofSeconds(30));
    }

    /**
     * 使用指定恢复间隔创建失效订阅生命周期。
     *
     * @param connectionFactory 业务项目提供的唯一 Redis 连接工厂
     * @param listener Letool 失效消息监听器
     * @param channel Redis Pub/Sub 通道
     * @param retryInterval 订阅失败后的恢复间隔
     */
    public RedisCacheInvalidationSubscriber(
            RedisConnectionFactory connectionFactory,
            RedisCacheInvalidationListener listener,
            String channel,
            Duration retryInterval) {
        this(connectionFactory, listener, channel, retryInterval,
                RedisMessageListenerContainer::new);
    }

    /**
     * 使用可替换容器工厂创建订阅器，供生命周期回归测试稳定模拟首次失败和后续恢复。
     */
    RedisCacheInvalidationSubscriber(
            RedisConnectionFactory connectionFactory,
            RedisCacheInvalidationListener listener,
            String channel,
            Duration retryInterval,
            Supplier<RedisMessageListenerContainer> containerFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "Redis 连接工厂不能为空");
        this.listener = Objects.requireNonNull(listener, "Redis 缓存失效监听器不能为空");
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("Redis Pub/Sub 通道不能为空");
        }
        this.channel = channel;
        if (retryInterval == null || retryInterval.isZero() || retryInterval.isNegative()) {
            throw new IllegalArgumentException("Redis 订阅恢复间隔必须大于 0");
        }
        this.retryInterval = retryInterval;
        this.containerFactory = Objects.requireNonNull(containerFactory, "Redis 监听容器工厂不能为空");
        this.executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "letool-cache-invalidation-subscriber");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** 异步建立订阅，避免 Redis 连接等待进入 Spring 启动主线程。 */
    @Override
    public void start() {
        if (active.compareAndSet(false, true)) {
            scheduleSubscription(Duration.ZERO);
        }
    }

    private void scheduleSubscription(Duration delay) {
        if (!active.get() || executor.isShutdown()) {
            return;
        }
        executor.schedule(this::subscribeOnce, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** 单次尝试建立订阅；失败容器必须销毁，下一次重试使用全新容器。 */
    private void subscribeOnce() {
        if (!active.get()) {
            return;
        }
        RedisMessageListenerContainer candidate = createContainer();
        try {
            candidate.start();
            synchronized (lifecycleMonitor) {
                if (active.get()) {
                    container = candidate;
                    subscribed = true;
                    failureLogged.set(false);
                    candidate = null;
                }
            }
            if (candidate != null) {
                closeContainer(candidate);
            }
        } catch (RuntimeException exception) {
            closeContainer(candidate);
            if (failureLogged.compareAndSet(false, true)) {
                log.warn("Redis cache invalidation subscription unavailable, causeType={}",
                        exception.getClass().getSimpleName());
            }
            log.debug("Redis cache invalidation subscription failure detail", exception);
            scheduleSubscription(retryInterval);
        }
    }

    /** 创建并完成通道绑定，但不在调用线程发起 Redis 连接。 */
    private RedisMessageListenerContainer createContainer() {
        RedisMessageListenerContainer candidate = Objects.requireNonNull(
                containerFactory.get(), "Redis 监听容器不能为空");
        candidate.setConnectionFactory(connectionFactory);
        long retryMillis = Math.max(1L, retryInterval.toMillis());
        // 单次订阅确认不能长于框架恢复周期，否则失败状态会长期占住唯一后台线程。
        candidate.setMaxSubscriptionRegistrationWaitingTime(retryMillis);
        candidate.setRecoveryInterval(retryMillis);
        candidate.addMessageListener((message, pattern) ->
                        listener.onMessage(new String(message.getBody(), StandardCharsets.UTF_8)),
                new ChannelTopic(channel));
        return candidate;
    }

    /** 停止订阅并释放当前内部容器。 */
    @Override
    public void stop() {
        active.set(false);
        RedisMessageListenerContainer current;
        synchronized (lifecycleMonitor) {
            current = container;
            container = null;
            subscribed = false;
        }
        closeContainer(current);
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    /** @return 生命周期是否已启动；不代表 Redis 当前已经订阅成功 */
    @Override
    public boolean isRunning() {
        return active.get();
    }

    /** @return 当前是否已经成功建立 Redis Pub/Sub 订阅 */
    public boolean isSubscribed() {
        return subscribed;
    }

    /** 关闭生命周期并终止后台线程。 */
    @Override
    public void close() {
        stop();
        executor.shutdownNow();
    }

    /** 以幂等方式停止并销毁一次订阅尝试持有的容器。 */
    private void closeContainer(RedisMessageListenerContainer target) {
        if (target == null) {
            return;
        }
        try {
            target.stop();
        } catch (RuntimeException exception) {
            log.debug("Redis cache invalidation container stop failure", exception);
        }
        try {
            target.destroy();
        } catch (Exception exception) {
            log.debug("Redis cache invalidation container destroy failure", exception);
        }
    }
}
