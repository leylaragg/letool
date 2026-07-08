package com.github.leyland.letool.cache.config;

import com.github.leyland.letool.cache.aspect.CacheAspect;
import com.github.leyland.letool.cache.core.CacheConfig;
import com.github.leyland.letool.cache.core.CacheManager;
import com.github.leyland.letool.cache.core.MultiLevelCache;
import com.github.leyland.letool.cache.serializer.CacheSerializer;
import com.github.leyland.letool.cache.serializer.JacksonCacheSerializer;
import com.github.leyland.letool.cache.support.CacheMonitor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CacheAutoConfiguration} 的自动装配契约测试。
 *
 * <p>重点覆盖业务项目自定义缓存基础设施 Bean 时，cache starter 是否正确退让。</p>
 */
class CacheAutoConfigurationTest {

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
        contextRunner
                .withClassLoader(new FilteredClassLoader("org.springframework.data.redis"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CacheManager.class);
                    assertThat(context).hasSingleBean(CacheMonitor.class);
                });
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

    private CacheConfig<?, ?> extractConfig(MultiLevelCache<?, ?> cache) {
        try {
            Field field = cache.getClass().getDeclaredField("config");
            field.setAccessible(true);
            return (CacheConfig<?, ?>) field.get(cache);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to inspect cache config", e);
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
}
