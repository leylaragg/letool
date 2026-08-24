package io.github.leylaragg.letool.cache.config;

import io.github.leylaragg.letool.cache.aspect.CacheAspect;
import io.github.leylaragg.letool.cache.consistency.CacheConsistencyMode;
import io.github.leylaragg.letool.cache.consistency.CacheInvalidationEventStore;
import io.github.leylaragg.letool.cache.consistency.CacheMutationCoordinator;
import io.github.leylaragg.letool.cache.consistency.SpringCacheMutationCoordinator;
import io.github.leylaragg.letool.cache.consistency.RedisCacheFenceStore;
import io.github.leylaragg.letool.cache.consistency.JdbcCacheInvalidationEventStore;
import io.github.leylaragg.letool.cache.consistency.CacheInvalidationRecovery;
import io.github.leylaragg.letool.cache.consistency.CacheInvalidationRecoveryScheduler;
import io.github.leylaragg.letool.cache.core.CacheConfig;
import io.github.leylaragg.letool.cache.core.CacheInvalidationPublisher;
import io.github.leylaragg.letool.cache.core.CacheManager;
import io.github.leylaragg.letool.cache.core.CacheRecoveryScheduler;
import io.github.leylaragg.letool.cache.core.MultiLevelCache;
import io.github.leylaragg.letool.cache.core.RedisCacheInvalidationListener;
import io.github.leylaragg.letool.cache.core.RedisCacheInvalidationPublisher;
import io.github.leylaragg.letool.cache.serializer.CacheSerializer;
import io.github.leylaragg.letool.cache.serializer.JacksonCacheSerializer;
import io.github.leylaragg.letool.cache.support.CacheMonitor;
import io.github.leylaragg.letool.tool.redis.RedisUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 缓存 starter 的自动配置入口。
 *
 * <p>这个类负责把二级缓存所需的核心组件注册到 Spring 容器中，包括：
 * 缓存管理器、序列化器、注解切面、监控器、Redis 失效消息发布器、
 * Redis 失效消息监听器，以及 Redis 恢复探测定时任务。</p>
 *
 * <p>默认情况下 {@code letool.cache.enabled=true}，项目引入 starter 后即可自动启用。
 * 如果业务系统没有引入 Redis，框架会自动退化为本地 L1 缓存；如果存在
 * {@link RedisUtil}，则会同时启用 Redis L2 缓存。</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(CacheProperties.class)
