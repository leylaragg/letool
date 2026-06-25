package com.github.leyland.letool.cipher.sm;

import com.github.leyland.letool.cipher.exception.CipherException;
import com.github.leyland.letool.tool.util.Base64Util;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * 国密 SM2 非对称加密 —— 类似 RSA，基于椭圆曲线.
 */
public final class Sm2Util {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private Sm2Util() {}

    /**
     * 生成 SM2 密钥对.
     *
     * @return Base64 编码的密钥对
     */
    public static Sm2KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
            generator.initialize(256, new SecureRandom());
            KeyPair pair = generator.generateKeyPair();
            return new Sm2KeyPair(
                    Base64Util.encode(pair.getPublic().getEncoded()),
                    Base64Util.encode(pair.getPrivate().getEncoded()));
        } catch (Exception e) {
            throw new CipherException("Failed to generate SM2 key pair", e);
        }
    }

    /**
     * SM2 公钥加密.
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
            KeyFactory keyFactory = KeyFactory.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
            PublicKey publicKey = keyFactory.generatePublic(keySpec);

            Cipher cipher = Cipher.getInstance("SM2", BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            return Base64Util.encode(cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8)));
        } catch (CipherException e) {
            throw e;
        } catch (Exception e) {
            throw new CipherException("SM2 encrypt failed", e);
        }
    }

    /**
     * SM2 私钥解密.
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
            KeyFactory keyFactory = KeyFactory.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
            PrivateKey privateKey = keyFactory.generatePrivate(keySpec);

            Cipher cipher = Cipher.getInstance("SM2", BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            return new String(cipher.doFinal(Base64Util.decodeToBytes(cipherText)), StandardCharsets.UTF_8);
        } catch (CipherException e) {
            throw e;
        } catch (Exception e) {
            throw new CipherException("SM2 decrypt failed", e);
        }
    }

    /**
     * SM2 密钥对.
     */
    public static class Sm2KeyPair {
        private final String publicKey;
        private final String privateKey;

        public Sm2KeyPair(String publicKey, String privateKey) {
            this.publicKey = publicKey;
            this.privateKey = privateKey;
        }

        public String getPublicKey() { return publicKey; }
        public String getPrivateKey() { return privateKey; }
    }
}
