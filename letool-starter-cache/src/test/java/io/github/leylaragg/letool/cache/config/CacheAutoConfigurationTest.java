package io.github.leylaragg.letool.cache.config;

import io.github.leylaragg.letool.cache.aspect.CacheAspect;
import io.github.leylaragg.letool.cache.core.CacheConfig;
import io.github.leylaragg.letool.cache.core.CacheManager;
import io.github.leylaragg.letool.cache.core.MultiLevelCache;
import io.github.leylaragg.letool.cache.core.CacheReadFailurePolicy;
import io.github.leylaragg.letool.cache.core.CacheWriteFailurePolicy;
import io.github.leylaragg.letool.cache.core.RedisCacheInvalidationSubscriber;
import io.github.leylaragg.letool.cache.serializer.CacheSerializer;
import io.github.leylaragg.letool.cache.serializer.JacksonCacheSerializer;
import io.github.leylaragg.letool.cache.support.CacheMonitor;
import io.github.leylaragg.letool.redis.config.LetoolRedisAutoConfiguration;
import io.github.leylaragg.letool.redis.config.LetoolRedisTemplateAutoConfiguration;
import io.github.leylaragg.letool.tool.config.LetoolToolAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.SubscriptionListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import io.github.leylaragg.letool.cache.consistency.CacheInvalidationEventStore;
import io.github.leylaragg.letool.cache.consistency.CacheInvalidationRecovery;
import io.github.leylaragg.letool.cache.consistency.CacheInvalidationRecoveryScheduler;
import io.github.leylaragg.letool.cache.consistency.CacheMutationCoordinator;
import io.github.leylaragg.letool.redis.RedisUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CacheAutoConfiguration} 的自动装配契约测试。
 *
 * <p>重点覆盖业务项目自定义缓存基础设施 Bean 时，cache starter 是否正确退让。</p>
 */
class CacheAutoConfigurationTest {

