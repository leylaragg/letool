package com.github.leyland.letool.tool.config;

import com.github.leyland.letool.tool.redis.RedisUtil;
import com.github.leyland.letool.tool.spring.SpringUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

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
     * Redis helper should stay absent when no {@link StringRedisTemplate} bean exists.
     */
    @Test
    void shouldStartWithoutRedisTemplateAndNotCreateRedisUtil() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SpringUtil.class);
            assertThat(context).doesNotHaveBean(RedisUtil.class);
        });
    }

    /**
     * Redis helper should be created only when Redis infrastructure is present.
     */
    @Test
    void shouldCreateRedisUtilWhenStringRedisTemplateExists() {
        contextRunner
                .withUserConfiguration(RedisTemplateConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(StringRedisTemplate.class);
                    assertThat(context).hasSingleBean(RedisUtil.class);
                    assertThat(context.getBean(RedisUtil.class).getTemplate())
                            .isSameAs(context.getBean(StringRedisTemplate.class));
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
        StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }

        @Bean({"redisUtil", "userRedisUtil"})
        RedisUtil redisUtil(StringRedisTemplate stringRedisTemplate) {
            return new RedisUtil(stringRedisTemplate);
        }
    }
}
