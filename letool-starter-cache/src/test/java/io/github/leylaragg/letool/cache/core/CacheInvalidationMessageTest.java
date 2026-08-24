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
import static org.mockito.Mockito.verify;

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
    @DisplayName("失效消息应使用带版本的 UTF-8 协议")
    void shouldEncodeVersionedWirePayload() {
        CacheInvalidationMessage message = CacheInvalidationMessage.keys(
                "规则|缓存",
                List.of("项目,版本", "路径\\节点"),
                "节点|一"
        );

        String payload = message.toPayload();

        assertTrue(payload.startsWith("v1|"));
        CacheInvalidationMessage decoded = CacheInvalidationMessage.fromPayload(payload);
        assertEquals("规则|缓存", decoded.getCacheName());
        assertEquals(List.of("项目,版本", "路径\\节点"), decoded.getKeys());
        assertEquals("节点|一", decoded.getSourceInstanceId());
    }

    @Test
    @DisplayName("PREFIX 消息支持中文和 Redis glob 特殊字符")
    void shouldRoundTripPrefixMessage() {
        CacheInvalidationMessage source = CacheInvalidationMessage.prefix(
                "规则|索引",
                "项目:*?[草稿]\\节点",
                "节点|一"
        );

        CacheInvalidationMessage decoded =
                CacheInvalidationMessage.fromPayload(source.toPayload());

        assertTrue(decoded.isPrefix());
        assertFalse(decoded.isAll());
        assertEquals("项目:*?[草稿]\\节点", decoded.getPrefix());
    }

    @Test
    @DisplayName("PREFIX 消息拒绝空前缀")
    void shouldRejectBlankPrefix() {
        assertThrows(CacheException.class, () ->
                CacheInvalidationMessage.prefix("rules", " ", "node-a"));
    }

    @Test
    @DisplayName("监听器把 PREFIX 消息路由为本地前缀清理")
    void listenerShouldRoutePrefixInvalidation() {
        CacheManager cacheManager = mock(CacheManager.class);
        org.mockito.Mockito.when(cacheManager.instanceId()).thenReturn("node-b");
        RedisCacheInvalidationListener listener =
                new RedisCacheInvalidationListener(cacheManager);

        listener.onMessage(CacheInvalidationMessage.prefix(
                "rule:index", "project:42:", "node-a").toPayload());

        verify(cacheManager).evictLocalByPrefix("rule:index", "project:42:");
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
