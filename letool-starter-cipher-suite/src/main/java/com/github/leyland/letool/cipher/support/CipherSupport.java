package com.github.leyland.letool.cipher.support;

import com.github.leyland.letool.cipher.exception.CipherException;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 加密模块内部参数、编码和随机数支持。
 *
 * <p>该类公开仅用于跨算法包复用，不属于稳定业务 API。</p>
 */
public final class CipherSupport {

    /** 字符串型密码 API 允许处理的最大内存载荷，固定为 16 MiB。 */
    public static final int MAXIMUM_IN_MEMORY_PAYLOAD_BYTES = 16 * 1024 * 1024;

    private static final int MAXIMUM_BASE64_KEY_CHARACTERS = 16 * 1024;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** 工具类禁止实例化。 */
    private CipherSupport() {
    }

    /**
     * 要求参数不为 {@code null}。
     *
     * @param value 参数值
     * @param parameterName 参数名称
     * @param <T> 参数类型
     * @return 原参数值
     * @throws CipherException 参数为 {@code null} 时抛出
     */
    public static <T> T requireNonNull(T value, String parameterName) {
        if (value == null) {
            throw CipherException.invalidParameter(parameterName + "不能为空");
        }
        return value;
    }

    /**
     * 解码 Base64 密钥并屏蔽原始密钥内容。
     *
     * @param base64Key Base64 密钥
     * @param algorithm 算法名称
     * @return 解码后的密钥字节
     * @throws CipherException 密钥为空或不是合法 Base64 时抛出
     */
    public static byte[] decodeKey(String base64Key, String algorithm) {
        if (base64Key == null || base64Key.isBlank()) {
            throw CipherException.invalidKey(algorithm);
        }
        if (base64Key.length() > MAXIMUM_BASE64_KEY_CHARACTERS) {
            throw CipherException.invalidKey(algorithm + "编码长度超过支持范围");
        }
        try {
            return Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException exception) {
            throw CipherException.invalidKey(algorithm);
        }
    }

    /**
     * 校验 AES 密钥长度。
     *
     * @param keyBytes AES 密钥字节
     * @return 原密钥字节
     * @throws CipherException 长度不是 128、192 或 256 位时抛出
     */
    public static byte[] requireAesKey(byte[] keyBytes) {
        int length = keyBytes.length;
        if (length != 16 && length != 24 && length != 32) {
            throw CipherException.invalidKey("AES");
        }
        return keyBytes;
    }

    /**
     * 校验 SM4 密钥长度。
     *
     * @param keyBytes SM4 密钥字节
     * @return 原密钥字节
     * @throws CipherException 长度不是 128 位时抛出
     */
    public static byte[] requireSm4Key(byte[] keyBytes) {
        if (keyBytes.length != 16) {
            throw CipherException.invalidKey("SM4");
        }
        return keyBytes;
    }

    /**
     * 校验字符串型密码 API 的内存载荷上限。
     *
     * @param data 已编码为字节的载荷
     * @param parameterName 参数名称
     * @return 原载荷字节
     * @throws CipherException 载荷超过 16 MiB 时抛出
     */
    public static byte[] requireInMemoryPayload(byte[] data, String parameterName) {
        requireNonNull(data, parameterName);
        if (data.length > MAXIMUM_IN_MEMORY_PAYLOAD_BYTES) {
            throw CipherException.invalidParameter(
                    parameterName + "不得超过 " + MAXIMUM_IN_MEMORY_PAYLOAD_BYTES + " 字节");
        }
        return data;
    }

    /**
     * 生成密码学安全随机字节。
     *
     * @param length 字节长度，必须大于零
     * @return 新的随机字节数组
     */
    public static byte[] randomBytes(int length) {
        if (length <= 0) {
            throw CipherException.invalidParameter("随机数长度必须大于零");
        }
        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }
}
