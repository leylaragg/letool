package com.github.leyland.letool.tool.redis;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RedisUtil}.
 */
class RedisUtilTest {

    /**
     * Pipeline execution should pass Redis operations to the caller callback.
     */
    @Test
    void pipelineShouldExecuteConsumerWithRedisOperations() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        List<Object> expected = List.of("ok");
        when(redisTemplate.executePipelined(any(SessionCallback.class))).thenAnswer(invocation -> {
            SessionCallback<?> callback = invocation.getArgument(0);
            callback.execute(redisTemplate);
            return expected;
        });

        RedisUtil redisUtil = new RedisUtil(redisTemplate);
        AtomicReference<RedisOperations<String, String>> operationsRef = new AtomicReference<>();

        List<Object> actual = redisUtil.pipeline(operationsRef::set);

        assertThat(actual).isSameAs(expected);
        assertThat(operationsRef.get()).isSameAs(redisTemplate);
    }
}
