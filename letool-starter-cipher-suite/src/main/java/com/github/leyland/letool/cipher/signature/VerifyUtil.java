package com.github.leyland.letool.cipher.signature;

import com.github.leyland.letool.cipher.exception.CipherException;
import com.github.leyland.letool.tool.util.Base64Util;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;

/**
 * 签名验证工具 —— 使用公钥验证数字签名.
 */
public final class VerifyUtil {

    private static final String DEFAULT_ALGORITHM = "SHA256withRSA";

    private VerifyUtil() {}

    /**
     * 使用 RSA 公钥验证签名（SHA256withRSA）.
     *
     * @param data             原始数据
     * @param base64Signature  Base64 编码的签名
     * @param base64PublicKey  Base64 编码的 RSA 公钥
     * @return {@code true} 如果签名有效
     */
    public static boolean verify(String data, String base64Signature, String base64PublicKey) {
        return verify(data, base64Signature, base64PublicKey, DEFAULT_ALGORITHM);
    }

    /**
     * 使用公钥验证签名（指定算法）.
     *
     * @param data             原始数据
     * @param base64Signature  Base64 编码的签名
     * @param base64PublicKey  Base64 编码的公钥
     * @param algorithm        签名算法
     * @return {@code true} 如果签名有效
     */
    public static boolean verify(String data, String base64Signature, String base64PublicKey, String algorithm) {
        if (data == null || base64Signature == null || base64PublicKey == null) return false;
        try {
            byte[] keyBytes = Base64Util.decodeToBytes(base64PublicKey);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);

            PublicKey publicKey;
            try {
                publicKey = java.security.KeyFactory.getInstance("RSA").generatePublic(keySpec);
            } catch (Exception e) {
                publicKey = java.security.KeyFactory.getInstance("EC").generatePublic(keySpec);
            }

            Signature signature = Signature.getInstance(algorithm);
            signature.initVerify(publicKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64Util.decodeToBytes(base64Signature));
        } catch (Exception e) {
            throw new CipherException("Verify failed", e);
        }
    }
}
