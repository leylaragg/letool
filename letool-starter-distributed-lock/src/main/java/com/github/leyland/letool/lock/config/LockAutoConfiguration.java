package com.github.leyland.letool.lock.config;

import com.github.leyland.letool.lock.aspect.IdempotentAspect;
import com.github.leyland.letool.lock.aspect.LockAspect;
import com.github.leyland.letool.lock.core.DistributedLock;
import com.github.leyland.letool.lock.core.LockTemplate;
import com.github.leyland.letool.lock.core.RedisPessimisticLock;
import com.github.leyland.letool.lock.idempotent.IdempotentService;
import com.github.leyland.letool.tool.redis.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * letool-starter-distributed-lock 模块的 Spring Boot 自动配置类。
 *
 * <p>负责按条件装配分布式锁和幂等性相关的所有 Bean，装配链路如下：</p>
 *
 * <h3>分布式锁链路</h3>
 * <ol>
 *   <li>{@link RedisPessimisticLock} —— 实现 {@link DistributedLock} 接口</li>
 *   <li>{@link LockTemplate} —— 基于 {@link DistributedLock} 提供函数式 API</li>
 *   <li>{@link LockAspect} —— 基于 {@link LockTemplate} 提供 AOP 声明式锁</li>
 * </ol>
 *
 * <h3>幂等性链路</h3>
 * <ol>
 *   <li>{@link IdempotentService} —— 核心幂等检查逻辑</li>
 *   <li>{@link IdempotentAspect} —— 基于 {@link IdempotentService} 提供 AOP 声明式幂等</li>
 * </ol>
 *
 * <p>激活条件：</p>
 * <ul>
 *   <li>classpath 上存在 {@link RedisUtil} 类（即引入了 letool-starter-tool 模块）</li>
 *   <li>配置项 {@code letool.lock.enabled} 为 {@code true}（默认为 true）</li>
 * </ul>
 *
 * @author leyland
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(LockProperties.class)
@ConditionalOnClass(RedisUtil.class)
@ConditionalOnProperty(prefix = "letool.lock", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LockAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LockAutoConfiguration.class);

    // ======================== 分布式锁相关 Bean ========================

    /**
     * 注册 {@link DistributedLock} 的 Redis 悲观锁实现 Bean。
     *
     * <p>仅在存在 {@link RedisUtil} Bean 时装配。使用 {@link LockProperties}
     * 中的配置初始化锁的前缀、超时等参数。</p>
     *
     * @param redisUtil  Redis 操作工具类（由 letool-starter-tool 提供）
     * @param properties 分布式锁配置属性
     * @return Redis 悲观锁实例
     */
    @Bean
    @ConditionalOnBean(RedisUtil.class)
    @ConditionalOnProperty(prefix = "letool.lock", name = "backend", havingValue = "redis", matchIfMissing = true)
    @ConditionalOnMissingBean(DistributedLock.class)
    public DistributedLock distributedLock(RedisUtil redisUtil, LockProperties properties) {
        log.info("Initializing Redis distributed lock: prefix={}", properties.getPessimistic().getLockPrefix());
        return new RedisPessimisticLock(redisUtil, properties);
    }

    /**
     * 注册 {@link LockTemplate} Bean，为用户提供函数式编程风格的锁操作 API。
     *
     * <p>仅在存在 {@link DistributedLock} Bean 时装配。</p>
     *
     * @param lock 分布式锁实例
     * @return 锁模板实例
     */
    @Bean
    @ConditionalOnBean(DistributedLock.class)
    @ConditionalOnMissingBean(LockTemplate.class)
    public LockTemplate lockTemplate(DistributedLock lock) {
        return new LockTemplate(lock);
    }

    /**
     * 注册 {@link LockAspect} Bean，提供基于 {@code @Lock} 注解的声明式分布式锁。
     *
     * <p>仅在存在 {@link LockTemplate} Bean 时装配。</p>
     *
     * @param lockTemplate 锁模板实例
     * @return 锁切面实例
     */
    @Bean
    @ConditionalOnBean(LockTemplate.class)
    @ConditionalOnMissingBean(LockAspect.class)
    public LockAspect lockAspect(LockTemplate lockTemplate) {
        return new LockAspect(lockTemplate);
    }

    // ======================== 幂等性相关 Bean ========================

    /**
     * 注册 {@link IdempotentService} Bean，提供幂等检查的核心服务。
     *
     * <p>仅在存在 {@link RedisUtil} Bean 时装配。使用 {@link LockProperties}
     * 中的幂等配置初始化 key 前缀。</p>
     *
     * @param redisUtil  Redis 操作工具类
     * @param properties 分布式锁配置属性
     * @return 幂等服务实例
     */
    @Bean
    @ConditionalOnBean(RedisUtil.class)
    @ConditionalOnMissingBean(IdempotentService.class)
    @ConditionalOnExpression("'${letool.lock.backend:redis}' == 'redis' && '${letool.lock.idempotent.enabled:true}' == 'true'")
    public IdempotentService idempotentService(RedisUtil redisUtil, LockProperties properties) {
        return new IdempotentService(redisUtil, properties);
    }

    /**
     * 注册 {@link IdempotentAspect} Bean，提供基于 {@code @Idempotent} 注解的声明式幂等控制。
     *
     * <p>仅在存在 {@link IdempotentService} Bean 时装配。</p>
     *
     * @param idempotentService 幂等服务实例
     * @return 幂等切面实例
     */
    @Bean
    @ConditionalOnBean(IdempotentService.class)
    @ConditionalOnMissingBean(IdempotentAspect.class)
    @ConditionalOnExpression("'${letool.lock.backend:redis}' == 'redis' && '${letool.lock.idempotent.enabled:true}' == 'true'")
    public IdempotentAspect idempotentAspect(IdempotentService idempotentService) {
        return new IdempotentAspect(idempotentService);
    }
}
