package com.github.leyland.letool.cache.config;

import com.github.leyland.letool.cache.aspect.CacheAspect;
import com.github.leyland.letool.cache.core.CacheConfig;
import com.github.leyland.letool.cache.core.CacheManager;
import com.github.leyland.letool.cache.core.MultiLevelCache;
import com.github.leyland.letool.cache.serializer.CacheSerializer;
import com.github.leyland.letool.cache.serializer.JacksonCacheSerializer;
import com.github.leyland.letool.cache.support.CacheMonitor;
import com.github.leyland.letool.tool.redis.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

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
    public CacheSerializer cacheSerializer() {
        return new JacksonCacheSerializer();
    }

    /**
     * 注册缓存管理器 —— 如果存在 RedisUtil 则传给 CacheManager 以启用 L2.
     */
    @Bean
    public CacheManager cacheManager(CacheSerializer serializer,
                                     @org.springframework.beans.factory.annotation.Autowired(required = false) RedisUtil redisUtil) {
        CacheManager manager = new CacheManager(redisUtil, serializer);
        log.info("CacheManager initialized, L2 Redis: {}", redisUtil != null ? "enabled" : "disabled (Redis not available)");
        return manager;
    }

    /**
     * 注册缓存 AOP 切面.
     */
    @Bean
    @ConditionalOnBean(CacheManager.class)
    public CacheAspect cacheAspect(CacheManager cacheManager) {
        return new CacheAspect(cacheManager);
    }

    /**
     * 注册缓存监控.
     */
    @Bean
    @ConditionalOnBean(CacheManager.class)
    @ConditionalOnProperty(prefix = "letool.cache.monitoring", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CacheMonitor cacheMonitor(CacheManager cacheManager) {
        return new CacheMonitor(cacheManager);
    }

    /**
     * 根据配置文件中的实例列表预注册缓存.
     */
    @Bean
    public Object cacheInstancesInitializer(CacheManager cacheManager, CacheProperties properties) {
        for (CacheProperties.InstanceConfig ic : properties.getInstances()) {
            CacheConfig<Object, Object> config = CacheConfig.builder(ic.getName())
                    .l1MaxSize(ic.getL1MaxSize())
                    .l1Ttl(ic.getL1Ttl())
                    .l2Ttl(ic.getL2Ttl())
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