    private static final String TEST_INVALIDATION_CHANNEL = "letool:test:invalidation";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CacheAutoConfiguration.class))
            .withPropertyValues("spring.main.allow-bean-definition-overriding=false");

    /**
     * 默认启用时应装配 L1 缓存所需的核心组件，并保持注解和监控能力可用。
     */
    @Test
    void shouldCreateDefaultCacheInfrastructureBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(CacheProperties.class);
            assertThat(context).hasSingleBean(CacheSerializer.class);
            assertThat(context).hasSingleBean(CacheManager.class);
            assertThat(context).hasSingleBean(CacheAspect.class);
            assertThat(context).hasSingleBean(CacheMonitor.class);
            assertThat(context).hasBean("cacheInstancesInitializer");
        });
    }

    /**
     * 总开关关闭时，缓存 starter 不应留下任何运行时基础设施 Bean。
     */
    @Test
    void shouldDisableCacheAutoConfiguration() {
        contextRunner
                .withPropertyValues("letool.cache.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(CacheProperties.class);
                    assertThat(context).doesNotHaveBean(CacheManager.class);
                    assertThat(context).doesNotHaveBean(CacheAspect.class);
                    assertThat(context).doesNotHaveBean(CacheMonitor.class);
                });
    }

    /**
     * 没有 Redis 相关 classpath 时，cache starter 仍应以 L1-only 模式启动。
     */
    @Test
    void shouldStartAsL1OnlyCacheWhenRedisClasspathIsMissing() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        LetoolRedisTemplateAutoConfiguration.class,
                        RedisAutoConfiguration.class,
                        LetoolToolAutoConfiguration.class,
                        CacheAutoConfiguration.class))
                .withClassLoader(new FilteredClassLoader("org.springframework.data.redis"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CacheManager.class);
                    assertThat(context).hasSingleBean(CacheMonitor.class);
                    assertThat(context).doesNotHaveBean(RedisUtil.class);
                    assertThat(context).doesNotHaveBean(
                            "letoolCacheInvalidationStringRedisTemplate");
                });
    }

    /**
     * 标准业务应用只引入 Redis Starter 时，连接工厂由 Spring Boot 自动配置提供。
     * Letool 必须在它之后注册 RedisUtil 和失效广播组件，不能因条件判断过早静默退化为 L1-only。
     */
    @Test
    void shouldCreateL2AndInvalidationInfrastructureAfterBootRedisAutoConfiguration() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        LetoolRedisTemplateAutoConfiguration.class,
                        RedisAutoConfiguration.class,
                        LetoolRedisAutoConfiguration.class,
                        LetoolToolAutoConfiguration.class,
                        CacheAutoConfiguration.class))
                .withUserConfiguration(ListenerContainerTakeoverOnlyConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RedisConnectionFactory.class);
                    assertThat(context).hasSingleBean(RedisUtil.class);
                    assertThat(context).doesNotHaveBean("letoolCacheInvalidationStringRedisTemplate");
                    assertThat(context).hasBean("letoolCacheInvalidationPublisher");
                    assertThat(context).hasSingleBean(RedisCacheInvalidationSubscriber.class);
                    RedisUtil businessRedisUtil = context.getBean(RedisUtil.class);
                    RedisUtil cacheRedisUtil = (RedisUtil) extractField(
                            context.getBean(CacheManager.class), "redisUtil");
                    assertThat(cacheRedisUtil).isNotSameAs(businessRedisUtil);
                    assertThat(cacheRedisUtil.getTemplate().getKeySerializer())
                            .isInstanceOf(StringRedisSerializer.class);
                    assertThat(cacheRedisUtil.getTemplate().getHashKeySerializer())
                            .isInstanceOf(StringRedisSerializer.class);
                    @SuppressWarnings("unchecked")
                    RedisSerializer<Object> cacheValueSerializer =
                            (RedisSerializer<Object>) cacheRedisUtil.getTemplate().getValueSerializer();
                    @SuppressWarnings("unchecked")
                    RedisSerializer<Object> businessValueSerializer =
                            (RedisSerializer<Object>) businessRedisUtil.getTemplate().getValueSerializer();
                    byte[] businessValue = businessValueSerializer.serialize("rule-value");
                    assertThat(cacheValueSerializer.deserialize(businessValue))
                            .isEqualTo("rule-value");
                    assertThat(cacheValueSerializer.deserialize(
                            "7".getBytes(StandardCharsets.UTF_8))).isEqualTo("7");
                    assertThat(businessRedisUtil.getTemplate().getKeySerializer())
                            .isNotInstanceOf(StringRedisSerializer.class);
                });
    }

    /** 已使用字符串 Key 的业务模板仍应保持不变，由私有视图兼容框架版本元数据。 */
    @Test
    void shouldKeepStringKeyBusinessRedisUtilUnchanged() {
        contextRunner
                .withUserConfiguration(StringKeyRedisUtilConfiguration.class)
                .withPropertyValues("letool.cache.invalidation.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    RedisUtil businessRedisUtil = context.getBean(RedisUtil.class);
                    RedisUtil cacheRedisUtil = (RedisUtil) extractField(
                            context.getBean(CacheManager.class), "redisUtil");
                    assertThat(cacheRedisUtil).isNotSameAs(businessRedisUtil);
                    assertThat(cacheRedisUtil.getTemplate().getValueSerializer())
                            .isInstanceOf(CacheRedisValueSerializer.class);
                    assertThat(businessRedisUtil.getTemplate().getValueSerializer())
                            .isNotInstanceOf(CacheRedisValueSerializer.class);
                });
    }

    /**
     * 失效模板通过 ObjectProvider 延迟取得唯一连接工厂，避免把可选 Redis 基础设施
     * 声明成无条件注入点，也避免存在多个无主候选时启动失败。
     */
    @Test
    void invalidationPublisherShouldResolveSingleConnectionFactoryLazily() {
        Method method = Arrays.stream(CacheAutoConfiguration.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName()
                        .equals("letoolCacheInvalidationPublisher"))
                .findFirst()
                .orElseThrow();

        assertThat(method.getParameterTypes()).contains(ObjectProvider.class);
        assertThat(method.getParameterTypes()).doesNotContain(RedisConnectionFactory.class);
        assertThat(method.getAnnotation(ConditionalOnSingleCandidate.class)).isNotNull();
    }

    /**
     * 默认监听容器同样应延迟取得唯一连接工厂，避免可选 Redis 基础设施形成直接注入警告。
     */
    @Test
    void invalidationSubscriberShouldResolveSingleConnectionFactoryLazily() {
        Method method = Arrays.stream(CacheAutoConfiguration.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName()
                        .equals("letoolCacheInvalidationSubscriber"))
                .findFirst()
                .orElseThrow();

        assertThat(method.getParameterTypes()).contains(ObjectProvider.class);
        assertThat(method.getParameterTypes()).doesNotContain(RedisConnectionFactory.class);
        assertThat(method.getAnnotation(ConditionalOnSingleCandidate.class)).isNotNull();
    }

    /**
     * Letool 协议模板只供失效发布器按名称使用，不能和 Boot 默认模板共同参与业务按类型注入。
     */
    @Test
    void shouldKeepBootStringRedisTemplateAsBusinessDefaultCandidate() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        LetoolRedisTemplateAutoConfiguration.class,
                        RedisAutoConfiguration.class,
                        LetoolToolAutoConfiguration.class,
                        CacheAutoConfiguration.class))
                .withUserConfiguration(
                        ListenerContainerTakeoverOnlyConfiguration.class,
                        StringRedisTemplateConsumerConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    StringRedisTemplateConsumer consumer =
                            context.getBean(StringRedisTemplateConsumer.class);
                    assertThat(consumer.redisTemplate()).isSameAs(
                            context.getBean("stringRedisTemplate"));
                    assertThat(context).doesNotHaveBean(
                            "letoolCacheInvalidationStringRedisTemplate");
                    assertThat(context).hasSingleBean(StringRedisTemplate.class);
                });
    }

    /**
     * JDBC Outbox 是 DURABLE 可选能力，普通缓存不能强制业务项目引入 spring-jdbc。
     */
    @Test
    void shouldStartWithoutSpringJdbcClasspath() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("org.springframework.jdbc"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CacheManager.class);
                    assertThat(context).doesNotHaveBean(io.github.leylaragg.letool.cache.consistency.CacheInvalidationEventStore.class);
                });
    }

    /**
     * JDBC 是 DURABLE 的可选能力，工厂方法不能把 JdbcTemplate 当成无条件依赖。
     */
    @Test
    void jdbcOutboxFactoryShouldResolveJdbcTemplateLazily() {
        Method factoryMethod = Arrays.stream(CacheAutoConfiguration.JdbcOutboxConfiguration.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("cacheInvalidationEventStore"))
                .findFirst()
                .orElseThrow();

        assertThat(factoryMethod.getParameterTypes()).contains(ObjectProvider.class);
        assertThat(factoryMethod.getParameterTypes()).doesNotContain(JdbcTemplate.class);
    }

    /**
     * 没有 AspectJ 时，注解切面不应加载，但编程式缓存 API 仍然可用。
     */
    @Test
    void shouldStartWithoutCacheAspectWhenAspectJClasspathIsMissing() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("org.aspectj"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CacheManager.class);
                    assertThat(context).doesNotHaveBean(CacheAspect.class);
                });
    }

    /**
     * 关闭监控时，只应移除 CacheMonitor，不影响核心缓存管理器。
     */
    @Test
    void shouldDisableCacheMonitoringOnly() {
        contextRunner
                .withPropertyValues("letool.cache.monitoring.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(CacheManager.class);
                    assertThat(context).doesNotHaveBean(CacheMonitor.class);
                });
    }

    /**
     * 验证用户提供序列化器、管理器、切面、监控器和初始化器时，自动配置不会创建重复 Bean。
     */
    @Test
    void shouldBackOffWhenUserProvidesCacheInfrastructureBeans() {
        contextRunner
                .withUserConfiguration(UserCacheConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(CacheSerializer.class);
                    assertThat(context).hasSingleBean(CacheManager.class);
                    assertThat(context).hasSingleBean(CacheAspect.class);
                    assertThat(context).hasSingleBean(CacheMonitor.class);
                    assertThat(context.getBean(CacheSerializer.class))
                            .isSameAs(context.getBean("cacheSerializer"));
                    assertThat(context.getBean(CacheManager.class))
                            .isSameAs(context.getBean("cacheManager"));
                    assertThat(context.getBean(CacheAspect.class))
                            .isSameAs(context.getBean("cacheAspect"));
                    assertThat(context.getBean(CacheMonitor.class))
                            .isSameAs(context.getBean("cacheMonitor"));
                    assertThat(context.getBean("cacheInstancesInitializer"))
                            .isSameAs(context.getBean("userCacheInstancesInitializer"));
                });
    }

    /**
     * Disabling annotation support should keep the programmatic cache API available.
     */
    @Test
    void shouldDisableCacheAspectWhenAnnotationSupportIsDisabled() {
        contextRunner
                .withPropertyValues("letool.cache.annotation.enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(CacheManager.class);
                    assertThat(context).hasSingleBean(CacheSerializer.class);
                    assertThat(context).doesNotHaveBean(CacheAspect.class);
                });
    }

    /**
     * 全局强一致开关关闭时，YAML 预注册的缓存实例也必须被约束为非强一致。
     *
     * <p>否则业务项目会以为配置了 {@code letool.cache.strong-consistency=false} 就能统一关闭
     * Redis 版本校验，但启动时预注册的实例仍然保持默认 true，造成配置语义和实际行为不一致。</p>
     */
    @Test
    void shouldApplyGlobalStrongConsistencySwitchToConfiguredInstances() {
        contextRunner
                .withPropertyValues(
                        "letool.cache.strong-consistency=false",
                        "letool.cache.instances[0].name=weak-consistency-cache")
                .run(context -> {
                    CacheManager cacheManager = context.getBean(CacheManager.class);
                    MultiLevelCache<Object, Object> cache = cacheManager.get("weak-consistency-cache");
                    CacheConfig<?, ?> config = extractConfig(cache);

                    assertThat(config.isStrongConsistency()).isFalse();
                });
    }

    /** 实例级读取失败策略应覆盖全局默认值并写入实际缓存配置。 */
    @Test
    void shouldApplyInstanceReadFailurePolicy() {
        contextRunner
                .withPropertyValues(
                        "letool.cache.consistency.read-failure-policy=EMPTY_ON_FAILURE",
                        "letool.cache.instances[0].name=strict-set-cache",
                        "letool.cache.instances[0].read-failure-policy=FAIL_CLOSED")
                .run(context -> {
                    CacheManager cacheManager = context.getBean(CacheManager.class);
                    MultiLevelCache<Object, Object> cache = cacheManager.get("strict-set-cache");
                    CacheConfig<?, ?> config = extractConfig(cache);

                    assertThat(config.getReadFailurePolicy())
                            .isEqualTo(CacheReadFailurePolicy.FAIL_CLOSED);
                });
    }

    /** 实例级写失败策略应覆盖全局默认值并写入实际缓存配置。 */
    @Test
    void shouldApplyInstanceWriteFailurePolicy() {
        contextRunner
                .withPropertyValues(
                        "letool.cache.consistency.write-failure-policy=BEST_EFFORT",
                        "letool.cache.instances[0].name=critical-cache",
                        "letool.cache.instances[0].write-failure-policy=FAIL_CLOSED")
                .run(context -> {
                    CacheManager cacheManager = context.getBean(CacheManager.class);
                    MultiLevelCache<Object, Object> cache = cacheManager.get("critical-cache");

                    assertThat(cache.getWriteFailurePolicy())
                            .isEqualTo(CacheWriteFailurePolicy.FAIL_CLOSED);
                });
    }

    /** 失效基础设施按各自所有权声明稳定 Bean 名和退让条件。 */
    @Test
    void invalidationInfrastructureShouldUseStableNamesAndOwnershipBackoff() {
        assertNamedTypedBackoff(
                "letoolCacheInvalidationPublisher",
                io.github.leylaragg.letool.cache.core.CacheInvalidationPublisher.class);
        assertNamedTypedBackoff(
                "letoolCacheInvalidationListener",
                io.github.leylaragg.letool.cache.core.RedisCacheInvalidationListener.class);
        assertNamedTypedBackoff(
                "letoolCacheInvalidationSubscriber",
                RedisCacheInvalidationSubscriber.class);
    }

    /** 业务只有一个监听容器时，Letool 私有订阅不应修改业务容器。 */
    @Test
    void shouldRegisterInvalidationListenerOnSingleBusinessContainer() {
        contextRunner
                .withUserConfiguration(BusinessRedisListenerConfiguration.class)
                .withPropertyValues(
                        "letool.cache.invalidation.channel=" + TEST_INVALIDATION_CHANNEL)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RedisMessageListenerContainer.class);
                    assertThat(context).hasSingleBean(RedisCacheInvalidationSubscriber.class);
                    assertThat(context.getBeansOfType(RedisMessageListenerContainer.class))
                            .containsOnlyKeys("businessRedisMessageListenerContainer");
                    RedisMessageListenerContainer businessContainer = context.getBean(
                            "businessRedisMessageListenerContainer",
                            RedisMessageListenerContainer.class);
                    BusinessRedisListenerConsumer consumer =
                            context.getBean(BusinessRedisListenerConsumer.class);
                    assertThat(consumer.listenerContainer()).isSameAs(businessContainer);
                    verify(businessContainer, never()).addMessageListener(
                            org.mockito.ArgumentMatchers.<MessageListener>any(),
                            org.mockito.ArgumentMatchers.<Topic>any());
                });
    }

    /** 没有业务容器时，Letool 只暴露自有订阅类型，不新增业务容器候选。 */
    @Test
    void shouldCreateDefaultListenerContainerWhenBusinessContainerIsMissing() {
        contextRunner
                .withUserConfiguration(LetoolOnlyRedisListenerConfiguration.class)
                .withPropertyValues(
                        "letool.cache.invalidation.channel=" + TEST_INVALIDATION_CHANNEL)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(RedisMessageListenerContainer.class);
                    assertThat(context).hasSingleBean(RedisCacheInvalidationSubscriber.class);
                });
    }

    /** 业务同名旧容器不再接管 Letool 订阅，私有订阅仍应存在。 */
    @Test
    void shouldBackOffWhenUserProvidesNamedInvalidationListenerContainer() {
        contextRunner
                .withUserConfiguration(NamedInvalidationListenerConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RedisMessageListenerContainer.class);
                    assertThat(context).hasSingleBean(RedisCacheInvalidationSubscriber.class);
                    assertThat(context.getBean("letoolCacheInvalidationListenerContainer"))
                            .isSameAs(context.getBean(RedisMessageListenerContainer.class));
                    RedisMessageListenerContainer container =
                            context.getBean(RedisMessageListenerContainer.class);
                    verify(container, never()).addMessageListener(
                            any(MessageListener.class),
                            any(Topic.class));
                });
    }

    /** Redis 在应用启动阶段不可用时，失效订阅应后台恢复，不能阻断业务 Context。 */
    @Test
    void shouldStartWhenRedisIsUnavailableDuringInvalidationSubscription() {
        contextRunner
                .withUserConfiguration(UnavailableRedisInfrastructureConfiguration.class)
                .withPropertyValues("letool.cache.degradation.recovery-interval=10ms")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("letoolCacheInvalidationSubscriber");
                });
    }

    /** 多个业务监听容器不能改变 Letool 私有失效订阅的装配结果。 */
    @Test
    void shouldCreateInvalidationSubscriberAlongsideMultipleBusinessContainers() {
        contextRunner
                .withUserConfiguration(MultipleBusinessRedisListenerConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(RedisMessageListenerContainer.class))
                            .containsOnlyKeys("firstBusinessListenerContainer", "secondBusinessListenerContainer");
                    assertThat(context).hasBean("letoolCacheInvalidationSubscriber");
                });
    }

    /**
     * DURABLE 模式缺少事务管理器、Redis 和事件仓储时必须启动失败，不能静默降级。
     */
    @Test
    void durableModeShouldFailFastWithoutRequiredInfrastructure() {
        contextRunner
                .withPropertyValues("letool.cache.consistency.mode=DURABLE")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("DURABLE");
                });
    }

    /**
     * 单个缓存实例选择 DURABLE 时同样必须执行基础设施校验。
     */
    @Test
    void durableInstanceShouldFailFastWithoutRequiredInfrastructure() {
        contextRunner
                .withPropertyValues(
                        "letool.cache.instances[0].name=permissions",
                        "letool.cache.instances[0].consistency-mode=DURABLE")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("DURABLE");
                });
    }

    @Test
    void transactionalModeShouldNotStartDurableRecoveryWorker() {
        contextRunner
                .withUserConfiguration(DurableInfrastructureConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(CacheInvalidationRecovery.class);
                    assertThat(context).doesNotHaveBean(CacheInvalidationRecoveryScheduler.class);
                });
    }

    @Test
    void durableModeShouldStartRecoveryWorkerWhenInfrastructureIsComplete() {
        contextRunner
                .withUserConfiguration(DurableInfrastructureConfiguration.class)
                .withPropertyValues("letool.cache.consistency.mode=DURABLE")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CacheInvalidationRecovery.class);
                    assertThat(context).hasSingleBean(CacheInvalidationRecoveryScheduler.class);
                });
    }

    @Test
    void multipleJdbcTemplatesShouldRequireExplicitEventStore() {
        contextRunner
                .withUserConfiguration(MultipleJdbcTemplatesConfiguration.class)
                .run(context -> assertThat(context).doesNotHaveBean(CacheInvalidationEventStore.class));
    }

    @Test
    void durableWithMultipleTransactionManagersShouldRequireExplicitCoordinator() {
        contextRunner
                .withUserConfiguration(MultipleTransactionManagersConfiguration.class)
                .withPropertyValues("letool.cache.consistency.mode=DURABLE")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("CacheMutationCoordinator");
                });
    }

    @Test
    void durableShouldNotUsePrimaryFromMultipleInfrastructureCandidates() {
        contextRunner
                .withUserConfiguration(PrimaryMultipleInfrastructureConfiguration.class)
                .withPropertyValues("letool.cache.consistency.mode=DURABLE")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("多数据源");
                });
    }

    private CacheConfig<?, ?> extractConfig(MultiLevelCache<?, ?> cache) {
        try {
            Field field = cache.getClass().getDeclaredField("config");
            field.setAccessible(true);
            return (CacheConfig<?, ?>) field.get(cache);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to inspect cache config", e);
        }
    }

    private void assertNamedTypedBackoff(String methodName, Class<?> beanType) {
        try {
            Method method = Arrays.stream(CacheAutoConfiguration.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst()
                    .orElseThrow();
            org.springframework.context.annotation.Bean bean =
                    method.getAnnotation(org.springframework.context.annotation.Bean.class);
            ConditionalOnMissingBean condition =
                    method.getAnnotation(ConditionalOnMissingBean.class);

            assertThat(Set.of(bean.value())).contains(methodName);
            assertThat(Set.of(condition.name())).contains(methodName);
            assertThat(Set.of(condition.value())).contains(beanType);
        } catch (RuntimeException exception) {
            throw exception;
        }
    }

    /**
     * 创建可完成监听容器订阅确认的 Redis 连接工厂替身。
     *
     * @return 不建立网络连接的 Redis 连接工厂
     */
    private static RedisConnectionFactory mockRedisConnectionFactory() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        when(connectionFactory.getConnection()).thenReturn(connection);
        doAnswer(invocation -> {
            MessageListener listener = invocation.getArgument(0);
            byte[] channel = invocation.getArgument(1);
            assertThat(listener).isInstanceOf(SubscriptionListener.class);
            SubscriptionListener subscriptionListener = (SubscriptionListener) listener;
            // 这里只模拟订阅确认以完成容器生命周期，不建立真实 Redis 网络连接。
            subscriptionListener.onChannelSubscribed(channel, 1);
            return null;
        }).when(connection).subscribe(any(MessageListener.class), any(byte[][].class));
        return connectionFactory;
    }

    /**
     * 读取自动配置对象持有的依赖，用于确认 CacheManager 没有静默丢失 Redis L2。
     *
     * @param target 被检查的对象
     * @param fieldName 字段名称
     * @return 字段当前值
     */
    private static Object extractField(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("读取自动配置依赖失败: " + fieldName, exception);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UserCacheConfiguration {

        @Bean
        CacheSerializer cacheSerializer() {
            return new JacksonCacheSerializer();
        }

        @Bean
        CacheManager cacheManager(CacheSerializer cacheSerializer) {
            return new CacheManager(null, cacheSerializer);
        }

        @Bean
        CacheAspect cacheAspect(CacheManager cacheManager) {
            return new CacheAspect(cacheManager);
        }

        @Bean
        CacheMonitor cacheMonitor(CacheManager cacheManager) {
            return new CacheMonitor(cacheManager);
        }

        @Bean({"cacheInstancesInitializer", "userCacheInstancesInitializer"})
        Object cacheInstancesInitializer() {
            return new Object();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DurableInfrastructureConfiguration {

        @Bean
        RedisUtil redisUtil() {
            return org.mockito.Mockito.mock(RedisUtil.class);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return org.mockito.Mockito.mock(PlatformTransactionManager.class);
        }

        @Bean
        CacheInvalidationEventStore cacheInvalidationEventStore() {
            return org.mockito.Mockito.mock(CacheInvalidationEventStore.class);
        }
    }

    /** 提供已符合缓存键空间契约的业务 RedisUtil。 */
    @Configuration(proxyBeanMethods = false)
    static class StringKeyRedisUtilConfiguration {

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return mock(RedisConnectionFactory.class);
        }

        @Bean
        RedisUtil redisUtil(RedisConnectionFactory connectionFactory) {
            RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
            redisTemplate.setConnectionFactory(connectionFactory);
            redisTemplate.setKeySerializer(StringRedisSerializer.UTF_8);
            redisTemplate.setHashKeySerializer(StringRedisSerializer.UTF_8);
            redisTemplate.afterPropertiesSet();
            return new RedisUtil(redisTemplate);
        }
    }

    /** 提供普通业务 Redis 监听容器，验证 Letool 复用它且不增加同类型 Bean。 */
    @Configuration(proxyBeanMethods = false)
    static class BusinessRedisListenerConfiguration {

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return mockRedisConnectionFactory();
        }

        @Bean
        RedisMessageListenerContainer businessRedisMessageListenerContainer(
                RedisConnectionFactory connectionFactory) {
            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(connectionFactory);
            return spy(container);
        }

        /**
         * 创建按类型依赖 Redis 监听容器的业务消费者。
         *
         * @param listenerContainer 业务默认监听容器
         * @return 业务监听消费者
         */
        @Bean
        BusinessRedisListenerConsumer businessRedisListenerConsumer(
                RedisMessageListenerContainer listenerContainer) {
            return new BusinessRedisListenerConsumer(listenerContainer);
        }
    }

    /** 只提供 Redis 连接，让自动配置创建默认的 Letool 监听容器。 */
    @Configuration(proxyBeanMethods = false)
    static class LetoolOnlyRedisListenerConfiguration {

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return mockRedisConnectionFactory();
        }

    }

    /** 保存业务按类型注入到的 Redis 监听容器。 */
    record BusinessRedisListenerConsumer(RedisMessageListenerContainer listenerContainer) {
    }

    /** 提供同名 Letool 监听容器，验证自动配置按所有权退让。 */
    @Configuration(proxyBeanMethods = false)
    static class NamedInvalidationListenerConfiguration {

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return mockRedisConnectionFactory();
        }

        @Bean("letoolCacheInvalidationListenerContainer")
        RedisMessageListenerContainer letoolCacheInvalidationListenerContainer(
                RedisConnectionFactory connectionFactory) {
            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(connectionFactory);
            return spy(container);
        }
    }

    /** 提供确定失败的连接工厂，复现 Redis 暂时不可用的启动边界。 */
    @Configuration(proxyBeanMethods = false)
    static class UnavailableRedisInfrastructureConfiguration {

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
            when(connectionFactory.getConnection())
                    .thenThrow(new IllegalStateException("redis unavailable"));
            return connectionFactory;
        }
    }

    /** 模拟业务自行维护两个互不相关的 Redis 监听容器。 */
    @Configuration(proxyBeanMethods = false)
    static class MultipleBusinessRedisListenerConfiguration {

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return mockRedisConnectionFactory();
        }

        @Bean
        RedisMessageListenerContainer firstBusinessListenerContainer() {
            return mock(RedisMessageListenerContainer.class);
        }

        @Bean
        RedisMessageListenerContainer secondBusinessListenerContainer() {
            return mock(RedisMessageListenerContainer.class);
        }
    }

    /** 只接管监听容器，连接工厂仍由 Spring Boot Redis 自动配置创建。 */
    @Configuration(proxyBeanMethods = false)
    static class ListenerContainerTakeoverOnlyConfiguration {

        @Bean("letoolCacheInvalidationListenerContainer")
        RedisMessageListenerContainer letoolCacheInvalidationListenerContainer() {
            return mock(RedisMessageListenerContainer.class);
        }
    }

    /** 模拟业务代码按类型注入 Spring Boot 默认 StringRedisTemplate。 */
    @Configuration(proxyBeanMethods = false)
    static class StringRedisTemplateConsumerConfiguration {

        @Bean
        StringRedisTemplateConsumer stringRedisTemplateConsumer(
                StringRedisTemplate redisTemplate) {
            return new StringRedisTemplateConsumer(redisTemplate);
        }
    }

    /** 保存业务按类型取得的字符串 Redis 模板。 */
    record StringRedisTemplateConsumer(StringRedisTemplate redisTemplate) {
    }

    @Configuration(proxyBeanMethods = false)
    static class MultipleJdbcTemplatesConfiguration {

        @Bean
        JdbcTemplate firstJdbcTemplate() {
            return org.mockito.Mockito.mock(JdbcTemplate.class);
        }

        @Bean
        JdbcTemplate secondJdbcTemplate() {
            return org.mockito.Mockito.mock(JdbcTemplate.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MultipleTransactionManagersConfiguration {

        @Bean
        RedisUtil redisUtil() {
            return org.mockito.Mockito.mock(RedisUtil.class);
        }

        @Bean
        CacheInvalidationEventStore cacheInvalidationEventStore() {
            return org.mockito.Mockito.mock(CacheInvalidationEventStore.class);
        }

        @Bean
        PlatformTransactionManager firstTransactionManager() {
            return org.mockito.Mockito.mock(PlatformTransactionManager.class);
        }

        @Bean
        PlatformTransactionManager secondTransactionManager() {
            return org.mockito.Mockito.mock(PlatformTransactionManager.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class PrimaryMultipleInfrastructureConfiguration {

        @Bean
        RedisUtil redisUtil() {
            return org.mockito.Mockito.mock(RedisUtil.class);
        }

        @Bean
        @Primary
        PlatformTransactionManager primaryTransactionManager() {
            return org.mockito.Mockito.mock(PlatformTransactionManager.class);
        }

        @Bean
        PlatformTransactionManager secondaryTransactionManager() {
            return org.mockito.Mockito.mock(PlatformTransactionManager.class);
        }

        @Bean
        @Primary
        JdbcTemplate primaryJdbcTemplate() {
            return org.mockito.Mockito.mock(JdbcTemplate.class);
        }

        @Bean
        JdbcTemplate secondaryJdbcTemplate() {
            return org.mockito.Mockito.mock(JdbcTemplate.class);
        }
    }
}
