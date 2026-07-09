package com.github.leyland.letool.tool.config;

import com.github.leyland.letool.tool.redis.RedisUtil;
import com.github.leyland.letool.tool.redis.RedisMessageQueueUtil;
import com.github.leyland.letool.tool.redis.FastJson2JsonRedisSerializer;
import com.github.leyland.letool.tool.spring.SpringUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
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
 * Tests the Spring Boot starter contract for {@link LetoolToolAutoConfiguration}.
 *
 * <p>The tool starter is the lightweight base module. Spring and Redis helpers
 * must be explicit adapter beans, not accidental results of broad component
 * scanning.</p>
 */
class LetoolToolAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LetoolToolAutoConfiguration.class))
            .withPropertyValues("spring.main.allow-bean-definition-overriding=false");

    /**
     * Redis helper should stay absent when no {@link RedisTemplate} bean exists.
     */
    @Test
    void shouldStartWithoutRedisTemplateAndNotCreateRedisUtil() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SpringUtil.class);
            assertThat(context).doesNotHaveBean(RedisUtil.class);
            assertThat(context).doesNotHaveBean(RedisMessageQueueUtil.class);
        });
    }

    /**
     * Redis helper should be created only when object Redis infrastructure is present.
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
     * When Redis connection infrastructure exists but the application does not
     * define redisTemplate, the starter should provide a JSON object template.
     */
    @Test
    void shouldCreateDefaultRedisTemplateWhenConnectionFactoryExists() {
        contextRunner
                .withUserConfiguration(RedisConnectionFactoryConfiguration.class)
                .withPropertyValues("letool.tool.redis.auto-type-accept-prefixes[0]=com.github.leyland.letool.tool.config")
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
     * StringRedisTemplate alone should not activate RedisUtil because RedisUtil is
     * intended to use the application's object RedisTemplate and its serializers.
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
     * User-defined adapter beans should replace starter defaults cleanly.
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
     * Minimal Redis infrastructure used to activate {@link RedisUtil}.
     */
    @Configuration(proxyBeanMethods = false)
    static class RedisTemplateConfiguration {

        @Bean
        RedisTemplate<String, Object> redisTemplate() {
            return mock(RedisTemplate.class);
        }
    }

    /**
     * Minimal Redis connection infrastructure used to activate the default template.
     */
    @Configuration(proxyBeanMethods = false)
    static class RedisConnectionFactoryConfiguration {

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return mock(RedisConnectionFactory.class);
        }
    }

    /**
     * Simulates applications that only define StringRedisTemplate.
     */
    @Configuration(proxyBeanMethods = false)
    static class StringRedisTemplateConfiguration {

        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }
    }

    /**
     * Simulates applications that own the tool adapter beans themselves.
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
