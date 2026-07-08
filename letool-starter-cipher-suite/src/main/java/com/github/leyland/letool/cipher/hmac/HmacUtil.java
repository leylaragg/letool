package com.github.leyland.letool.cipher.hmac;

import com.github.leyland.letool.cipher.exception.CipherException;
import com.github.leyland.letool.tool.util.Base64Util;
import com.github.leyland.letool.tool.util.HexUtil;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * HMAC 消息认证码工具.
 */
public final class HmacUtil {

    private HmacUtil() {}

    /**
     * 计算 HMAC-SHA256 并返回十六进制字符串.
     *
     * @param data 待签名数据
     * @param key  密钥
     * @return 十六进制 HMAC 值
     */
    public static String hmacSha256(String data, String key) {
        return hmac(data, key, "HmacSHA256");
    }

    /**
     * 计算 HMAC-SHA256 并返回 Base64 字符串.
     */
    public static String hmacSha256Base64(String data, String key) {
        return hmacBase64(data, key, "HmacSHA256");
    }

    /**
     * 计算 HMAC-SHA512 并返回十六进制字符串.
     */
    public static String hmacSha512(String data, String key) {
        return hmac(data, key, "HmacSHA512");
    }

    /**
     * 计算 HMAC-SHA512 并返回 Base64 字符串.
     */
    public static String hmacSha512Base64(String data, String key) {
        return hmacBase64(data, key, "HmacSHA512");
    }

    private static String hmac(String data, String key, String algorithm) {
        if (data == null || key == null) return null;
        try {
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), algorithm);
            Mac mac = Mac.getInstance(algorithm);
            mac.init(keySpec);
            return HexUtil.encodeHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new CipherException(algorithm + " failed", e);
        }
    }

    /**
     * 使用原始字节密钥计算 HMAC（推荐方法）.
     * <p>与 String 重载不同，此方法直接使用给定的字节数组作为密钥，不会经过 UTF-8 编码，
     * 因此可使用 {@link KeyGenerator#generateHmacKey()} 的 Base64 解码后的完整随机密钥。</p>
     */
    public static String hmacSha256(String data, byte[] key) {
        return hmac(data, key, "HmacSHA256");
    }

    public static String hmacSha256Base64(String data, byte[] key) {
        return hmacBase64(data, key, "HmacSHA256");
    }

    public static String hmacSha512(String data, byte[] key) {
        return hmac(data, key, "HmacSHA512");
    }

    public static String hmacSha512Base64(String data, byte[] key) {
        return hmacBase64(data, key, "HmacSHA512");
    }

    private static String hmac(String data, byte[] key, String algorithm) {
        if (data == null || key == null) return null;
        try {
            SecretKeySpec keySpec = new SecretKeySpec(key, algorithm);
            Mac mac = Mac.getInstance(algorithm);
            mac.init(keySpec);
            return HexUtil.encodeHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new CipherException(algorithm + " failed", e);
        }
    }

    private static String hmacBase64(String data, String key, String algorithm) {
        if (data == null || key == null) return null;
        try {
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), algorithm);
            Mac mac = Mac.getInstance(algorithm);
            mac.init(keySpec);
            return Base64Util.encode(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new CipherException(algorithm + " failed", e);
        }
    }

    private static String hmacBase64(String data, byte[] key, String algorithm) {
        if (data == null || key == null) return null;
        try {
            SecretKeySpec keySpec = new SecretKeySpec(key, algorithm);
            Mac mac = Mac.getInstance(algorithm);
            mac.init(keySpec);
            return Base64Util.encode(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new CipherException(algorithm + " failed", e);
        }
    }
}
