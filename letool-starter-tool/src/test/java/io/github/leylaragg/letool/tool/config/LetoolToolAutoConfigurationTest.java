package io.github.leylaragg.letool.tool.config;

import io.github.leylaragg.letool.tool.http.HttpTemplate;
import io.github.leylaragg.letool.tool.redis.RedisUtil;
import io.github.leylaragg.letool.tool.redis.RedisMessageQueueUtil;
import io.github.leylaragg.letool.tool.redis.FastJson2JsonRedisSerializer;
import io.github.leylaragg.letool.tool.json.Fastjson2JsonCodec;
import io.github.leylaragg.letool.tool.json.JsonCodec;
import io.github.leylaragg.letool.tool.spring.SpringUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 验证 {@link LetoolToolAutoConfiguration} 的 Spring Boot Starter 契约。
 *
 * <p>工具 Starter 是轻量级基础模块。Spring 和 Redis 工具必须通过明确的
 * 适配器 Bean 注册，不能依赖宽泛组件扫描偶然生效。</p>
 */
class LetoolToolAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    LetoolRedisTemplateAutoConfiguration.class,
                    LetoolToolAutoConfiguration.class))
            .withPropertyValues("spring.main.allow-bean-definition-overriding=false");

    /**
     * 不存在 {@link RedisTemplate} Bean 时不应创建 Redis 工具。
     */
    @Test
    void shouldStartWithoutRedisTemplateAndNotCreateRedisUtil() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SpringUtil.class);
            assertThat(context).hasSingleBean(JsonCodec.class);
            assertThat(context.getBean(JsonCodec.class)).isInstanceOf(Fastjson2JsonCodec.class);
            assertThat(context).doesNotHaveBean(RedisUtil.class);
            assertThat(context).doesNotHaveBean(RedisMessageQueueUtil.class);
        });
    }

    /**
     * 用户自定义编解码器应替换默认实现，且不引入全局可变状态。
     */
    @Test
    void shouldBackOffWhenUserProvidesJsonCodec() {
        contextRunner
                .withUserConfiguration(UserJsonCodecConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JsonCodec.class);
                    assertThat(context.getBean(JsonCodec.class))
                            .isSameAs(context.getBean("userJsonCodec"));
                });
    }

    /**
     * 仅在对象 Redis 基础设施存在时创建 Redis 工具。
     */
    @Test
    void shouldCreateRedisUtilWhenRedisTemplateExists() {
        contextRunner
                .withUserConfiguration(RedisTemplateConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RedisTemplate.class);
                    assertThat(context).hasSingleBean(RedisUtil.class);
                    assertThat(context).hasSingleBean(RedisMessageQueueUtil.class);
                    assertThat(context.getBean(RedisUtil.class).getTemplate())
                            .isSameAs(context.getBean(RedisTemplate.class));
                    assertThat(context.getBean(RedisMessageQueueUtil.class).getTemplate())
                            .isSameAs(context.getBean(RedisTemplate.class));
                });
    }

    /**
     * Redis 连接基础设施存在但应用未定义 {@code redisTemplate} 时，
     * Starter 应提供 JSON 对象模板。
     */
    @Test
    void shouldCreateDefaultRedisTemplateWhenConnectionFactoryExists() {
        contextRunner
                .withUserConfiguration(RedisConnectionFactoryConfiguration.class)
                .withPropertyValues("letool.tool.redis.auto-type-accept-prefixes[0]=io.github.leylaragg.letool.tool.config")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RedisConnectionFactory.class);
                    assertThat(context).hasSingleBean(RedisTemplate.class);
                    assertThat(context).hasSingleBean(RedisUtil.class);
                    assertThat(context).hasSingleBean(RedisMessageQueueUtil.class);

                    RedisTemplate<?, ?> redisTemplate = context.getBean(RedisTemplate.class);
                    assertThat(redisTemplate.getConnectionFactory())
                            .isSameAs(context.getBean(RedisConnectionFactory.class));
                    assertThat(redisTemplate.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);
                    assertThat(redisTemplate.getHashKeySerializer()).isInstanceOf(StringRedisSerializer.class);
                    assertThat(redisTemplate.getValueSerializer()).isInstanceOf(FastJson2JsonRedisSerializer.class);
                    assertThat(redisTemplate.getHashValueSerializer()).isInstanceOf(FastJson2JsonRedisSerializer.class);
                    assertThat(context.getBean(RedisUtil.class).getTemplate()).isSameAs(redisTemplate);
                    assertThat(context.getBean(RedisMessageQueueUtil.class).getTemplate()).isSameAs(redisTemplate);

                    FastJson2JsonRedisSerializer<Object> serializer =
                            (FastJson2JsonRedisSerializer<Object>) redisTemplate.getValueSerializer();
                    RedisValue value = new RedisValue();
                    value.setName("configured");
                    Object actual = serializer.deserialize(serializer.serialize(value));
                    assertThat(actual).isInstanceOf(RedisValue.class);
                    assertThat(((RedisValue) actual).getName()).isEqualTo("configured");
                });
    }

    /**
     * 业务提前提供连接工厂时，Letool 应在 Boot 默认模板之前保留原有 Fastjson2 对象模板。
     * 适配器可以延后注册，但不能因此把既有缓存数据的序列化协议切换为 JDK。
     */
    @Test
    void shouldPreserveFastJsonTemplateWhenUserFactoryExistsBeforeBootRedisAutoConfiguration() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        LetoolRedisTemplateAutoConfiguration.class,
                        RedisAutoConfiguration.class,
                        LetoolToolAutoConfiguration.class))
                .withUserConfiguration(RedisConnectionFactoryConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    RedisTemplate<?, ?> redisTemplate = context.getBean(
                            "redisTemplate", RedisTemplate.class);
                    assertThat(redisTemplate.getValueSerializer())
                            .isInstanceOf(FastJson2JsonRedisSerializer.class);
                    assertThat(context.getBean(RedisUtil.class).getTemplate())
                            .isSameAs(redisTemplate);
                });
    }

    /**
     * 仅存在 StringRedisTemplate 时不应激活 RedisUtil，因为 RedisUtil 应使用
     * 应用的对象 RedisTemplate 及其序列化器。
     */
    @Test
    void shouldNotCreateRedisUtilWhenOnlyStringRedisTemplateExists() {
        contextRunner
                .withUserConfiguration(StringRedisTemplateConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(StringRedisTemplate.class);
                    assertThat(context).doesNotHaveBean(RedisUtil.class);
                    assertThat(context).doesNotHaveBean(RedisMessageQueueUtil.class);
                });
    }

    /**
     * 用户自定义适配器 Bean 应完整替换 Starter 默认实现。
     */
    @Test
    void shouldBackOffWhenUserProvidesToolAdapterBeans() {
        contextRunner
                .withUserConfiguration(UserToolAdapterConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SpringUtil.class);
                    assertThat(context).hasSingleBean(RedisUtil.class);
                    assertThat(context.getBean(SpringUtil.class))
                            .isSameAs(context.getBean("userSpringUtil"));
                    assertThat(context.getBean(RedisUtil.class))
                            .isSameAs(context.getBean("userRedisUtil"));
                });
    }

    /**
     * 验证工具 Starter 默认提供可直接注入的 HTTP 请求模板。
     */
    @Test
    void shouldProvideDefaultHttpTemplate() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(HttpTemplate.class);
        });
    }

    /**
     * 验证应用提供独立 HTTP 模板时默认模板完整退让。
     */
    @Test
    void shouldBackOffWhenUserProvidesHttpTemplate() {
        contextRunner
                .withUserConfiguration(UserHttpTemplateConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(HttpTemplate.class);
                    assertThat(context.getBean(HttpTemplate.class))
                            .isSameAs(context.getBean("userHttpTemplate"));
                });
    }

    /**
     * 用于激活 {@link RedisUtil} 的最小 Redis 基础设施。
     */
    @Configuration(proxyBeanMethods = false)
    static class RedisTemplateConfiguration {

        @Bean
        RedisTemplate<String, Object> redisTemplate() {
            return mock(RedisTemplate.class);
        }
    }

    /**
     * 用于激活默认模板的最小 Redis 连接基础设施。
     */
    @Configuration(proxyBeanMethods = false)
    static class RedisConnectionFactoryConfiguration {

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return mock(RedisConnectionFactory.class);
        }
    }

    /**
     * 模拟仅定义 StringRedisTemplate 的应用。
     */
    @Configuration(proxyBeanMethods = false)
    static class StringRedisTemplateConfiguration {

        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }
    }

    /**
     * 模拟使用其他 JSON 实现的应用。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserJsonCodecConfiguration {

        @Bean
        JsonCodec userJsonCodec() {
            return mock(JsonCodec.class);
        }
    }

    /**
     * 模拟应用自行提供 HTTP 模板。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserHttpTemplateConfiguration {

        /**
         * 创建应用自定义 HTTP 模板。
         *
         * @return 用户管理的 HTTP 模板
         */
        @Bean
        HttpTemplate userHttpTemplate() {
            return new HttpTemplate();
        }
    }

    /**
     * 模拟自行提供工具适配器 Bean 的应用。
     */
    @Configuration(proxyBeanMethods = false)
    static class UserToolAdapterConfiguration {

        @Bean({"springUtil", "userSpringUtil"})
        SpringUtil springUtil() {
            return new SpringUtil();
        }

        @Bean
        RedisTemplate<String, Object> redisTemplate() {
            return mock(RedisTemplate.class);
        }

        @Bean({"redisUtil", "userRedisUtil"})
        RedisUtil redisUtil(RedisTemplate<String, Object> redisTemplate) {
            return new RedisUtil(redisTemplate);
        }
    }

    public static class RedisValue {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
