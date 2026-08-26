package io.github.leylaragg.letool.cache.core;

import io.github.leylaragg.letool.redis.serializer.FastJson2JsonRedisSerializer;
import io.github.leylaragg.letool.redis.RedisFacade;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redis 失效消息发布字节契约测试。
 */
class RedisCacheInvalidationPublisherTest {

    @Test
    void defaultObjectSerializerShouldNotWrapInvalidationPayload() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        when(connectionFactory.getConnection()).thenReturn(connection);
        when(connection.publish(any(byte[].class), any(byte[].class))).thenReturn(1L);

        RedisTemplate<String, Object> objectTemplate = new RedisTemplate<>();
        objectTemplate.setConnectionFactory(connectionFactory);
        objectTemplate.setKeySerializer(new StringRedisSerializer());
        objectTemplate.setValueSerializer(new FastJson2JsonRedisSerializer<>(Object.class));
        objectTemplate.afterPropertiesSet();

        RedisCacheInvalidationPublisher publisher = new RedisCacheInvalidationPublisher(
                new RedisFacade(objectTemplate),
                "letool:test:invalidation"
        );
        CacheInvalidationMessage message = CacheInvalidationMessage.keys(
                "rule-cache",
                List.of("project-1"),
                "node-a"
        );

        publisher.publish(message);

        ArgumentCaptor<byte[]> bodyCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(connection).publish(any(byte[].class), bodyCaptor.capture());
        String publishedPayload = new String(bodyCaptor.getValue(), StandardCharsets.UTF_8);
        assertThat(publishedPayload).isEqualTo(message.toPayload());
        assertThat(publishedPayload).startsWith("v1|");
    }
}
