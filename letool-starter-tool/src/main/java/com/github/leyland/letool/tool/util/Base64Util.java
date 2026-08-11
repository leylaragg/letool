package com.github.leyland.letool.tool.util;

import com.github.leyland.letool.tool.encoding.EncodingOperationException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Base64 编解码工具，提供标准编码和 URL 安全编码的便捷入口。
 *
 * <p>所有字符串入口固定使用 UTF-8。URL 安全编码默认保留 JDK 的填充字符，
 * 需要无填充文本时应显式调用 {@link #encodeUrlSafeWithoutPadding(byte[])}。</p>
 */
public final class Base64Util {

    /** JDK 标准 Base64 编码器。 */
    private static final Base64.Encoder ENCODER = Base64.getEncoder();

    /** JDK 标准 Base64 解码器。 */
    private static final Base64.Decoder DECODER = Base64.getDecoder();

    /** JDK URL 安全 Base64 编码器，保留填充字符。 */
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder();

    /** JDK URL 安全 Base64 编码器，不输出填充字符。 */
    private static final Base64.Encoder URL_ENCODER_WITHOUT_PADDING =
            Base64.getUrlEncoder().withoutPadding();

    /** JDK URL 安全 Base64 解码器。 */
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    /**
     * 禁止创建工具类实例。
     */
    private Base64Util() {
    }

    /**
     * 将字节数组编码为标准 Base64 字符串。
     *
     * @param data 原始字节数组，不得为 {@code null}
     * @return 标准 Base64 字符串
     * @throws EncodingOperationException 当字节数组为空引用时抛出
     */
    public static String encode(byte[] data) {
        requireNonNull(data, "data");
        return ENCODER.encodeToString(data);
    }

    /**
     * 将 UTF-8 字符串编码为标准 Base64 字符串。
     *
     * @param str 原始字符串，不得为 {@code null}
     * @return 标准 Base64 字符串
     * @throws EncodingOperationException 当字符串为空引用时抛出
     */
    public static String encode(String str) {
        requireNonNull(str, "str");
        return encode(str.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 将标准 Base64 字符串解码为字节数组。
     *
     * @param base64 标准 Base64 文本，不得为 {@code null}
     * @return 解码后的字节数组
     * @throws EncodingOperationException 当参数为空或文本格式不合法时抛出
     */
    public static byte[] decodeToBytes(String base64) {
        requireNonNull(base64, "base64");
        try {
            return DECODER.decode(base64);
        } catch (IllegalArgumentException exception) {
            throw EncodingOperationException.base64DecodeFailed("standard", exception);
        }
    }

    /**
     * 将标准 Base64 字符串解码为 UTF-8 字符串。
     *
     * @param base64 标准 Base64 文本，不得为 {@code null}
     * @return 解码后的 UTF-8 字符串
     * @throws EncodingOperationException 当参数为空或文本格式不合法时抛出
     */
    public static String decode(String base64) {
        return new String(decodeToBytes(base64), StandardCharsets.UTF_8);
    }

    /**
     * 将字节数组编码为保留填充字符的 URL 安全 Base64 字符串。
     *
     * @param data 原始字节数组，不得为 {@code null}
     * @return 使用 {@code -}、{@code _} 并可能包含 {@code =} 的 Base64 字符串
     * @throws EncodingOperationException 当字节数组为空引用时抛出
     */
    public static String encodeUrlSafe(byte[] data) {
        requireNonNull(data, "data");
        return URL_ENCODER.encodeToString(data);
    }

    /**
     * 将 UTF-8 字符串编码为保留填充字符的 URL 安全 Base64 字符串。
     *
     * @param str 原始字符串，不得为 {@code null}
     * @return URL 安全 Base64 字符串
     * @throws EncodingOperationException 当字符串为空引用时抛出
     */
    public static String encodeUrlSafe(String str) {
        requireNonNull(str, "str");
        return encodeUrlSafe(str.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 将字节数组编码为不含填充字符的 URL 安全 Base64 字符串。
     *
     * @param data 原始字节数组，不得为 {@code null}
     * @return 不含 {@code =} 的 URL 安全 Base64 字符串
     * @throws EncodingOperationException 当字节数组为空引用时抛出
     */
    public static String encodeUrlSafeWithoutPadding(byte[] data) {
        requireNonNull(data, "data");
        return URL_ENCODER_WITHOUT_PADDING.encodeToString(data);
    }

    /**
     * 将 UTF-8 字符串编码为不含填充字符的 URL 安全 Base64 字符串。
     *
     * @param str 原始字符串，不得为 {@code null}
     * @return 不含 {@code =} 的 URL 安全 Base64 字符串
     * @throws EncodingOperationException 当字符串为空引用时抛出
     */
    public static String encodeUrlSafeWithoutPadding(String str) {
        requireNonNull(str, "str");
        return encodeUrlSafeWithoutPadding(str.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 将 URL 安全 Base64 字符串解码为字节数组。
     *
     * <p>JDK 解码器同时接受有填充和无填充的合法文本。</p>
     *
     * @param base64 URL 安全 Base64 文本，不得为 {@code null}
     * @return 解码后的字节数组
     * @throws EncodingOperationException 当参数为空或文本格式不合法时抛出
     */
    public static byte[] decodeUrlSafeToBytes(String base64) {
        requireNonNull(base64, "base64");
        try {
            return URL_DECODER.decode(base64);
        } catch (IllegalArgumentException exception) {
            throw EncodingOperationException.base64DecodeFailed("url-safe", exception);
        }
    }

    /**
     * 将 URL 安全 Base64 字符串解码为 UTF-8 字符串。
     *
     * @param base64 URL 安全 Base64 文本，不得为 {@code null}
     * @return 解码后的 UTF-8 字符串
     * @throws EncodingOperationException 当参数为空或文本格式不合法时抛出
     */
    public static String decodeUrlSafe(String base64) {
        return new String(decodeUrlSafeToBytes(base64), StandardCharsets.UTF_8);
    }

    /**
     * 校验公开方法的必填参数。
     *
     * @param value 待校验参数
     * @param parameterName 安全的参数名称
     */
    private static void requireNonNull(Object value, String parameterName) {
        if (value == null) {
            throw EncodingOperationException.invalidArgument(parameterName);
        }
    }
}
