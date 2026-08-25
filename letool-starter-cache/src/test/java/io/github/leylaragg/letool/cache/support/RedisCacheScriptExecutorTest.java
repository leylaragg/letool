package io.github.leylaragg.letool.cache.support;

import io.github.leylaragg.letool.tool.redis.RedisUtil;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 缓存 Lua 执行协议测试。 */
class RedisCacheScriptExecutorTest {

    /** 整数结果必须使用 Long 解码，预序列化业务值必须保持原始字节。 */
    @Test
    void shouldDeclareLongResultAndPreserveBinaryArgument() {
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        RedisUtil redisUtil = mock(RedisUtil.class);
        when(redisUtil.getTemplate()).thenReturn(redisTemplate);
        when(redisTemplate.execute(
                any(RedisScript.class),
                any(RedisSerializer.class),
                any(RedisSerializer.class),
                anyList(),
                any(Object[].class))).thenReturn(1L);
        byte[] serializedValue = new byte[]{(byte) 0xAC, (byte) 0xED, 0x00, 0x05};

        Long result = RedisCacheScriptExecutor.executeRaw(
                redisUtil, "return 1", Long.class,
                List.of("cache:key"), serializedValue);

        ArgumentCaptor<RedisScript<?>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<RedisSerializer<?>> argumentSerializerCaptor =
                ArgumentCaptor.forClass(RedisSerializer.class);
        ArgumentCaptor<Object[]> argumentsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(redisTemplate).execute(
                scriptCaptor.capture(),
                argumentSerializerCaptor.capture(),
                any(RedisSerializer.class),
                anyList(),
                argumentsCaptor.capture());
        assertThat(result).isEqualTo(1L);
        assertThat(scriptCaptor.getValue().getResultType()).isEqualTo(Long.class);
        assertThat(argumentsCaptor.getValue()).containsExactly((Object) serializedValue);
        @SuppressWarnings("unchecked")
        RedisSerializer<Object> argumentSerializer =
                (RedisSerializer<Object>) argumentSerializerCaptor.getValue();
        assertThat(argumentSerializer.serialize(serializedValue)).isSameAs(serializedValue);
    }
}
