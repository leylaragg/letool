package io.github.leylaragg.letool.redis.config;

import io.github.leylaragg.letool.redis.RedisUtil;
import io.github.leylaragg.letool.redis.queue.RedisMessageQueueUtil;
import io.github.leylaragg.letool.redis.serializer.FastJson2JsonRedisSerializer;
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
 * 验证 Redis Starter 对对象模板和业务门面的条件装配契约。
 */
class LetoolRedisAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    LetoolRedisTemplateAutoConfiguration.class,
                    LetoolRedisAutoConfiguration.class));

    /** 只有字符串模板时不能创建会读写对象的 Redis 门面。 */
    @Test
    void shouldNotCreateFacadeFromStringTemplateOnly() {
        contextRunner.withUserConfiguration(StringTemplateConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(RedisUtil.class);
                    assertThat(context).doesNotHaveBean(RedisMessageQueueUtil.class);
                });
    }

    /** 业务提供对象模板时，门面必须复用同一个模板及其序列化协议。 */
    @Test
    void shouldCreateFacadeFromNamedObjectTemplate() {
        contextRunner.withUserConfiguration(ObjectTemplateConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    RedisTemplate<?, ?> template = context.getBean("redisTemplate", RedisTemplate.class);
                    assertThat(context).hasSingleBean(RedisUtil.class);
                    assertThat(context).hasSingleBean(RedisMessageQueueUtil.class);
                    assertThat(context.getBean(RedisUtil.class).getTemplate()).isSameAs(template);
                    assertThat(context.getBean(RedisMessageQueueUtil.class).getTemplate()).isSameAs(template);
                });
    }

    /** 唯一连接工厂存在时应在 Boot 默认配置之前保留 Letool 的对象序列化协议。 */
    @Test
    void shouldCreateCompatibleObjectTemplateFromSingleConnectionFactory() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        LetoolRedisTemplateAutoConfiguration.class,
                        RedisAutoConfiguration.class,
                        LetoolRedisAutoConfiguration.class))
                .withUserConfiguration(ConnectionFactoryConfiguration.class)
                .withPropertyValues(
                        "letool.redis.serialization.auto-type-accept-prefixes[0]=io.github.leylaragg.letool.redis.config")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    RedisTemplate<?, ?> template = context.getBean("redisTemplate", RedisTemplate.class);
                    assertThat(template.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);
                    assertThat(template.getHashKeySerializer()).isInstanceOf(StringRedisSerializer.class);
                    assertThat(template.getValueSerializer()).isInstanceOf(FastJson2JsonRedisSerializer.class);
                    assertThat(context.getBean(RedisUtil.class).getTemplate()).isSameAs(template);
                });
    }

    /** 用户声明的 Redis 门面必须使默认 Bean 完整退让。 */
    @Test
    void shouldBackOffForUserFacade() {
        contextRunner.withUserConfiguration(UserFacadeConfiguration.class)
                .run(context -> assertThat(context.getBean(RedisUtil.class))
                        .isSameAs(context.getBean("userRedisUtil")));
    }

    @Configuration(proxyBeanMethods = false)
    static class StringTemplateConfiguration {
        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ObjectTemplateConfiguration {
        @Bean("redisTemplate")
        RedisTemplate<String, Object> redisTemplate() {
            return mock(RedisTemplate.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ConnectionFactoryConfiguration {
        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return mock(RedisConnectionFactory.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UserFacadeConfiguration {
        @Bean("redisTemplate")
        RedisTemplate<String, Object> redisTemplate() {
            return mock(RedisTemplate.class);
        }

        @Bean({"redisUtil", "userRedisUtil"})
        RedisUtil redisUtil(RedisTemplate<String, Object> redisTemplate) {
            return new RedisUtil(redisTemplate);
        }
    }
}
