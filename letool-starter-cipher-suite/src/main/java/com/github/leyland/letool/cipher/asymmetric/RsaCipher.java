package com.github.leyland.letool.cipher.asymmetric;

import com.github.leyland.letool.cipher.exception.CipherException;
import com.github.leyland.letool.tool.util.Base64Util;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * RSA 非对称加密 —— 公钥加密 / 私钥解密.
 *
 * <p>默认使用 RSA/ECB/PKCS1Padding，密钥大小 2048 位.</p>
 */
public final class RsaCipher {

    private static final String RSA_ALGORITHM = "RSA/ECB/PKCS1Padding";

    private RsaCipher() {}

    /**
     * 生成 RSA 密钥对.
     *
     * @param keySize 密钥大小（推荐 2048）
     * @return Base64 编码的密钥对
     */
    public static RsaKeyPair generateKeyPair(int keySize) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(keySize, new SecureRandom());
            KeyPair pair = generator.generateKeyPair();
            return new RsaKeyPair(
                    Base64Util.encode(pair.getPublic().getEncoded()),
                    Base64Util.encode(pair.getPrivate().getEncoded()));
        } catch (Exception e) {
            throw new CipherException("Failed to generate RSA key pair", e);
        }
    }

    /**
     * 公钥加密.
     *
     * @param plainText       明文
     * @param base64PublicKey Base64 编码的公钥
     * @return Base64 编码的密文
     */
    public static String encrypt(String plainText, String base64PublicKey) {
        if (plainText == null) return null;
        try {
            byte[] keyBytes = Base64Util.decodeToBytes(base64PublicKey);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicKey = keyFactory.generatePublic(keySpec);

            Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            return Base64Util.encode(cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8)));
        } catch (CipherException e) {
            throw e;
        } catch (Exception e) {
            throw new CipherException("RSA encrypt failed", e);
        }
    }

    /**
     * 私钥解密.
     *
     * @param cipherText        Base64 编码的密文
     * @param base64PrivateKey  Base64 编码的私钥
     * @return 明文
     */
    public static String decrypt(String cipherText, String base64PrivateKey) {
        if (cipherText == null) return null;
        try {
            byte[] keyBytes = Base64Util.decodeToBytes(base64PrivateKey);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = keyFactory.generatePrivate(keySpec);

            Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            return new String(cipher.doFinal(Base64Util.decodeToBytes(cipherText)), StandardCharsets.UTF_8);
        } catch (CipherException e) {
            throw e;
        } catch (Exception e) {
            throw new CipherException("RSA decrypt failed", e);
        }
    }

    /**
     * RSA 密钥对 —— 公钥和私钥均为 Base64 编码.
     */
    public static class RsaKeyPair {
        private final String publicKey;
        private final String privateKey;

        public RsaKeyPair(String publicKey, String privateKey) {
            this.publicKey = publicKey;
            this.privateKey = privateKey;
        }

        public String getPublicKey() { return publicKey; }
        public String getPrivateKey() { return privateKey; }
    }
}