@ConditionalOnProperty(prefix = "letool.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CacheAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CacheAutoConfiguration.class);

    /**
     * 注册基于 Spring 事务的缓存修改协调器。
     *
     * @param transactionManager 业务数据库事务管理器
     * @return 缓存修改协调器
     */
    @Bean
    @ConditionalOnBean(PlatformTransactionManager.class)
    @Conditional(ExactSingleTransactionManagerCondition.class)
    @ConditionalOnMissingBean(CacheMutationCoordinator.class)
    public CacheMutationCoordinator cacheMutationCoordinator(
            PlatformTransactionManager transactionManager,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            RedisCacheFenceStore fenceStore,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            CacheInvalidationEventStore eventStore) {
        return new SpringCacheMutationCoordinator(transactionManager, fenceStore, eventStore);
    }

    /** 注册 Redis 单 Key 写入围栏。 */
    @Bean
    @ConditionalOnBean(RedisUtil.class)
    @ConditionalOnMissingBean(RedisCacheFenceStore.class)
    @Conditional(DurableCacheConfiguredCondition.class)
    public RedisCacheFenceStore redisCacheFenceStore(
            RedisUtil redisUtil, CacheProperties properties) {
        return new RedisCacheFenceStore(
                redisUtil, properties.getRedisPrefix(), properties.getConsistency().getFenceTtl());
    }

    /** 注册 DURABLE Outbox 单批恢复处理器。 */
    @Bean
    @ConditionalOnBean({CacheInvalidationEventStore.class, RedisCacheFenceStore.class})
    @ConditionalOnMissingBean(CacheInvalidationRecovery.class)
    @Conditional(DurableCacheConfiguredCondition.class)
    public CacheInvalidationRecovery cacheInvalidationRecovery(
            CacheInvalidationEventStore eventStore,
            RedisCacheFenceStore fenceStore,
            CacheProperties properties) {
        CacheProperties.Consistency consistency = properties.getConsistency();
        return new CacheInvalidationRecovery(
                eventStore, fenceStore, UUID.randomUUID().toString(),
                consistency.getRecoveryBatchSize(), consistency.getRecoveryLease(),
                consistency.getRetryBaseDelay());
    }

    /** 注册 DURABLE Outbox 后台恢复调度器。 */
    @Bean(destroyMethod = "close")
    @ConditionalOnBean(CacheInvalidationRecovery.class)
    @ConditionalOnMissingBean(CacheInvalidationRecoveryScheduler.class)
    public CacheInvalidationRecoveryScheduler cacheInvalidationRecoveryScheduler(
            CacheInvalidationRecovery recovery, CacheProperties properties) {
        return new CacheInvalidationRecoveryScheduler(
                recovery,
                properties.getConsistency().getRecoveryInterval(),
                properties.getConsistency().getCleanupInterval(),
                properties.getConsistency().getCompletedRetention(),
                properties.getConsistency().getCleanupBatchSize());
    }

    /**
     * 校验 DURABLE 模式所需基础设施，防止强一致配置被静默降级。
     *
     * @param properties 缓存配置
     * @param redisProvider Redis 操作入口候选
     * @param coordinatorProvider 已绑定正确事务管理器的缓存修改协调器候选
     * @param eventStoreProvider 持久化事件仓储候选
     * @return 启动期校验标记对象
     */
    @Bean
    @ConditionalOnMissingBean(name = "cacheConsistencyInfrastructureValidator")
    public Object cacheConsistencyInfrastructureValidator(
            CacheProperties properties,
            ObjectProvider<RedisUtil> redisProvider,
            ObjectProvider<CacheMutationCoordinator> coordinatorProvider,
            ObjectProvider<CacheInvalidationEventStore> eventStoreProvider) {
        boolean durableConfigured = properties.getConsistency().getMode() == CacheConsistencyMode.DURABLE
                || properties.getInstances().stream()
                .anyMatch(instance -> instance.getConsistencyMode() == CacheConsistencyMode.DURABLE);
        if (durableConfigured && (redisProvider.getIfUnique() == null
                || coordinatorProvider.getIfUnique() == null
                || eventStoreProvider.getIfUnique() == null)) {
            throw new IllegalStateException(
                    "DURABLE 缓存一致性模式要求唯一 Redis、CacheMutationCoordinator 和 CacheInvalidationEventStore；"
                            + "多数据源项目必须显式声明绑定正确事务管理器/数据源的实现");
        }
        return new Object();
    }

    /**
     * 注册默认的缓存序列化器。
     *
     * <p>Redis 中保存的是二进制数据，框架默认使用 Jackson 实现对象和字节数组之间的转换。
     * 如果业务项目需要 Kryo、Fastjson、Protobuf 等其他序列化方式，只需要自行声明
     * {@link CacheSerializer} Bean，即可覆盖这里的默认实现。</p>
     */
    @Bean
    @ConditionalOnMissingBean(CacheSerializer.class)
    public CacheSerializer cacheSerializer() {
        return new JacksonCacheSerializer();
    }

    /**
     * 注册缓存管理器。
     *
     * <p>{@link CacheManager} 是所有缓存实例的统一入口，负责按名称创建、复用和查询缓存。
     * 这里会把全局开关、Redis 前缀、序列化器以及失效消息发布器统一传入管理器。
     * {@link RedisUtil} 是可选依赖：存在时启用 L2，不存在时只使用本地 Caffeine。</p>
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

    /**
     * 注册缓存失效广播专用的字符串 Redis 模板。
     *
     * <p>该模板只使用字符串序列化器，确保失效协议不会被业务对象模板的 Fastjson2、Jackson
     * 或 JDK 序列化器再次包装。</p>
     *
     * @param connectionFactory Redis 连接工厂
     * @return 失效广播专用字符串模板
     */
    @Bean("letoolCacheInvalidationStringRedisTemplate")
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnMissingBean(name = "letoolCacheInvalidationStringRedisTemplate")
    @ConditionalOnProperty(prefix = "letool.cache.invalidation", name = "enabled", havingValue = "true", matchIfMissing = true)
    public StringRedisTemplate letoolCacheInvalidationStringRedisTemplate(
            RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * 注册 Redis 版的本地缓存失效消息发布器。
     *
     * <p>当某个 JVM 执行 put/evict/clear 时，需要通知其他 JVM 清理自己的 L1 本地缓存。
     * 这里通过 Redis Pub/Sub 广播失效消息，从而避免多实例部署时读到旧的本地数据。</p>
     */
    @Bean("letoolCacheInvalidationPublisher")
    @ConditionalOnBean(name = "letoolCacheInvalidationStringRedisTemplate")
    @ConditionalOnMissingBean(
            value = CacheInvalidationPublisher.class,
            name = "letoolCacheInvalidationPublisher")
    @ConditionalOnProperty(prefix = "letool.cache.invalidation", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CacheInvalidationPublisher letoolCacheInvalidationPublisher(
            @Qualifier("letoolCacheInvalidationStringRedisTemplate") StringRedisTemplate redisTemplate,
            CacheProperties properties) {
        return new RedisCacheInvalidationPublisher(redisTemplate, properties.getInvalidation().getChannel());
    }

    /**
     * 注册 Redis 失效消息监听器。
     *
     * <p>监听器只负责解析消息并调用 {@link CacheManager} 清理本机 L1。
     * 真正的 Redis 订阅动作由 {@link RedisMessageListenerContainer} 完成。</p>
     */
    @Bean("letoolCacheInvalidationListener")
    @ConditionalOnBean(CacheManager.class)
    @ConditionalOnMissingBean(
            value = RedisCacheInvalidationListener.class,
            name = "letoolCacheInvalidationListener")
    @ConditionalOnProperty(prefix = "letool.cache.invalidation", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RedisCacheInvalidationListener letoolCacheInvalidationListener(CacheManager cacheManager) {
        return new RedisCacheInvalidationListener(cacheManager);
    }

    /**
     * 注册 Redis Pub/Sub 监听容器。
     *
     * <p>Spring Data Redis 的监听容器会订阅配置中的失效通道，并把收到的消息体转成 UTF-8
     * 字符串交给 {@link RedisCacheInvalidationListener}。这里没有强依赖 Redis 连接工厂，
     * 只有在业务项目已经配置 Redis 时才会创建该容器。</p>
     */
    @Bean("letoolCacheInvalidationListenerContainer")
    @ConditionalOnClass(RedisMessageListenerContainer.class)
    @ConditionalOnBean({RedisCacheInvalidationListener.class, RedisConnectionFactory.class})
    @ConditionalOnMissingBean(
            value = RedisMessageListenerContainer.class,
            name = "letoolCacheInvalidationListenerContainer")
    public RedisMessageListenerContainer letoolCacheInvalidationListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisCacheInvalidationListener listener,
            CacheProperties properties) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener((message, pattern) ->
                listener.onMessage(new String(message.getBody(), StandardCharsets.UTF_8)),
                new ChannelTopic(properties.getInvalidation().getChannel()));
        return container;
    }

    /**
     * 注册 L2 降级后的恢复探测任务。
     *
     * <p>当 Redis 异常时缓存实例会进入 L2 降级状态，避免每次访问都阻塞在 Redis 错误上。
     * 该任务会定期尝试探测 Redis 是否恢复，一旦恢复成功，缓存实例会重新启用 L2。</p>
     */
    @Bean
    @ConditionalOnBean(CacheManager.class)
    @ConditionalOnMissingBean(CacheRecoveryScheduler.class)
    @ConditionalOnProperty(prefix = "letool.cache.degradation", name = "recovery-enabled", havingValue = "true", matchIfMissing = true)
    public CacheRecoveryScheduler cacheRecoveryScheduler(CacheManager cacheManager, CacheProperties properties) {
        return new CacheRecoveryScheduler(cacheManager, properties.getDegradation().getRecoveryInterval());
    }

    /**
     * 注册缓存注解切面。
     *
     * <p>开启后，业务方法可以通过 {@code @MultiLevelCacheable}、{@code @MultiLevelCachePut}
     * 和 {@code @MultiLevelCacheEvict} 直接使用二级缓存能力。</p>
     */
    @Bean
    @ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
    @ConditionalOnBean(CacheManager.class)
    @ConditionalOnMissingBean(CacheAspect.class)
    @ConditionalOnProperty(prefix = "letool.cache.annotation", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CacheAspect cacheAspect(
            CacheManager cacheManager,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            CacheMutationCoordinator mutationCoordinator) {
        return new CacheAspect(cacheManager, mutationCoordinator);
    }

    /**
     * 注册缓存监控对象。
     *
     * <p>监控器用于对外暴露当前缓存实例、命中率、L1/L2 状态等运行数据，
     * 方便后续接入 actuator、日志巡检或业务自定义监控。</p>
     */
    @Bean
    @ConditionalOnBean(CacheManager.class)
    @ConditionalOnMissingBean(CacheMonitor.class)
    @ConditionalOnProperty(prefix = "letool.cache.monitoring", name = "enabled", havingValue = "true", matchIfMissing = true)
    public CacheMonitor cacheMonitor(
            CacheManager cacheManager,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            CacheInvalidationRecovery invalidationRecovery) {
        return new CacheMonitor(cacheManager, invalidationRecovery);
    }

    /**
     * 根据配置文件预注册缓存实例。
     *
     * <p>如果业务项目希望在启动时就固定创建若干缓存，可以在
     * {@code letool.cache.instances} 下声明名称、L1 容量、TTL、是否强一致等参数。
     * 这里会把每个实例配置转换为 {@link CacheConfig}，再交给 {@link CacheManager} 管理。</p>
     */
    @Bean
    @ConditionalOnMissingBean(name = "cacheInstancesInitializer")
    public Object cacheInstancesInitializer(CacheManager cacheManager, CacheProperties properties) {
        for (CacheProperties.InstanceConfig ic : properties.getInstances()) {
            CacheConfig<Object, Object> config = CacheConfig.builder(ic.getName())
                    .l1Enabled(ic.isL1Enabled())
                    .l1MaxSize(ic.getL1MaxSize())
                    .redisBatchSize(ic.getRedisBatchSize())
                    .l1Ttl(ic.getL1Ttl())
                    .l2Ttl(ic.getL2Ttl())
                    .versionMetadataRetention(ic.getVersionMetadataRetention() == null
                            ? properties.getConsistency().getVersionMetadataRetention()
                            : ic.getVersionMetadataRetention())
                    .fenceTtl(properties.getConsistency().getFenceTtl())
                    .recoveryInterval(properties.getConsistency().getRecoveryInterval())
                    .l2Enabled(ic.isL2Enabled())
                    .consistencyMode(ic.getConsistencyMode() == null
                            ? properties.getConsistency().getMode()
                            : ic.getConsistencyMode())
                    .readValidation(properties.isStrongConsistency() && ic.isStrongConsistency()
                            ? (ic.getReadValidation() == null
                            ? properties.getConsistency().getReadValidation()
                            : ic.getReadValidation())
                            : io.github.leylaragg.letool.cache.consistency.CacheReadValidation.NONE)
                    .writePolicy(ic.getWritePolicy() == null
                            ? properties.getConsistency().getWritePolicy()
                            : ic.getWritePolicy())
                    .readFailurePolicy(ic.getReadFailurePolicy() == null
                            ? properties.getConsistency().getReadFailurePolicy()
                            : ic.getReadFailurePolicy())
                    .nullValueCache(ic.isNullValueCache())
                    .nullValueTtl(ic.getNullValueTtl())
                    .redisKeyPrefix(properties.getRedisPrefix());
            MultiLevelCache<Object, Object> cache = cacheManager.getOrCreate(config);
            log.info("Cache instance [{}] registered: L1(max={}, ttl={}) L2(ttl={}) nullCache={}",
                    ic.getName(), ic.getL1MaxSize(), ic.getL1Ttl(), ic.getL2Ttl(), ic.isNullValueCache());
        }
        return new Object();
    }

    /**
     * 隔离 JDBC Outbox 的可选装配，未引入 spring-jdbc 的项目只会加载 L1 缓存配置。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.jdbc.core.JdbcTemplate")
    static class JdbcOutboxConfiguration {

        /**
         * 使用业务项目中唯一的 JdbcTemplate 注册默认 Outbox 仓储。
         *
         * @param jdbcTemplateProvider 业务 JDBC 操作入口候选
         * @param properties 缓存配置
         * @return 缓存失效事件仓储
         */
        @Bean
        @Conditional({ExactSingleJdbcTemplateCondition.class, DurableCacheConfiguredCondition.class})
        @ConditionalOnMissingBean(CacheInvalidationEventStore.class)
        CacheInvalidationEventStore cacheInvalidationEventStore(ObjectProvider<JdbcTemplate> jdbcTemplateProvider, CacheProperties properties) {
            // 条件已经确认候选唯一，只在创建 Outbox 仓储时取出实际实例。
            JdbcTemplate jdbcTemplate = jdbcTemplateProvider.getObject();
            return new JdbcCacheInvalidationEventStore(jdbcTemplate, properties.getConsistency().getOutboxTable());
        }
    }
}
