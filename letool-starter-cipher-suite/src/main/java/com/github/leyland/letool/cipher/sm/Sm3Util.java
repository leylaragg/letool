package com.github.leyland.letool.cipher.sm;

import com.github.leyland.letool.cipher.exception.CipherException;
import com.github.leyland.letool.tool.util.HexUtil;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Security;

/**
 * 国密 SM3 哈希算法 —— 类似 SHA-256.
 */
public final class Sm3Util {

    private static final String SM3 = "SM3";

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private Sm3Util() {}

    /**
     * 计算 SM3 哈希值.
     *
     * @param input 输入字符串
     * @return 十六进制哈希值（64 字符）
     */
    public static String sm3(String input) {
        if (input == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance(SM3, BouncyCastleProvider.PROVIDER_NAME);
            return HexUtil.encodeHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new CipherException("SM3 hash failed", e);
        }
    }

    /**
     * 计算字节数组的 SM3 哈希值.
     */
    public static String sm3(byte[] data) {
        if (data == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance(SM3, BouncyCastleProvider.PROVIDER_NAME);
            return HexUtil.encodeHex(md.digest(data));
        } catch (Exception e) {
            throw new CipherException("SM3 hash failed", e);
        }
    }
}
