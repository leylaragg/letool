package io.github.leylaragg.letool.redis.config;

import io.github.leylaragg.letool.redis.serializer.FastJson2JsonRedisSerializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * 在 Spring Boot 注册默认 RedisTemplate 之前处理 Letool 的兼容模板。
 *
 * <p>只有业务方已经提前提供唯一连接工厂、同时没有对象模板时才创建 Fastjson2 模板。
 * 标准 Boot 场景由 {@link RedisAutoConfiguration} 管理默认模板，避免 Letool 覆盖应用所有权。</p>
 */
@AutoConfiguration(before = RedisAutoConfiguration.class)
@ConditionalOnClass(RedisTemplate.class)
@EnableConfigurationProperties(LetoolRedisProperties.class)
public class LetoolRedisTemplateAutoConfiguration {

    /**
     * 保留既有的字符串键、Fastjson2 值序列化协议。
     *
     * @param connectionFactoryProvider 唯一 Redis 连接工厂提供器
     * @param properties Tool Starter 配置，包括 Redis 自动类型包白名单
     * @return Letool 兼容对象模板
     */
    @Bean("redisTemplate")
    @ConditionalOnSingleCandidate(RedisConnectionFactory.class)
    @ConditionalOnMissingBean(name = "redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(
            ObjectProvider<RedisConnectionFactory> connectionFactoryProvider,
            LetoolRedisProperties properties) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactoryProvider.getObject());

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);

        FastJson2JsonRedisSerializer<Object> valueSerializer =
                new FastJson2JsonRedisSerializer<>(
                        Object.class,
                        properties.getSerialization().getAutoTypeAcceptPrefixes().toArray(String[]::new));
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        return template;
    }
}
