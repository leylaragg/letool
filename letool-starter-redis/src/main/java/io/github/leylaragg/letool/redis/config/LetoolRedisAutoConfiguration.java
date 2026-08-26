package io.github.leylaragg.letool.redis.config;

import io.github.leylaragg.letool.lock.config.LockAutoConfiguration;
import io.github.leylaragg.letool.lock.core.LockTemplate;
import io.github.leylaragg.letool.redis.RedisFacade;
import io.github.leylaragg.letool.redis.cache.RedisCacheTemplate;
import io.github.leylaragg.letool.redis.queue.RedisMessageQueueTemplate;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Redis 业务门面的自动配置。
 *
 * <p>只有应用上下文存在名为 {@code redisTemplate} 的对象模板时才创建门面 Bean，
 * 这样不会把仅配置字符串模板的应用误判为支持对象缓存。</p>
 */
@AutoConfiguration(after = {
        RedisAutoConfiguration.class,
        LetoolRedisBackendAutoConfiguration.class,
        LockAutoConfiguration.class
})
@ConditionalOnClass(RedisTemplate.class)
@EnableConfigurationProperties(LetoolRedisProperties.class)
public class LetoolRedisAutoConfiguration {

    /**
     * 创建基础 Redis 操作门面。
     *
     * @param redisTemplate 应用拥有的对象模板
     * @param redissonClient 可选的 Redisson 客户端
     * @param lockTemplate 可选的分布式锁模板
     * @param cacheTemplate 可选的缓存回源模板
     * @param properties Redis Starter 配置
     * @return 可注入业务服务的 Redis 门面
     */
    @Bean
    @ConditionalOnBean(name = "redisTemplate")
    @ConditionalOnMissingBean(RedisFacade.class)
    public RedisFacade redisFacade(
            @Qualifier("redisTemplate") RedisTemplate<?, ?> redisTemplate,
            ObjectProvider<RedissonClient> redissonClient,
            ObjectProvider<LockTemplate> lockTemplate,
            ObjectProvider<RedisCacheTemplate> cacheTemplate,
            LetoolRedisProperties properties) {
        return new RedisFacade(
                asObjectRedisTemplate(redisTemplate),
                redissonClient.getIfAvailable(),
                lockTemplate.getIfAvailable(),
                cacheTemplate.getIfAvailable(),
                properties);
    }

    /**
     * 在分布式锁模板可用时创建缓存回源保护组件。
     *
     * @param redisTemplate 应用拥有的对象模板
     * @param lockTemplate 自动释放锁的模板
     * @param properties Redis Starter 配置
     * @return 缓存回源模板
     */
    @Bean
    @ConditionalOnBean(LockTemplate.class)
    @ConditionalOnMissingBean(RedisCacheTemplate.class)
    public RedisCacheTemplate redisCacheTemplate(
            @Qualifier("redisTemplate") RedisTemplate<?, ?> redisTemplate,
            LockTemplate lockTemplate,
            LetoolRedisProperties properties) {
        return new RedisCacheTemplate(
                asObjectRedisTemplate(redisTemplate),
                lockTemplate,
                properties.getCache().getLockKeyPrefix());
    }

    /**
     * 创建 Redis List/Stream 消息操作模板。
     *
     * @param redisTemplate 应用拥有的对象模板
     * @return Redis 消息操作模板
     */
    @Bean
    @ConditionalOnBean(name = "redisTemplate")
    @ConditionalOnMissingBean(RedisMessageQueueTemplate.class)
    public RedisMessageQueueTemplate redisMessageQueueTemplate(
            @Qualifier("redisTemplate") RedisTemplate<?, ?> redisTemplate) {
        return new RedisMessageQueueTemplate(asObjectRedisTemplate(redisTemplate));
    }

    /**
     * 将 Spring Boot 的宽泛模板视图适配为 Redis 门面所需类型。
     * 泛型不会改变实际序列化协议，协议仍由应用提供的模板决定。
     */
    @SuppressWarnings("unchecked")
    private static RedisTemplate<String, Object> asObjectRedisTemplate(RedisTemplate<?, ?> redisTemplate) {
        return (RedisTemplate<String, Object>) redisTemplate;
    }
}
