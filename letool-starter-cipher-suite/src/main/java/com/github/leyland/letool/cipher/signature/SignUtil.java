package com.github.leyland.letool.cipher.signature;

import com.github.leyland.letool.cipher.exception.CipherException;
import com.github.leyland.letool.tool.util.Base64Util;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;

/**
 * 数字签名工具 —— 使用私钥对数据进行签名.
 */
public final class SignUtil {

    private static final String DEFAULT_ALGORITHM = "SHA256withRSA";

    private SignUtil() {}

    /**
     * 使用 RSA 私钥对数据签名（SHA256withRSA）.
     *
     * @param data             待签名数据
     * @param base64PrivateKey Base64 编码的 RSA 私钥
     * @return Base64 编码的签名
     */
    public static String sign(String data, String base64PrivateKey) {
        return sign(data, base64PrivateKey, DEFAULT_ALGORITHM);
    }

    /**
     * 使用私钥对数据签名（指定算法）.
     *
     * @param data             待签名数据
     * @param base64PrivateKey Base64 编码的私钥
     * @param algorithm        签名算法（如 SHA256withRSA / SHA256withECDSA）
     * @return Base64 编码的签名
     */
    public static String sign(String data, String base64PrivateKey, String algorithm) {
        if (data == null || base64PrivateKey == null) return null;
        try {
            byte[] keyBytes = Base64Util.decodeToBytes(base64PrivateKey);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);

            // Try RSA first, then EC
            PrivateKey privateKey;
            try {
                privateKey = java.security.KeyFactory.getInstance("RSA").generatePrivate(keySpec);
            } catch (Exception e) {
                privateKey = java.security.KeyFactory.getInstance("EC").generatePrivate(keySpec);
            }

            Signature signature = Signature.getInstance(algorithm);
            signature.initSign(privateKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            return Base64Util.encode(signature.sign());
        } catch (CipherException e) {
            throw e;
        } catch (Exception e) {
            throw new CipherException("Sign failed", e);
        }
    }
}
