package com.github.leyland.letool.tool.config;

import com.github.leyland.letool.tool.json.Fastjson2JsonCodec;
import com.github.leyland.letool.tool.json.JsonCodec;
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
 * 基础工具 Starter 自动配置。
 *
 * <p>该模块是轻量级工具基础层，只注册默认有用的 Spring 适配器 Bean。
 * Redis 等可选适配器通过明确的类路径和 Bean 条件进行隔离。</p>
 */
@AutoConfiguration
@AutoConfigureBefore(RedisAutoConfiguration.class)
@EnableConfigurationProperties(LetoolToolProperties.class)
public class LetoolToolAutoConfiguration {

    /**
     * 注册与底层实现无关的 JSON 扩展接口。
     *
     * <p>应用可以声明自定义 {@link JsonCodec} Bean 替换 Fastjson2。默认编解码器
     * 不可变，也不会修改 Fastjson2 全局状态。</p>
     *
     * @return 基于 Fastjson2 的默认 JSON 编解码器
     */
    @Bean
    @ConditionalOnMissingBean(JsonCodec.class)
    public JsonCodec jsonCodec() {
        return Fastjson2JsonCodec.createDefault();
    }

    /**
     * 在应用未自行提供时注册 Spring 应用上下文工具。
     *
     * @return Spring 应用上下文工具
     */
    @Bean
    @ConditionalOnMissingBean(SpringUtil.class)
    public SpringUtil springUtil() {
        return new SpringUtil();
    }

    /**
     * Redis 专用适配器配置。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RedisTemplate.class)
    static class RedisToolConfiguration {

        /**
         * 当应用具备 Redis 连接基础设施但未定义 {@code redisTemplate} 时，
         * 提供默认的对象 RedisTemplate。
         *
         * @param connectionFactory Redis 连接工厂
         * @param properties 工具 Starter 配置，包括 Redis 自动类型包白名单
         * @return 使用字符串键和 Fastjson2 JSON 值的 RedisTemplate
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
         * 仅在应用存在 Redis 基础设施时注册 Redis 工具。
         *
         * @param redisTemplate Spring Redis 对象模板
         * @return Redis 工具封装
         */
        @Bean
        @ConditionalOnBean(name = "redisTemplate")
        @ConditionalOnMissingBean(RedisUtil.class)
        public RedisUtil redisUtil(RedisTemplate<String, Object> redisTemplate) {
            return new RedisUtil(redisTemplate);
        }

        /**
         * 仅在应用存在 Redis 基础设施时注册 Redis 消息队列工具。
         *
         * @param redisTemplate Spring Redis 对象模板
         * @return Redis 消息队列工具封装
         */
        @Bean
        @ConditionalOnBean(name = "redisTemplate")
        @ConditionalOnMissingBean(RedisMessageQueueUtil.class)
        public RedisMessageQueueUtil redisMessageQueueUtil(RedisTemplate<String, Object> redisTemplate) {
            return new RedisMessageQueueUtil(redisTemplate);
        }
    }
}
