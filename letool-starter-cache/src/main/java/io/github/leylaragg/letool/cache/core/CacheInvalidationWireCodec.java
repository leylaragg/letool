package io.github.leylaragg.letool.cache.core;

import io.github.leylaragg.letool.cache.exception.CacheException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 缓存失效消息的内部传输协议编解码器。
 *
 * <p>该协议独立于业务 RedisTemplate 的值序列化器，发布端和监听端始终按 UTF-8 字符串传输。
 * 编解码器只在框架内部使用，避免不同节点通过业务 SPI 配出互不兼容的消息格式。</p>
 */
final class CacheInvalidationWireCodec {

    /** 当前失效消息协议版本。 */
    private static final String VERSION = "v1";
    /** 精确业务 Key 失效操作。 */
    private static final String KEYS_OPERATION = "KEYS";
    /** 整个缓存区域失效操作。 */
    private static final String ALL_OPERATION = "ALL";
    /** 序列化业务 Key 前缀失效操作。 */
    private static final String PREFIX_OPERATION = "PREFIX";
    /** 表示整个缓存区域的特殊标记。 */
    private static final String ALL_MARKER = "*";

    private CacheInvalidationWireCodec() {
    }

    /**
     * 把失效消息编码为带版本的 UTF-8 文本协议。
     *
     * @param message 待编码的失效消息
     * @return 不依赖业务值序列化器的协议载荷
     */
    static String encode(CacheInvalidationMessage message) {
        if (message == null) {
            throw CacheException.invalidationMessageInvalid();
        }
        String operation = message.isAll()
                ? ALL_OPERATION
                : message.isPrefix() ? PREFIX_OPERATION : KEYS_OPERATION;
        String keyPart = message.isAll() ? ALL_MARKER
                : message.isPrefix() ? escape(message.getPrefix())
                : message.getKeys().stream()
                        .map(CacheInvalidationWireCodec::escape)
                        .collect(Collectors.joining(","));
        return VERSION
                + "|" + escape(message.getSourceInstanceId())
                + "|" + escape(message.getCacheName())
                + "|" + operation
                + "|" + keyPart;
    }

    /**
     * 解析当前版本或 2.1.x 旧版失效载荷。
     *
     * @param payload Redis Pub/Sub 收到的文本载荷
     * @return 解析后的失效消息
     */
    static CacheInvalidationMessage decode(String payload) {
        if (payload == null || payload.isBlank()) {
            throw CacheException.invalidationMessageInvalid();
        }
        if (payload.startsWith(VERSION + "|")) {
            return decodeV1(payload);
        }
        if (payload.matches("v\\d+\\|.*")) {
            throw CacheException.invalidationMessageInvalid();
        }
        return decodeLegacy(payload);
    }

    private static CacheInvalidationMessage decodeV1(String payload) {
        String[] parts = payload.split("\\|", -1);
        if (parts.length != 5 || !VERSION.equals(parts[0])) {
            throw CacheException.invalidationMessageInvalid();
        }
        String source = unescape(parts[1]);
        String cache = unescape(parts[2]);
        validateIdentity(source, cache);
        if (ALL_OPERATION.equals(parts[3])) {
            if (!ALL_MARKER.equals(parts[4])) {
                throw CacheException.invalidationMessageInvalid();
            }
            return CacheInvalidationMessage.all(cache, source);
        }
        if (!KEYS_OPERATION.equals(parts[3])) {
            if (PREFIX_OPERATION.equals(parts[3])) {
                String prefix = unescape(parts[4]);
                if (prefix.isBlank()) {
                    throw CacheException.invalidationMessageInvalid();
                }
                return CacheInvalidationMessage.prefix(cache, prefix, source);
            }
            throw CacheException.invalidationMessageInvalid();
        }
        return CacheInvalidationMessage.keys(cache, decodeKeys(parts[4]), source);
    }

    private static CacheInvalidationMessage decodeLegacy(String payload) {
        String[] parts = payload.split("\\|", -1);
        if (parts.length != 4 || (!"0".equals(parts[2]) && !"1".equals(parts[2]))) {
            throw CacheException.invalidationMessageInvalid();
        }
        String source = unescape(parts[0]);
        String cache = unescape(parts[1]);
        validateIdentity(source, cache);
        if ("1".equals(parts[2])) {
            if (!ALL_MARKER.equals(parts[3])) {
                throw CacheException.invalidationMessageInvalid();
            }
            return CacheInvalidationMessage.all(cache, source);
        }
        return CacheInvalidationMessage.keys(cache, decodeKeys(parts[3]), source);
    }

    private static List<String> decodeKeys(String rawKeys) {
        List<String> keys = new ArrayList<>();
        if (rawKeys.isBlank()) {
            return keys;
        }
        for (String key : rawKeys.split(",")) {
            if (!key.isEmpty()) {
                keys.add(unescape(key));
            }
        }
        return keys;
    }

    private static void validateIdentity(String source, String cache) {
        if (source.isBlank() || cache.isBlank()) {
            throw CacheException.invalidationMessageInvalid();
        }
    }

    private static String escape(String value) {
        return value == null
                ? ""
                : value.replace("\\", "\\\\")
                        .replace("|", "\\p")
                        .replace(",", "\\c");
    }

    private static String unescape(String value) {
        StringBuilder output = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current != '\\') {
                output.append(current);
                continue;
            }
            if (++index >= value.length()) {
                throw CacheException.invalidationMessageInvalid();
            }
            char escaped = value.charAt(index);
            if (escaped == '\\') {
                output.append('\\');
            } else if (escaped == 'p') {
                output.append('|');
            } else if (escaped == 'c') {
                output.append(',');
            } else {
                throw CacheException.invalidationMessageInvalid();
            }
        }
        return output.toString();
    }
}
