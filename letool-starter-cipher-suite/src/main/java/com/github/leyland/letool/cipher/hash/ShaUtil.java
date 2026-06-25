package com.github.leyland.letool.cipher.hash;

import com.github.leyland.letool.cipher.exception.CipherException;
import com.github.leyland.letool.tool.util.HexUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * SHA 哈希工具 —— 支持 SHA-1 / SHA-256 / SHA-512.
 */
public final class ShaUtil {

    private ShaUtil() {}

    /**
     * 计算字符串的 SHA-256 值.
     */
    public static String sha256(String input) {
        return hash(input, "SHA-256");
    }

    /**
     * 计算字符串的 SHA-512 值.
     */
    public static String sha512(String input) {
        return hash(input, "SHA-512");
    }

    /**
     * 计算 SHA 哈希.
     *
     * @param input     输入字符串
     * @param algorithm 算法（SHA-1 / SHA-256 / SHA-512）
     * @return 十六进制哈希值
     */
    public static String hash(String input, String algorithm) {
        if (input == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            return HexUtil.encodeHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new CipherException(algorithm + " hash failed", e);
        }
    }

    /**
     * 计算字节数组的 SHA-256 值.
     */
    public static String sha256(byte[] data) {
        return hash(data, "SHA-256");
    }

    /**
     * 计算字节数组的 SHA 哈希.
     */
    public static String hash(byte[] data, String algorithm) {
        if (data == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            return HexUtil.encodeHex(md.digest(data));
        } catch (Exception e) {
            throw new CipherException(algorithm + " hash failed", e);
        }
    }
}
