package io.github.leylaragg.letool.cipher.hmac;

import io.github.leylaragg.letool.cipher.exception.CipherException;
import io.github.leylaragg.letool.cipher.support.CipherSupport;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

/**
 * HMAC-SHA256 和 HMAC-SHA512 消息认证工具。
 *
 * <p>字符串密钥统一为 Base64 编码的原始密钥，生产 API 要求密钥至少 256 位。</p>
 */
public final class HmacUtil {

    private static final int MINIMUM_KEY_LENGTH = 32;

    /** 工具类禁止实例化。 */
    private HmacUtil() {
    }

    /**
     * 计算 HMAC-SHA256 并返回小写十六进制结果。
     *
     * @param data 待认证数据
     * @param base64Key Base64 编码的原始密钥
     * @return 小写十六进制消息认证码
     */
    public static String hmacSha256(String data, String base64Key) {
        return HexFormat.of().formatHex(calculate(data, decodeKey(base64Key), "HmacSHA256"));
    }

    /**
     * 计算 HMAC-SHA256 并返回 Base64 结果。
     *
     * @param data 待认证数据
     * @param base64Key Base64 编码的原始密钥
     * @return Base64 消息认证码
     */
    public static String hmacSha256Base64(String data, String base64Key) {
        return Base64.getEncoder().encodeToString(calculate(data, decodeKey(base64Key), "HmacSHA256"));
    }

    /**
     * 计算 HMAC-SHA512 并返回小写十六进制结果。
     *
     * @param data 待认证数据
     * @param base64Key Base64 编码的原始密钥
     * @return 小写十六进制消息认证码
     */
    public static String hmacSha512(String data, String base64Key) {
        return HexFormat.of().formatHex(calculate(data, decodeKey(base64Key), "HmacSHA512"));
    }

    /**
     * 计算 HMAC-SHA512 并返回 Base64 结果。
     *
     * @param data 待认证数据
     * @param base64Key Base64 编码的原始密钥
     * @return Base64 消息认证码
     */
    public static String hmacSha512Base64(String data, String base64Key) {
        return Base64.getEncoder().encodeToString(calculate(data, decodeKey(base64Key), "HmacSHA512"));
    }

    /**
     * 使用原始密钥计算 HMAC-SHA256 十六进制结果。
     *
     * @param data 待认证数据
     * @param key 原始密钥字节
     * @return 小写十六进制消息认证码
     */
    public static String hmacSha256(String data, byte[] key) {
        return HexFormat.of().formatHex(calculate(data, requireKey(key), "HmacSHA256"));
    }

    /**
     * 使用原始密钥计算 HMAC-SHA256 Base64 结果。
     *
     * @param data 待认证数据
     * @param key 原始密钥字节
     * @return Base64 消息认证码
     */
    public static String hmacSha256Base64(String data, byte[] key) {
        return Base64.getEncoder().encodeToString(calculate(data, requireKey(key), "HmacSHA256"));
    }

    /**
     * 使用原始密钥计算 HMAC-SHA512 十六进制结果。
     *
     * @param data 待认证数据
     * @param key 原始密钥字节
     * @return 小写十六进制消息认证码
     */
    public static String hmacSha512(String data, byte[] key) {
        return HexFormat.of().formatHex(calculate(data, requireKey(key), "HmacSHA512"));
    }

    /**
     * 使用原始密钥计算 HMAC-SHA512 Base64 结果。
     *
     * @param data 待认证数据
     * @param key 原始密钥字节
     * @return Base64 消息认证码
     */
    public static String hmacSha512Base64(String data, byte[] key) {
        return Base64.getEncoder().encodeToString(calculate(data, requireKey(key), "HmacSHA512"));
    }

    /**
     * 使用常量时间比较验证十六进制 HMAC-SHA256。
     *
     * @param data 原始数据
     * @param expectedHex 预期十六进制消息认证码
     * @param base64Key Base64 编码的原始密钥
     * @return 消息认证码匹配时返回 {@code true}
     */
    public static boolean verifySha256(String data, String expectedHex, String base64Key) {
        byte[] expected = decodeHex(expectedHex);
        byte[] actual = calculate(data, decodeKey(base64Key), "HmacSHA256");
        return MessageDigest.isEqual(actual, expected);
    }

    /**
     * 使用常量时间比较验证 Base64 HMAC-SHA256。
     *
     * @param data 原始数据
     * @param expectedBase64 预期 Base64 消息认证码
     * @param base64Key Base64 编码的原始密钥
     * @return 消息认证码匹配时返回 {@code true}
     */
    public static boolean verifySha256Base64(String data, String expectedBase64, String base64Key) {
        byte[] expected = decodeSha256Base64(expectedBase64);
        byte[] actual = calculate(data, decodeKey(base64Key), "HmacSHA256");
        return MessageDigest.isEqual(actual, expected);
    }

    /**
     * 执行 HMAC 运算。
     *
     * @param data 待认证数据
     * @param key 已校验的原始密钥
     * @param algorithm JCA 算法名称
     * @return 原始消息认证码
     */
    private static byte[] calculate(String data, byte[] key, String algorithm) {
        CipherSupport.requireNonNull(data, "HMAC 数据");
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key, algorithm));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw CipherException.operationFailed(algorithm, exception);
        }
    }

    /**
     * 解码并校验 Base64 HMAC 密钥。
     *
     * @param base64Key Base64 编码的原始密钥
     * @return 原始密钥字节
     */
    private static byte[] decodeKey(String base64Key) {
        return requireKey(CipherSupport.decodeKey(base64Key, "HMAC"));
    }

    /**
     * 校验 HMAC 密钥长度并返回防御性副本。
     *
     * @param key 原始密钥
     * @return 密钥副本
     */
    private static byte[] requireKey(byte[] key) {
        if (key == null || key.length < MINIMUM_KEY_LENGTH) {
            throw CipherException.invalidKey("HMAC 密钥不得少于 256 位");
        }
        return key.clone();
    }

    /**
     * 解码预期十六进制消息认证码。
     *
     * @param expectedHex 预期十六进制消息认证码
     * @return 原始消息认证码
     */
    private static byte[] decodeHex(String expectedHex) {
        if (expectedHex == null || expectedHex.length() != 64) {
            throw CipherException.invalidParameter("HMAC 十六进制编码不正确");
        }
        try {
            return HexFormat.of().parseHex(expectedHex);
        } catch (RuntimeException exception) {
            throw CipherException.invalidParameter("HMAC 十六进制编码不正确");
        }
    }

    /**
     * 解码并校验固定长度的 Base64 HMAC-SHA256。
     *
     * @param expectedBase64 预期 Base64 消息认证码
     * @return 32 字节消息认证码
     */
    private static byte[] decodeSha256Base64(String expectedBase64) {
        if (expectedBase64 == null || expectedBase64.isBlank() || expectedBase64.length() > 44) {
            throw CipherException.invalidParameter("HMAC Base64 编码不正确");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(expectedBase64);
            if (decoded.length != 32) {
                throw CipherException.invalidParameter("HMAC-SHA256 长度不正确");
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw CipherException.invalidParameter("HMAC Base64 编码不正确");
        }
    }
}
