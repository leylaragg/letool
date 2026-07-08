package com.github.leyland.letool.cipher.key;

import com.github.leyland.letool.cipher.asymmetric.RsaCipher;
import com.github.leyland.letool.cipher.symmetric.AesCipher;

/**
 * 密钥生成工具 —— 统一入口，生成各类算法的密钥.
 */
public final class KeyGenerator {

    private KeyGenerator() {}

    /** 复用 SecureRandom 实例，避免每次创建新实例带来的熵源开销 */
    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();

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
     * 生成 HmacSHA256 密钥（Base64 编码的 32 字节随机密钥）.
     * <p>生成的密钥应通过 Base64 解码后传入 {@code HmacUtil} 的 {@code byte[]} 重载方法。</p>
     *
     * @return Base64 编码的密钥
     */
    public static String generateHmacKey() {
        byte[] key = new byte[32];
        SECURE_RANDOM.nextBytes(key);
        return com.github.leyland.letool.tool.util.Base64Util.encode(key);
    }
}
