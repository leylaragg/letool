package com.github.leyland.letool.cache.config;

import com.github.leyland.letool.cache.aspect.CacheAspect;
import com.github.leyland.letool.cache.core.CacheConfig;
import com.github.leyland.letool.cache.core.CacheInvalidationPublisher;
import com.github.leyland.letool.cache.core.CacheManager;
import com.github.leyland.letool.cache.core.CacheRecoveryScheduler;
import com.github.leyland.letool.cache.core.MultiLevelCache;
import com.github.leyland.letool.cache.core.RedisCacheInvalidationListener;
import com.github.leyland.letool.cache.core.RedisCacheInvalidationPublisher;
import com.github.leyland.letool.cache.serializer.CacheSerializer;
import com.github.leyland.letool.cache.serializer.JacksonCacheSerializer;
import com.github.leyland.letool.cache.support.CacheMonitor;
import com.github.leyland.letool.tool.redis.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;

/**
 * 缓存自动配置 —— 注册 {@link CacheManager}、{@link CacheSerializer}、{@link CacheAspect} 等 Bean.
 *
 * <p>启用条件：{@code letool.cache.enabled=true}（默认开启）.
 * 如果 classpath 中存在 {@link RedisUtil}，CacheManager 会自动启用 L2 Redis 缓存.</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(CacheProperties.class)
@ConditionalOnProperty(prefix = "letool.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CacheAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CacheAutoConfiguration.class);

    /**
     * 注册默认的 Jackson 序列化器.
     */
    @Bean
    @ConditionalOnMissingBean(CacheSerializer.class)
    public CacheSerializer cacheSerializer() {
        return new JacksonCacheSerializer();
    }

    /**
     * 注册缓存管理器 —— 如果存在 RedisUtil 则传给 CacheManager 以启用 L2.
     */
    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    public CacheManager cacheManager(CacheSerializer serializer,
                                     CacheProperties properties,
                                     @org.springframework.beans.factory.annotation.Autowired(required = false) RedisUtil redisUtil,
                                     @org.springframework.beans.factory.annotation.Autowired(required = false) CacheInvalidationPublisher invalidationPublisher) {
        CacheManager manager = new CacheManager(
                redisUtil,
                serializer,
                properties.isL1Enabled(),
                properties.isL2Enabled(),
                properties.getRedisPrefix(),
                invalidationPublisher);
        log.info("CacheManager initialized, L2 Redis: {}", redisUtil != null ? "enabled" : "disabled (Redis not available)");
        return manager;
    }

    @Bean
    @ConditionalOnBean(RedisUtil.class)
    @ConditionalOnMissingBean(CacheInvalidationPublisher.class)
    @ConditionalOnProperty(prefix = "letool.cache.invalidation", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CacheInvalidationPublisher cacheInvalidationPublisher(RedisUtil redisUtil, CacheProperties properties) {
        return new RedisCacheInvalidationPublisher(redisUtil, properties.getInvalidation().getChannel());
    }

    @Bean
    @ConditionalOnBean(CacheManager.class)
    @ConditionalOnMissingBean(RedisCacheInvalidationListener.class)
    @ConditionalOnProperty(prefix = "letool.cache.invalidation", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RedisCacheInvalidationListener redisCacheInvalidationListener(CacheManager cacheManager) {
        return new RedisCacheInvalidationListener(cacheManager);
    }

    @Bean
    @ConditionalOnClass(RedisMessageListenerContainer.class)
    @ConditionalOnBean({RedisCacheInvalidationListener.class, RedisConnectionFactory.class})
    @ConditionalOnMissingBean(name = "cacheInvalidationListenerContainer")
    public RedisMessageListenerContainer cacheInvalidationListenerContainer(RedisConnectionFactory connectionFactory,
                                                                            RedisCacheInvalidationListener listener,
                                                                            CacheProperties properties) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) ->
                listener.onMessage(new String(message.getBody(), StandardCharsets.UTF_8)),
                new ChannelTopic(properties.getInvalidation().getChannel()));
        return container;
    }

    @Bean
    @ConditionalOnBean(CacheManager.class)
    @ConditionalOnMissingBean(CacheRecoveryScheduler.class)
    @ConditionalOnProperty(prefix = "letool.cache.degradation", name = "recovery-enabled", havingValue = "true", matchIfMissing = true)
    public CacheRecoveryScheduler cacheRecoveryScheduler(CacheManager cacheManager, CacheProperties properties) {
        return new CacheRecoveryScheduler(cacheManager, properties.getDegradation().getRecoveryInterval());
    }

    /**
     * 注册缓存 AOP 切面.
     */
    @Bean
    @ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
    @ConditionalOnBean(CacheManager.class)
    @ConditionalOnMissingBean(CacheAspect.class)
    @ConditionalOnProperty(prefix = "letool.cache.annotation", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CacheAspect cacheAspect(CacheManager cacheManager) {
        return new CacheAspect(cacheManager);
    }

    /**
     * 注册缓存监控.
     */
    @Bean
    @ConditionalOnBean(CacheManager.class)
    @ConditionalOnMissingBean(CacheMonitor.class)
    @ConditionalOnProperty(prefix = "letool.cache.monitoring", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CacheMonitor cacheMonitor(CacheManager cacheManager) {
        return new CacheMonitor(cacheManager);
    }

    /**
     * 根据配置文件中的实例列表预注册缓存.
     */
    @Bean
    @ConditionalOnMissingBean(name = "cacheInstancesInitializer")
    public Object cacheInstancesInitializer(CacheManager cacheManager, CacheProperties properties) {
        for (CacheProperties.InstanceConfig ic : properties.getInstances()) {
            CacheConfig<Object, Object> config = CacheConfig.builder(ic.getName())
                    .l1Enabled(ic.isL1Enabled())
                    .l1MaxSize(ic.getL1MaxSize())
                    .l1Ttl(ic.getL1Ttl())
                    .l2Ttl(ic.getL2Ttl())
                    .l2Enabled(ic.isL2Enabled())
                    .strongConsistency(ic.isStrongConsistency())
                    .nullValueCache(ic.isNullValueCache())
                    .nullValueTtl(ic.getNullValueTtl())
                    .redisKeyPrefix(properties.getRedisPrefix());
            MultiLevelCache<Object, Object> cache = cacheManager.getOrCreate(config);
            log.info("Cache instance [{}] registered: L1(max={}, ttl={}) L2(ttl={}) nullCache={}",
                    ic.getName(), ic.getL1MaxSize(), ic.getL1Ttl(), ic.getL2Ttl(), ic.isNullValueCache());
        }
        return new Object();
    }
}
