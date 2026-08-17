package io.github.leylaragg.letool.cache.core;

import io.github.leylaragg.letool.cache.exception.CacheErrorCode;
import io.github.leylaragg.letool.cache.exception.CacheException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 缓存失效消息编解码测试。
 */
@DisplayName("缓存失效消息测试")
@ExtendWith(OutputCaptureExtension.class)
class CacheInvalidationMessageTest {

    @Test
    @DisplayName("包含分隔符和转义符的字段应完整往返")
    void shouldRoundTripEscapedFields() {
        CacheInvalidationMessage source =
                CacheInvalidationMessage.keys(
                        "cache|name",
                        List.of("a,b", "c|d", "e\\f"),
                        "node|1"
                );

        CacheInvalidationMessage decoded =
                CacheInvalidationMessage.fromPayload(source.toPayload());

        assertEquals("cache|name", decoded.getCacheName());
        assertEquals(List.of("a,b", "c|d", "e\\f"), decoded.getKeys());
        assertEquals("node|1", decoded.getSourceInstanceId());
        assertFalse(decoded.isAll());
    }

    @Test
    @DisplayName("非法 all 标记应抛出统一缓存异常")
    void shouldRejectInvalidAllFlag() {
        CacheException exception = assertThrows(
                CacheException.class,
                () -> CacheInvalidationMessage.fromPayload("node|cache|x|key")
        );

        assertEquals(
                CacheErrorCode.INVALIDATION_MESSAGE_INVALID.getCode(),
                exception.getCode()
        );
    }

    @Test
    @DisplayName("非法失效消息日志不应泄露原始载荷")
    void listenerShouldNotLogRawInvalidPayload(CapturedOutput output) {
        RedisCacheInvalidationListener listener =
                new RedisCacheInvalidationListener(
                        mock(CacheManager.class)
                );

        listener.onMessage("secret-user-key");

        assertTrue(output.getOut().contains("invalidation"));
        assertFalse(output.getOut().contains("secret-user-key"));
    }
}
