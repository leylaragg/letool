package com.github.leyland.letool.cipher.asymmetric;

import com.github.leyland.letool.cipher.exception.CipherException;
import com.github.leyland.letool.cipher.support.CipherSupport;
import com.github.leyland.letool.cipher.support.RsaKeySupport;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;

/**
 * RSA-OAEP-SHA256 小数据加解密工具。
 *
 * <p>RSA 只适合加密会话密钥等短数据。业务大文本应使用 AES-GCM 或 SM4-GCM，
 * 再使用 RSA 封装对称密钥。</p>
 */
public final class RsaCipher {

    private static final String TRANSFORMATION = "RSA/ECB/OAEPPadding";
    private static final int SHA256_LENGTH_BYTES = 32;
    private static final OAEPParameterSpec OAEP_PARAMETERS = new OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT);

    /** 工具类禁止实例化。 */
    private RsaCipher() {
    }

    /**
     * 生成生产可用的 RSA 密钥对。
     *
     * @param keySize 密钥位数，只允许 2048、3072 或 4096
     * @return Base64 编码的密钥对
     */
    public static RsaKeyPair generateKeyPair(int keySize) {
        if (keySize != 2048 && keySize != 3072 && keySize != 4096) {
            throw CipherException.invalidParameter("RSA 密钥位数只允许 2048、3072 或 4096");
        }
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(keySize);
            KeyPair pair = generator.generateKeyPair();
            return new RsaKeyPair(
                    Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()),
                    Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
        } catch (GeneralSecurityException exception) {
            throw CipherException.operationFailed("RSA 密钥生成", exception);
        }
    }

    /**
     * 使用 RSA-OAEP-SHA256 公钥加密短文本。
     *
     * @param plainText UTF-8 明文
     * @param base64PublicKey Base64 编码的 X.509 RSA 公钥
     * @return Base64 编码的 RSA 密文
     */
    public static String encrypt(String plainText, String base64PublicKey) {
        CipherSupport.requireNonNull(plainText, "RSA 明文");
        RSAPublicKey publicKey = RsaKeySupport.publicKey(base64PublicKey);
        byte[] plainBytes = plainText.getBytes(StandardCharsets.UTF_8);
        int modulusBytes = (publicKey.getModulus().bitLength() + 7) / Byte.SIZE;
        int maximumPlaintextLength = modulusBytes - (2 * SHA256_LENGTH_BYTES) - 2;
        if (plainBytes.length > maximumPlaintextLength) {
            throw CipherException.invalidParameter(
                    "RSA-OAEP-SHA256 明文不得超过 " + maximumPlaintextLength + " 字节");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, OAEP_PARAMETERS);
            return Base64.getEncoder().encodeToString(cipher.doFinal(plainBytes));
        } catch (GeneralSecurityException exception) {
            throw CipherException.encryptionFailed("RSA-OAEP-SHA256", exception);
        }
    }

    /**
     * 使用 RSA-OAEP-SHA256 私钥解密。
     *
     * @param cipherText Base64 编码的 RSA 密文
     * @param base64PrivateKey Base64 编码的 PKCS#8 RSA 私钥
     * @return UTF-8 明文
     */
    public static String decrypt(String cipherText, String base64PrivateKey) {
        RSAPrivateKey privateKey = RsaKeySupport.privateKey(base64PrivateKey);
        int modulusBytes = (privateKey.getModulus().bitLength() + 7) / Byte.SIZE;
        byte[] cipherBytes = decodeCipherText(cipherText, modulusBytes);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, privateKey, OAEP_PARAMETERS);
            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw CipherException.decryptionFailed("RSA-OAEP-SHA256", exception);
        }
    }

    /**
     * 解码 RSA 密文且不在异常中暴露原值。
     *
     * @param cipherText Base64 编码的 RSA 密文
     * @param expectedLength 当前 RSA 模数对应的密文字节长度
     * @return 固定长度的密文字节
     */
    private static byte[] decodeCipherText(String cipherText, int expectedLength) {
        if (cipherText == null || cipherText.isBlank()) {
            throw CipherException.invalidEnvelope("RSA 密文不能为空");
        }
        int maximumEncodedLength = ((expectedLength + 2) / 3) * 4;
        if (cipherText.length() > maximumEncodedLength) {
            throw CipherException.invalidEnvelope("RSA 密文长度与密钥不匹配");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(cipherText);
            if (decoded.length != expectedLength) {
                throw CipherException.invalidEnvelope("RSA 密文长度与密钥不匹配");
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw CipherException.invalidEnvelope("RSA 密文 Base64 编码不正确");
        }
    }

    /**
     * Base64 编码的 RSA 公私钥对。
     */
    public static final class RsaKeyPair {

        private final String publicKey;
        private final String privateKey;

        /**
         * 创建 RSA 密钥对值对象。
         *
         * @param publicKey Base64 编码的 X.509 公钥
         * @param privateKey Base64 编码的 PKCS#8 私钥
         */
        public RsaKeyPair(String publicKey, String privateKey) {
            this.publicKey = publicKey;
            this.privateKey = privateKey;
        }

        /**
         * 获取公钥。
         *
         * @return Base64 编码的 X.509 公钥
         */
        public String getPublicKey() {
            return publicKey;
        }

        /**
         * 获取私钥。
         *
         * @return Base64 编码的 PKCS#8 私钥
         */
        public String getPrivateKey() {
            return privateKey;
        }
    }
}
