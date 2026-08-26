package io.github.leylaragg.letool.redis.config;

import io.github.leylaragg.letool.lock.config.LockAutoConfiguration;
import io.github.leylaragg.letool.lock.core.DistributedLock;
import io.github.leylaragg.letool.lock.idempotent.IdempotentStore;
import io.github.leylaragg.letool.redis.idempotent.RedisIdempotentStore;
import io.github.leylaragg.letool.redis.lock.RedissonDistributedLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 将 Redis/Redisson 适配成 distributed-lock 模块定义的后端 SPI。
 */
@AutoConfiguration(before = LockAutoConfiguration.class)
@EnableConfigurationProperties(LetoolRedisProperties.class)
public class LetoolRedisBackendAutoConfiguration {

    /**
     * @param client Redisson 客户端
     * @param properties Redis Starter 配置
     * @return Redisson 分布式锁后端
     */
    @Bean
    @ConditionalOnClass(RedissonClient.class)
    @ConditionalOnBean(RedissonClient.class)
    @ConditionalOnMissingBean(DistributedLock.class)
    public DistributedLock redissonDistributedLock(
            RedissonClient client, LetoolRedisProperties properties) {
        return new RedissonDistributedLock(
                client,
                properties.getLock().getKeyPrefix(),
                properties.getLock().isFair());
    }

    /**
     * @param template 字符串 Redis 模板
     * @param properties Redis Starter 配置
     * @return Redis 幂等占位存储
     */
    @Bean
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(IdempotentStore.class)
    public IdempotentStore redisIdempotentStore(
            StringRedisTemplate template, LetoolRedisProperties properties) {
        return new RedisIdempotentStore(
                template, properties.getIdempotent().getKeyPrefix());
    }
}
