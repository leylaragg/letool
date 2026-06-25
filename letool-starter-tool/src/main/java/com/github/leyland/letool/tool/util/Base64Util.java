package com.github.leyland.letool.tool.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Base64 编解码工具——标准 Base64 和 URL 安全 Base64.
 *
 * <h3>标准 Base64 vs URL 安全 Base64</h3>
 * <table>
 *   <tr><th>类型</th><th>特殊字符</th><th>适用场景</th></tr>
 *   <tr><td>标准 Base64</td><td>{@code + / =}</td><td>数据传输、存储</td></tr>
 *   <tr><td>URL 安全 Base64</td><td>{@code - _}（无填充）</td><td>URL 参数、文件名、JWT</td></tr>
 * </table>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * String encoded = Base64Util.encode("Hello World");          // SGVsbG8gV29ybGQ=
 * String decoded = Base64Util.decode(encoded);                // Hello World
 * String urlSafe = Base64Util.encodeUrlSafe("user@example");   // URL 安全格式
 * }</pre>
 */
public final class Base64Util {

    private Base64Util() {}

    /** JDK 标准 Base64 编码器 */
    private static final Base64.Encoder ENCODER = Base64.getEncoder();
    /** JDK 标准 Base64 解码器 */
    private static final Base64.Decoder DECODER = Base64.getDecoder();
    /** JDK URL 安全 Base64 编码器 */
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder();
    /** JDK URL 安全 Base64 解码器 */
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    // ======================== 标准 Base64 ========================

    /**
     * 将字节数组编码为标准 Base64 字符串.
     *
     * @param data 原始字节数组
     * @return Base64 字符串
     */
    public static String encode(byte[] data) {
        return ENCODER.encodeToString(data);
    }

    /**
     * 将字符串（UTF-8）编码为标准 Base64 字符串.
     *
     * @param str 原始字符串
     * @return Base64 字符串
     */
    public static String encode(String str) {
        return encode(str.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 解码标准 Base64 字符串为字节数组.
     *
     * @param base64 Base64 编码字符串
     * @return 解码后的字节数组
     */
    public static byte[] decodeToBytes(String base64) {
        return DECODER.decode(base64);
    }

    /**
     * 解码标准 Base64 字符串为 UTF-8 字符串.
     *
     * @param base64 Base64 编码字符串
     * @return 解码后的原始字符串
     */
    public static String decode(String base64) {
        return new String(DECODER.decode(base64), StandardCharsets.UTF_8);
    }

    // ======================== URL 安全 Base64 ========================

    /**
     * 将字节数组编码为 URL 安全的 Base64 字符串（使用 {@code -} 和 {@code _} 替代 {@code +} 和 {@code /}）.
     *
     * @param data 原始字节数组
     * @return URL 安全的 Base64 字符串
     */
    public static String encodeUrlSafe(byte[] data) {
        return URL_ENCODER.encodeToString(data);
    }

    /**
     * 将字符串编码为 URL 安全的 Base64 字符串.
     *
     * @param str 原始字符串
     * @return URL 安全的 Base64 字符串
     */
    public static String encodeUrlSafe(String str) {
        return encodeUrlSafe(str.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 解码 URL 安全的 Base64 字符串为 UTF-8 字符串.
     *
     * @param base64 URL 安全的 Base64 字符串
     * @return 解码后的原始字符串
     */
    public static String decodeUrlSafe(String base64) {
        return new String(URL_DECODER.decode(base64), StandardCharsets.UTF_8);
    }
}
