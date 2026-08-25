package io.github.leylaragg.letool.cache.integration;

import io.github.leylaragg.letool.cache.core.CacheConfig;
import io.github.leylaragg.letool.cache.core.CacheManager;
import io.github.leylaragg.letool.cache.core.MultiLevelCache;
import io.github.leylaragg.letool.cache.core.MultiLevelSetCache;
import io.github.leylaragg.letool.cache.core.RedisCacheInvalidationListener;
import io.github.leylaragg.letool.cache.core.RedisCacheInvalidationPublisher;
import io.github.leylaragg.letool.cache.config.CacheAutoConfiguration;
import io.github.leylaragg.letool.cache.config.CacheProperties;
import io.github.leylaragg.letool.cache.serializer.JacksonCacheSerializer;
import io.github.leylaragg.letool.tool.redis.FastJson2JsonRedisSerializer;
import io.github.leylaragg.letool.tool.redis.RedisUtil;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Letool 缓存修复的真实 Redis 集成门禁。
 *
 * <p>仅由 redis-integration Profile 启用；未提供连接环境变量时使用 Redis 7.2 容器。</p>
 */
@DisplayName("Redis 缓存加固集成测试")
@Tag("redis-integration")
class RedisCacheHardeningIntegrationTest {

    private static GenericContainer<?> redisContainer;
    private static LettuceConnectionFactory connectionFactory;
    private static RedisTemplate<String, Object> redisTemplate;
    private static StringRedisTemplate stringRedisTemplate;
    private static RedisUtil redisUtil;
    private static String redisPrefix;

    @BeforeAll
    static void connect() {
        String host = System.getenv("LETOOL_TEST_REDIS_HOST");
        int port;
        if (host == null || host.isBlank()) {
            redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
                    .withExposedPorts(6379);
            redisContainer.start();
            host = redisContainer.getHost();
            port = redisContainer.getMappedPort(6379);
        } else {
            port = Integer.parseInt(System.getenv().getOrDefault(
                    "LETOOL_TEST_REDIS_PORT", "6379"));
        }
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                host,
                port
        );
        String password = System.getenv("LETOOL_TEST_REDIS_PASSWORD");
        if (password != null && !password.isBlank()) {
            configuration.setPassword(RedisPassword.of(password));
        }
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();

        redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        StringRedisSerializer keySerializer = StringRedisSerializer.UTF_8;
        redisTemplate.setKeySerializer(keySerializer);
        redisTemplate.setHashKeySerializer(keySerializer);
        FastJson2JsonRedisSerializer<Object> valueSerializer =
                new FastJson2JsonRedisSerializer<>(Object.class);
        redisTemplate.setValueSerializer(valueSerializer);
        redisTemplate.setHashValueSerializer(valueSerializer);
        redisTemplate.afterPropertiesSet();
        stringRedisTemplate = new StringRedisTemplate(connectionFactory);
        redisUtil = new RedisUtil(redisTemplate);
        redisPrefix = "letool:audit:" + UUID.randomUUID().toString().replace("-", "") + ":";
    }

    @AfterAll
    static void disconnect() {
        if (redisTemplate != null && redisPrefix != null) {
            Set<String> keys = redisTemplate.keys(redisPrefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
            Set<String> remaining = redisTemplate.keys(redisPrefix + "*");
            assertNotNull(remaining);
            assertTrue(remaining.isEmpty(), "真实 Redis 集成测试结束后不得遗留审计 Key");
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        if (redisContainer != null) {
            redisContainer.stop();
        }
    }

    @Test
    @DisplayName("600 Key 批量读写分块有界且版本元数据带 TTL")
    void batchIoShouldBeBoundedAndVersionMetadataShouldExpire() {
        CacheConfig<String, String> config = CacheConfig.<String, String>builder("batch")
                .redisKeyPrefix(redisPrefix)
                .l1Ttl(Duration.ofMinutes(10))
                .l2Ttl(Duration.ofHours(1))
                .redisBatchSize(256)
                .versionMetadataRetention(Duration.ofDays(7))
                .build();
        MultiLevelCache<String, String> cache = new MultiLevelCache<>(
                config, redisUtil, new JacksonCacheSerializer());
        Map<String, String> entries = new LinkedHashMap<>();
        for (int index = 0; index < 600; index++) {
            entries.put("key-" + index, "value-" + index);
        }

        cache.putAll(entries);
        // 仅清空本机快照，验证从未创建区域 Epoch 时可从真实 Redis 安全回填 L1。
        cache.evictLocalAll();
        long batchesAfterWrite = cache.stats().getRedisBatchCount();
        Map<String, String> loaded = cache.getAllPresent(
                new LinkedHashSet<>(entries.keySet()));

        assertEquals(entries, loaded);
        assertFalse(cache.isL2Degraded());
        assertEquals(entries.size(), cache.estimatedSize());
        assertEquals(3, cache.stats().getRedisBatchCount() - batchesAfterWrite);
        long batchesAfterRead = cache.stats().getRedisBatchCount();
        assertEquals("value-0", cache.getIfPresent("key-0"));
        assertEquals(batchesAfterRead, cache.stats().getRedisBatchCount());
        Set<String> versionKeys = redisTemplate.keys(
                redisPrefix + "%META%:batch:*:version");
        assertNotNull(versionKeys);
        assertEquals(600, versionKeys.size());
        for (String versionKey : versionKeys.stream().limit(10).toList()) {
            Long ttl = redisTemplate.getExpire(versionKey);
            assertNotNull(ttl);
            assertTrue(ttl > Duration.ofDays(6).toSeconds());
            assertTrue(ttl <= Duration.ofDays(7).toSeconds());
        }
    }

    @Test
    @DisplayName("Boot 默认 JDK Key 模板可通过缓存私有 String Key 视图完成区域清理")
    void bootDefaultTemplateShouldSupportRegionCleanupThroughPrivateCacheView() {
        RedisTemplate<String, Object> bootDefaultTemplate = new RedisTemplate<>();
        bootDefaultTemplate.setConnectionFactory(connectionFactory);
        bootDefaultTemplate.afterPropertiesSet();
        assertFalse(bootDefaultTemplate.getKeySerializer() instanceof StringRedisSerializer);
        RedisUtil businessRedisUtil = new RedisUtil(bootDefaultTemplate);
        CacheProperties properties = new CacheProperties();
        properties.setRedisPrefix(redisPrefix);
        CacheManager cacheManager = new CacheAutoConfiguration().cacheManager(
                new JacksonCacheSerializer(), properties, businessRedisUtil, null);
        MultiLevelCache<String, String> cache = cacheManager.getOrCreate(
                CacheConfig.<String, String>builder("boot-default")
                        .redisKeyPrefix(redisPrefix)
                        .l1Enabled(false)
                        .build());

        cache.put("rule:1", "value-1");
        assertEquals("value-1", cache.getIfPresent("rule:1"));
        cache.evictAll();

        assertNull(cache.getIfPresent("rule:1"));
        assertFalse(cache.isL2Degraded());
        assertFalse(businessRedisUtil.getTemplate().getKeySerializer()
                instanceof StringRedisSerializer);
    }

    @Test
    @DisplayName("Set 前缀失效只删除匹配业务 Key")
    void setPrefixEvictionShouldKeepUnmatchedKeys() {
        CacheConfig<String, String> config = CacheConfig.<String, String>builder("set-prefix")
                .redisKeyPrefix(redisPrefix)
                .strongConsistency(false)
                .build();
        MultiLevelSetCache<String, String> cache = new CacheManager(
                redisUtil, new JacksonCacheSerializer())
                .getOrCreateSetCache(config, java.util.function.Function.identity(), String.class);
        cache.add("project:1", "R1");
        cache.add("project:2", "R2");
        cache.add("tenant:1", "R3");

        cache.evictByPrefix("project:");

        assertTrue(cache.getMembers("project:1").isEmpty());
        assertTrue(cache.getMembers("project:2").isEmpty());
        assertEquals(Set.of("R3"), cache.getMembers("tenant:1"));
    }

    @Test
    @DisplayName("双节点 Pub/Sub 使用原始字符串协议精确清理远端 L1")
    void pubSubShouldInvalidateRemoteLocalSnapshot() throws Exception {
        String channel = redisPrefix + "invalidation";
        RedisCacheInvalidationPublisher publisher =
                new RedisCacheInvalidationPublisher(stringRedisTemplate, channel);
        CacheManager writerManager = new CacheManager(
                redisUtil, new JacksonCacheSerializer(), true, true, redisPrefix, publisher);
        CacheManager readerManager = new CacheManager(
                redisUtil, new JacksonCacheSerializer(), true, true, redisPrefix,
                io.github.leylaragg.letool.cache.core.CacheInvalidationPublisher.noop());
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        RedisCacheInvalidationListener listener =
                new RedisCacheInvalidationListener(readerManager);
        container.addMessageListener(
                (message, pattern) -> listener.onMessage(
                        new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8)),
                new ChannelTopic(channel));
        container.afterPropertiesSet();
        container.start();
        try {
            CacheConfig<String, String> config = CacheConfig.<String, String>builder("pubsub-set")
                    .redisKeyPrefix(redisPrefix)
                    .strongConsistency(false)
                    .build();
            MultiLevelSetCache<String, String> writer = writerManager.getOrCreateSetCache(
                    config, java.util.function.Function.identity(), String.class);
            MultiLevelSetCache<String, String> reader = readerManager.getOrCreateSetCache(
                    config, java.util.function.Function.identity(), String.class);
            writer.add("project:1", "R1");
            assertEquals(Set.of("R1"), reader.getMembers("project:1"));

            writer.removeKey("project:1");

            Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertTrue(reader.getMembers("project:1").isEmpty()));
        } finally {
            container.stop();
            container.destroy();
        }
    }
}
