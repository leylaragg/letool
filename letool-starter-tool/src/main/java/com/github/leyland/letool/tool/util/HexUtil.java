package com.github.leyland.letool.tool.util;

import com.github.leyland.letool.tool.encoding.EncodingOperationException;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * 十六进制编解码工具，提供字节数组与十六进制文本之间的便捷转换。
 *
 * <p>为兼容现有调用，编码和解码入口继续保持 {@code null -> null}；
 * 非空文本会执行严格长度与字符校验。</p>
 */
public final class HexUtil {

    /** 小写十六进制格式。 */
    private static final HexFormat LOWER_CASE_FORMAT = HexFormat.of();

    /** 大写十六进制格式。 */
    private static final HexFormat UPPER_CASE_FORMAT = HexFormat.of().withUpperCase();

    /**
     * 禁止创建工具类实例。
     */
    private HexUtil() {
    }

    /**
     * 将字节数组编码为小写十六进制字符串。
     *
     * @param data 原始字节数组，允许为 {@code null}
     * @return 小写十六进制字符串；参数为 {@code null} 时返回 {@code null}
     */
    public static String encodeHex(byte[] data) {
        return encodeHex(data, false);
    }

    /**
     * 将字节数组编码为十六进制字符串。
     *
     * @param data 原始字节数组，允许为 {@code null}
     * @param upperCase 是否使用大写十六进制字符
     * @return 十六进制字符串；参数为 {@code null} 时返回 {@code null}
     */
    public static String encodeHex(byte[] data, boolean upperCase) {
        if (data == null) {
            return null;
        }
        return (upperCase ? UPPER_CASE_FORMAT : LOWER_CASE_FORMAT).formatHex(data);
    }

    /**
     * 将 UTF-8 字符串编码为小写十六进制字符串。
     *
     * @param str 原始字符串，允许为 {@code null}
     * @return 十六进制字符串；参数为 {@code null} 时返回 {@code null}
     */
    public static String encodeHex(String str) {
        return str == null ? null : encodeHex(str.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 将十六进制字符串严格解码为字节数组。
     *
     * @param hex 十六进制字符串，允许为 {@code null}
     * @return 解码后的字节数组；参数为 {@code null} 时返回 {@code null}
     * @throws EncodingOperationException 当文本长度为奇数或包含非法字符时抛出
     */
    public static byte[] decodeHex(String hex) {
        if (hex == null) {
            return null;
        }
        try {
            return LOWER_CASE_FORMAT.parseHex(hex);
        } catch (IllegalArgumentException exception) {
            throw EncodingOperationException.hexDecodeFailed(exception);
        }
    }

    /**
     * 将十六进制字符串解码为 UTF-8 字符串。
     *
     * @param hex 十六进制字符串，允许为 {@code null}
     * @return 解码后的 UTF-8 字符串；参数为 {@code null} 时返回 {@code null}
     * @throws EncodingOperationException 当文本长度为奇数或包含非法字符时抛出
     */
    public static String decodeHexToStr(String hex) {
        byte[] bytes = decodeHex(hex);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }
}
