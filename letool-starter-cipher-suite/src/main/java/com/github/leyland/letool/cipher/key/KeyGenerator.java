package com.github.leyland.letool.cipher.key;

import com.github.leyland.letool.cipher.asymmetric.RsaCipher;
import com.github.leyland.letool.cipher.symmetric.AesCipher;

/**
 * 密钥生成工具 —— 统一入口，生成各类算法的密钥.
 */
public final class KeyGenerator {

    private KeyGenerator() {}

    /**
     * 生成 AES 密钥（Base64 编码）.
     *
     * @param keySize 密钥大小：128 / 192 / 256
     * @return Base64 编码的密钥
     */
    public static String generateAesKey(int keySize) {
        return AesCipher.generateKey(keySize);
    }

    /**
     * 生成 RSA 密钥对.
     *
     * @param keySize 密钥大小（推荐 2048）
     * @return RSA 密钥对
     */
    public static RsaCipher.RsaKeyPair generateRsaKeyPair(int keySize) {
        return RsaCipher.generateKeyPair(keySize);
    }

    /**
     * 生成 HmacSHA256 密钥（Base64 编码的随机字节）.
     *
     * @return Base64 编码的密钥
     */
    public static String generateHmacKey() {
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        return com.github.leyland.letool.tool.util.Base64Util.encode(key);
    }
}
