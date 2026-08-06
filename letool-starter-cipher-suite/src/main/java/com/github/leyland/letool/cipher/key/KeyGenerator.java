package com.github.leyland.letool.cipher.key;

import com.github.leyland.letool.cipher.asymmetric.RsaCipher;
import com.github.leyland.letool.cipher.symmetric.AesCipher;
import com.github.leyland.letool.cipher.support.CipherSupport;

import java.util.Base64;

/**
 * 密钥生成工具 —— 统一入口，生成各类算法的密钥.
 */
public final class KeyGenerator {

    /** 工具类禁止实例化。 */
    private KeyGenerator() {
    }

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
     * <p>生成结果可直接传给 {@code HmacUtil} 的字符串密钥方法。</p>
     *
     * @return Base64 编码的密钥
     */
    public static String generateHmacKey() {
        return Base64.getEncoder().encodeToString(CipherSupport.randomBytes(32));
    }
}
