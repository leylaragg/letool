package com.github.leyland.letool.cache.config;

import com.github.leyland.letool.cache.aspect.CacheAspect;
import com.github.leyland.letool.cache.core.CacheManager;
import com.github.leyland.letool.cache.serializer.CacheSerializer;
import com.github.leyland.letool.cache.serializer.JacksonCacheSerializer;
import com.github.leyland.letool.cache.support.CacheMonitor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
     * 模拟业务项目自行接管缓存基础设施的配置。
     */
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
