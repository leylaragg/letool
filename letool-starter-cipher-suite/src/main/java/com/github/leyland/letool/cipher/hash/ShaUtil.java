package com.github.leyland.letool.cipher.hash;

import com.github.leyland.letool.cipher.exception.CipherException;
import com.github.leyland.letool.cipher.support.CipherSupport;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * SHA-256 和 SHA-512 摘要工具。
 */
public final class ShaUtil {

    /** 工具类禁止实例化。 */
    private ShaUtil() {
    }

    /**
     * 计算 UTF-8 字符串的 SHA-256 摘要。
     *
     * @param input 输入字符串
     * @return 小写十六进制摘要
     */
    public static String sha256(String input) {
        CipherSupport.requireNonNull(input, "SHA-256 输入");
        return sha256(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算 UTF-8 字符串的 SHA-512 摘要。
     *
     * @param input 输入字符串
     * @return 小写十六进制摘要
     */
    public static String sha512(String input) {
        CipherSupport.requireNonNull(input, "SHA-512 输入");
        return sha512(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算字节数组的 SHA-256 摘要。
     *
     * @param data 输入字节
     * @return 小写十六进制摘要
     */
    public static String sha256(byte[] data) {
        return digest(data, "SHA-256");
    }

    /**
     * 计算字节数组的 SHA-512 摘要。
     *
     * @param data 输入字节
     * @return 小写十六进制摘要
     */
    public static String sha512(byte[] data) {
        return digest(data, "SHA-512");
    }

    /**
     * 执行固定 SHA-2 摘要。
     *
     * @param data 输入字节
     * @param algorithm 固定算法名称
     * @return 小写十六进制摘要
     */
    private static String digest(byte[] data, String algorithm) {
        CipherSupport.requireNonNull(data, algorithm + " 输入");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(data));
        } catch (GeneralSecurityException exception) {
            throw CipherException.operationFailed(algorithm + " 摘要", exception);
        }
    }
}
