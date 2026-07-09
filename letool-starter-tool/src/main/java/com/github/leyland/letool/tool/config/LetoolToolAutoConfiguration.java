package com.github.leyland.letool.tool.config;

import com.github.leyland.letool.tool.redis.FastJson2JsonRedisSerializer;
import com.github.leyland.letool.tool.redis.RedisMessageQueueUtil;
import com.github.leyland.letool.tool.redis.RedisUtil;
import com.github.leyland.letool.tool.spring.SpringUtil;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Auto-configuration for the base tool starter.
 *
 * <p>This module is the lightweight toolkit foundation. It registers only the
 * Spring adapter beans that are useful by default and keeps optional adapters,
 * such as Redis, behind explicit classpath and bean conditions.</p>
 */
@AutoConfiguration
@AutoConfigureBefore(RedisAutoConfiguration.class)
@EnableConfigurationProperties(LetoolToolProperties.class)
public class LetoolToolAutoConfiguration {

    /**
     * Registers the Spring application-context helper unless the application owns it.
     *
     * @return Spring application-context helper.
     */
    @Bean
    @ConditionalOnMissingBean(SpringUtil.class)
    public SpringUtil springUtil() {
        return new SpringUtil();
    }

    /**
     * Redis-specific adapter configuration.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RedisTemplate.class)
    static class RedisToolConfiguration {

        /**
         * Provides a default object RedisTemplate when the application has Redis
         * connection infrastructure but has not defined its own redisTemplate.
         *
         * @param connectionFactory Redis connection factory.
         * @return RedisTemplate with String keys and Fastjson2 JSON values.
         */
        @Bean("redisTemplate")
        @ConditionalOnBean(RedisConnectionFactory.class)
        @ConditionalOnMissingBean(name = "redisTemplate")
        public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory,
                                                           LetoolToolProperties properties) {
            RedisTemplate<String, Object> template = new RedisTemplate<>();
            template.setConnectionFactory(connectionFactory);

            StringRedisSerializer keySerializer = new StringRedisSerializer();
            template.setKeySerializer(keySerializer);
            template.setHashKeySerializer(keySerializer);

            FastJson2JsonRedisSerializer<Object> valueSerializer =
                    new FastJson2JsonRedisSerializer<>(
                            Object.class,
                            properties.getRedis().getAutoTypeAcceptPrefixes().toArray(String[]::new));
            template.setValueSerializer(valueSerializer);
            template.setHashValueSerializer(valueSerializer);

            template.afterPropertiesSet();
            return template;
        }

        /**
         * Registers Redis helper only when application Redis infrastructure exists.
         *
         * @param redisTemplate Spring Redis object template.
         * @return Redis helper wrapper.
         */
        @Bean
        @ConditionalOnBean(name = "redisTemplate")
        @ConditionalOnMissingBean(RedisUtil.class)
        public RedisUtil redisUtil(RedisTemplate<String, Object> redisTemplate) {
            return new RedisUtil(redisTemplate);
        }

        /**
         * Registers Redis message queue helper only when application Redis infrastructure exists.
         *
         * @param redisTemplate Spring Redis object template.
         * @return Redis message queue helper wrapper.
         */
        @Bean
        @ConditionalOnBean(name = "redisTemplate")
        @ConditionalOnMissingBean(RedisMessageQueueUtil.class)
        public RedisMessageQueueUtil redisMessageQueueUtil(RedisTemplate<String, Object> redisTemplate) {
            return new RedisMessageQueueUtil(redisTemplate);
        }
    }
}
