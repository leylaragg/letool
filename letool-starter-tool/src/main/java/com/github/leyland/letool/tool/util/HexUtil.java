package com.github.leyland.letool.tool.util;

import com.github.leyland.letool.tool.encoding.EncodingOperationException;

import java.nio.charset.StandardCharsets;

/**
 * 十六进制编解码工具，提供字节数组与十六进制文本之间的便捷转换。
 *
 * <p>为兼容现有调用，编码和解码入口继续保持 {@code null -> null}；
 * 非空文本会执行严格长度与字符校验。</p>
 */
public final class HexUtil {

    /** 小写十六进制字符表。 */
    private static final char[] LOWER_CASE_DIGITS = "0123456789abcdef".toCharArray();

    /** 大写十六进制字符表。 */
    private static final char[] UPPER_CASE_DIGITS = "0123456789ABCDEF".toCharArray();

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
        char[] digits = upperCase ? UPPER_CASE_DIGITS : LOWER_CASE_DIGITS;
        char[] result = new char[data.length * 2];
        for (int index = 0; index < data.length; index++) {
            int value = data[index] & 0xff;
            result[index * 2] = digits[value >>> 4];
            result[index * 2 + 1] = digits[value & 0x0f];
        }
        return new String(result);
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
        if ((hex.length() & 1) != 0) {
            throw EncodingOperationException.hexDecodeFailed(
                    new IllegalArgumentException("Hex text length must be even")
            );
        }

        byte[] result = new byte[hex.length() / 2];
        for (int index = 0; index < hex.length(); index += 2) {
            int high = Character.digit(hex.charAt(index), 16);
            int low = Character.digit(hex.charAt(index + 1), 16);
            if (high < 0 || low < 0) {
                throw EncodingOperationException.hexDecodeFailed(
                        new IllegalArgumentException("Hex text contains an invalid character")
                );
            }
            result[index / 2] = (byte) ((high << 4) | low);
        }
        return result;
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
