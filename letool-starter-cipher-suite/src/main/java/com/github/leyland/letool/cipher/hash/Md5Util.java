package com.github.leyland.letool.cipher.hash;

import com.github.leyland.letool.cipher.exception.CipherException;
import com.github.leyland.letool.tool.util.HexUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * MD5 哈希工具.
 */
public final class Md5Util {

    private Md5Util() {}

    /**
     * 计算字符串的 MD5 值（32 位小写十六进制）.
     *
     * @param input 输入字符串
     * @return MD5 哈希值
     */
    public static String md5(String input) {
        if (input == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return HexUtil.encodeHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new CipherException("MD5 hash failed", e);
        }
    }

    /**
     * 计算字节数组的 MD5 值.
     */
    public static String md5(byte[] data) {
        if (data == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return HexUtil.encodeHex(md.digest(data));
        } catch (Exception e) {
            throw new CipherException("MD5 hash failed", e);
        }
    }
}
