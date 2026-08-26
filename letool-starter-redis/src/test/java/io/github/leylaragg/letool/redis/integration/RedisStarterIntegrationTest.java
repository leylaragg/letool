package io.github.leylaragg.letool.redis.integration;

import io.github.leylaragg.letool.lock.core.LockHandle;
import io.github.leylaragg.letool.lock.core.LockRequest;
import io.github.leylaragg.letool.lock.core.LockTemplate;
import io.github.leylaragg.letool.redis.RedisFacade;
import io.github.leylaragg.letool.redis.cache.RedisCachePolicy;
import io.github.leylaragg.letool.redis.cache.RedisCacheTemplate;
import io.github.leylaragg.letool.redis.config.LetoolRedisProperties;
import io.github.leylaragg.letool.redis.lock.RedissonDistributedLock;
import io.github.leylaragg.letool.redis.serializer.FastJson2JsonRedisSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Redis Starter 在真实 Redis 上的并发与租约语义门禁。
 *
 * <p>仅由 {@code redis-integration} Profile 启用。若没有提供测试 Redis 环境变量，
 * 测试会启动临时 Redis 7.2 容器，并在结束时清理本次随机命名空间。</p>
 */
@DisplayName("Redis Starter 集成测试")
@Tag("redis-integration")
class RedisStarterIntegrationTest {

    private static final Duration WATCHDOG_TIMEOUT = Duration.ofMillis(600);

    private static GenericContainer<?> redisContainer;
    private static LettuceConnectionFactory connectionFactory;
    private static RedisTemplate<String, Object> redisTemplate;
    private static RedissonClient firstClient;
    private static RedissonClient secondClient;
    private static RedissonDistributedLock firstLock;
    private static RedissonDistributedLock secondLock;
    private static RedisFacade redisFacade;
    private static String keyPrefix;

    /** 建立 Lettuce 与两个独立 Redisson 客户端，模拟跨进程锁竞争。 */
    @BeforeAll
    static void connect() {
        RedisEndpoint endpoint = resolveEndpoint();
        keyPrefix = "letool:redis-it:"
                + UUID.randomUUID().toString().replace("-", "") + ":";

        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration(
                endpoint.host(), endpoint.port());
        if (endpoint.password() != null) {
            standalone.setPassword(RedisPassword.of(endpoint.password()));
        }
        connectionFactory = new LettuceConnectionFactory(standalone);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();

        redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.setKeySerializer(StringRedisSerializer.UTF_8);
        redisTemplate.setHashKeySerializer(StringRedisSerializer.UTF_8);
        FastJson2JsonRedisSerializer<Object> valueSerializer =
                new FastJson2JsonRedisSerializer<>(Object.class);
        redisTemplate.setValueSerializer(valueSerializer);
        redisTemplate.setHashValueSerializer(valueSerializer);
        redisTemplate.afterPropertiesSet();

        firstClient = Redisson.create(redissonConfig(endpoint));
        secondClient = Redisson.create(redissonConfig(endpoint));
        String lockPrefix = keyPrefix + "lock:";
        firstLock = new RedissonDistributedLock(firstClient, lockPrefix, false);
        secondLock = new RedissonDistributedLock(secondClient, lockPrefix, false);

        LetoolRedisProperties properties = new LetoolRedisProperties();
        properties.getLock().setKeyPrefix(lockPrefix);
        properties.getCache().setLockKeyPrefix(keyPrefix + "rebuild:");
        properties.getCache().setLockWait(Duration.ofSeconds(5));
        LockTemplate lockTemplate = new LockTemplate(firstLock);
        RedisCacheTemplate cacheTemplate = new RedisCacheTemplate(
                redisTemplate, lockTemplate, properties.getCache().getLockKeyPrefix());
        redisFacade = new RedisFacade(
                redisTemplate, firstClient, lockTemplate, cacheTemplate, properties);
    }

