package com.github.leyland.letool.tool.util;

import java.nio.charset.StandardCharsets;

/**
 * 十六进制编解码工具——字节数组与十六进制字符串互转.
 *
 * <h3>典型场景</h3>
 * <ul>
 *   <li>将字节数组转为可读的十六进制字符串（如调试网络报文、显示哈希值）</li>
 *   <li>将十六进制字符串还原为原始字节（如解析配置文件中的二进制密钥）</li>
 *   <li>支持大小写输出控制，默认小写</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * String hex = HexUtil.encodeHex(new byte[]{0x0A, 0x1F});   // "0a1f"
 * String hexUpper = HexUtil.encodeHex(bytes, true);          // "0A1F"
 * byte[] bytes = HexUtil.decodeHex("0a1f");                  // [0x0A, 0x1F]
 * String str = HexUtil.encodeHex("Hello");                   // "48656c6c6f"
 * }</pre>
 */
public final class HexUtil {

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();
    private static final char[] HEX_CHARS_UPPER = "0123456789ABCDEF".toCharArray();

    private HexUtil() {}

    // ======================== 编码 ========================

    /**
     * 将字节数组编码为十六进制字符串（小写）.
     *
     * @param data 原始字节数组
     * @return 十六进制字符串，{@code data} 为 {@code null} 返回 {@code null}
     */
    public static String encodeHex(byte[] data) {
        return encodeHex(data, false);
    }

    /**
     * 将字节数组编码为十六进制字符串.
     *
     * @param data      原始字节数组
     * @param upperCase 是否使用大写字母
     * @return 十六进制字符串，{@code data} 为 {@code null} 返回 {@code null}
     */
    public static String encodeHex(byte[] data, boolean upperCase) {
        if (data == null) return null;
        char[] chars = upperCase ? HEX_CHARS_UPPER : HEX_CHARS;
        char[] result = new char[data.length * 2];
        for (int i = 0; i < data.length; i++) {
            result[i * 2] = chars[(data[i] >> 4) & 0x0F];
            result[i * 2 + 1] = chars[data[i] & 0x0F];
        }
        return new String(result);
    }

    /**
     * 将字符串（UTF-8）编码为十六进制字符串.
     *
     * @param str 原始字符串
     * @return 十六进制字符串，{@code str} 为 {@code null} 返回 {@code null}
     */
    public static String encodeHex(String str) {
        if (str == null) return null;
        return encodeHex(str.getBytes(StandardCharsets.UTF_8));
    }

    // ======================== 解码 ========================

    /**
     * 将十六进制字符串解码为字节数组.
     *
     * @param hex 十六进制字符串（长度必须为偶数）
     * @return 解码后的字节数组，{@code hex} 为 {@code null} 返回 {@code null}
     * @throws IllegalArgumentException 如果字符串含有非法十六进制字符
     */
    public static byte[] decodeHex(String hex) {
        if (hex == null) return null;
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((charToNibble(hex.charAt(i)) << 4)
                    | charToNibble(hex.charAt(i + 1)));
        }
        return data;
    }

    /**
     * 将十六进制字符串解码为 UTF-8 字符串.
     *
     * @param hex 十六进制字符串
     * @return 解码后的原始字符串，{@code hex} 为 {@code null} 返回 {@code null}
     */
    public static String decodeHexToStr(String hex) {
        byte[] bytes = decodeHex(hex);
        return bytes == null ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * 将单个十六进制字符转为 0-15 的数值.
     *
     * @param c 十六进制字符（0-9、a-f、A-F）
     * @return 对应的数值（0-15）
     * @throws IllegalArgumentException 如果字符不是合法的十六进制字符
     */
    private static int charToNibble(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        throw new IllegalArgumentException("Invalid hex char: " + c);
    }
}