    /** 清理随机命名空间，并关闭全部网络资源。 */
    @AfterAll
    static void disconnect() {
        if (redisTemplate != null && keyPrefix != null) {
            Set<String> keys = redisTemplate.keys(keyPrefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
            Set<String> remaining = redisTemplate.keys(keyPrefix + "*");
            assertNotNull(remaining);
            assertTrue(remaining.isEmpty(), "Redis Starter 集成测试不得遗留 Key");
        }
        if (firstClient != null) {
            firstClient.shutdown();
        }
        if (secondClient != null) {
            secondClient.shutdown();
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        if (redisContainer != null) {
            redisContainer.stop();
        }
    }

    /** 同一缓存的并发未命中只能触发一次数据源回调。 */
    @Test
    void concurrentMissesShouldLoadDatabaseOnce() throws Exception {
        String key = keyPrefix + "user:7";
        AtomicInteger loads = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<User>> futures = IntStream.range(0, 32)
                    .mapToObj(index -> pool.submit(() -> {
                        start.await();
                        return redisFacade.getOrLoad(
                                key,
                                User.class,
                                Duration.ofMinutes(5),
                                () -> {
                                    loads.incrementAndGet();
                                    LockSupport.parkNanos(Duration.ofMillis(100).toNanos());
                                    return new User(7L, "Leyla");
                                });
                    }))
                    .toList();
            start.countDown();

            for (Future<User> future : futures) {
                assertEquals("Leyla", future.get(10, TimeUnit.SECONDS).name());
            }
            assertEquals(1, loads.get());
        } finally {
            pool.shutdownNow();
        }
    }

    /** 空值短期占位阻止重复回源，关闭空值缓存后不应写入占位。 */
    @Test
    void nullValuePolicyShouldControlPenetrationProtection() {
        String protectedKey = keyPrefix + "missing:protected";
        AtomicInteger protectedLoads = new AtomicInteger();
        RedisCachePolicy<User> protectedPolicy = RedisCachePolicy.<User>builder(
                        Duration.ofMinutes(5))
                .cacheNull(Duration.ofSeconds(2))
                .build();

        assertNull(redisFacade.getOrLoad(
                protectedKey, User.class, protectedPolicy, () -> {
                    protectedLoads.incrementAndGet();
                    return null;
                }));
        assertNull(redisFacade.getOrLoad(
                protectedKey, User.class, protectedPolicy, () -> {
                    protectedLoads.incrementAndGet();
                    return null;
                }));
        assertEquals(1, protectedLoads.get());
        Long ttlMillis = redisTemplate.getExpire(protectedKey, TimeUnit.MILLISECONDS);
        assertNotNull(ttlMillis);
        assertTrue(ttlMillis > 0 && ttlMillis <= Duration.ofSeconds(2).toMillis());

        String unprotectedKey = keyPrefix + "missing:unprotected";
        AtomicInteger unprotectedLoads = new AtomicInteger();
        RedisCachePolicy<User> unprotectedPolicy = RedisCachePolicy.<User>builder(
                        Duration.ofMinutes(5))
                .doNotCacheNull()
                .build();
        assertNull(redisFacade.getOrLoad(
                unprotectedKey, User.class, unprotectedPolicy, () -> {
                    unprotectedLoads.incrementAndGet();
                    return null;
                }));
        assertNull(redisFacade.getOrLoad(
                unprotectedKey, User.class, unprotectedPolicy, () -> {
                    unprotectedLoads.incrementAndGet();
                    return null;
                }));
        assertEquals(2, unprotectedLoads.get());
        assertFalse(redisTemplate.hasKey(unprotectedKey));
    }

    /** 看门狗持锁超过初始租期后仍应保持互斥，主动释放后竞争方才能获取。 */
    @Test
    void watchdogShouldRenewUntilOwnerReleases() throws Exception {
        String key = "watchdog";
        LockHandle owner = firstLock.tryAcquire(LockRequest.watchdog(key, Duration.ZERO))
                .orElseThrow();
        try {
            Thread.sleep(WATCHDOG_TIMEOUT.plusMillis(500).toMillis());
            Optional<LockHandle> contender = secondLock.tryAcquire(
                    LockRequest.fixedLease(key, Duration.ofMillis(150), Duration.ofSeconds(1)));
            assertTrue(contender.isEmpty());
        } finally {
            owner.close();
        }

        LockHandle acquiredAfterRelease = secondLock.tryAcquire(
                LockRequest.fixedLease(key, Duration.ofSeconds(1), Duration.ofSeconds(1)))
                .orElseThrow();
        acquiredAfterRelease.close();
    }

    /** 固定租约到期允许新所有者接管，旧句柄关闭不得释放新所有者的锁。 */
    @Test
    void expiredFixedLeaseHandleShouldNotReleaseNewOwner() throws Exception {
        String key = "fixed-lease";
        LockHandle expiredOwner = firstLock.tryAcquire(LockRequest.fixedLease(
                        key, Duration.ZERO, Duration.ofMillis(300)))
                .orElseThrow();
        Thread.sleep(500);

        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch acquired = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<LockHandle> newOwner = new AtomicReference<>();
        Future<Boolean> ownership = pool.submit(() -> {
            LockHandle handle = secondLock.tryAcquire(
                            LockRequest.watchdog(key, Duration.ofSeconds(1)))
                    .orElseThrow();
            newOwner.set(handle);
            acquired.countDown();
            try (handle) {
                assertTrue(release.await(5, TimeUnit.SECONDS));
                return handle.isHeldByCurrentThread();
            }
        });
        try {
            assertTrue(acquired.await(5, TimeUnit.SECONDS));
            expiredOwner.close();
            assertTrue(secondLock.isLocked(key));
            assertNotNull(newOwner.get());
            release.countDown();
            assertTrue(ownership.get(5, TimeUnit.SECONDS));
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    /** 根据环境变量或临时容器确定测试 Redis。 */
    private static RedisEndpoint resolveEndpoint() {
        String host = System.getenv("LETOOL_TEST_REDIS_HOST");
        if (host == null || host.isBlank()) {
            redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
                    .withExposedPorts(6379);
            redisContainer.start();
            return new RedisEndpoint(
                    redisContainer.getHost(), redisContainer.getMappedPort(6379), null);
        }
        int port = Integer.parseInt(System.getenv().getOrDefault(
                "LETOOL_TEST_REDIS_PORT", "6379"));
        String password = System.getenv("LETOOL_TEST_REDIS_PASSWORD");
        return new RedisEndpoint(
                host, port, password == null || password.isBlank() ? null : password);
    }

    /** 创建使用较短看门狗超时的客户端，使续期语义可以在有限测试时间内验证。 */
    private static Config redissonConfig(RedisEndpoint endpoint) {
        Config config = new Config();
        config.setLockWatchdogTimeout(WATCHDOG_TIMEOUT.toMillis());
        var server = config.useSingleServer()
                .setAddress("redis://" + endpoint.host() + ":" + endpoint.port());
        if (endpoint.password() != null) {
            server.setPassword(endpoint.password());
        }
        return config;
    }

    /** 测试 Redis 连接参数。 */
    private record RedisEndpoint(String host, int port, String password) {
    }

    /** 缓存回源使用的最小业务对象。 */
    private record User(long id, String name) {
    }
}
