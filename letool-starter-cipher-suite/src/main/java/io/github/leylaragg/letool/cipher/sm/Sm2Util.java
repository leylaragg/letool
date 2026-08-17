package io.github.leylaragg.letool.cipher.sm;

import io.github.leylaragg.letool.cipher.exception.CipherException;
import io.github.leylaragg.letool.cipher.support.BouncyCastleSupport;
import io.github.leylaragg.letool.cipher.support.CipherSupport;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.interfaces.ECKey;
import org.bouncycastle.jce.spec.ECParameterSpec;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * 基于 {@code sm2p256v1} 命名曲线的 SM2 公钥加解密工具。
 */
public final class Sm2Util {

    private static final String CURVE_NAME = "sm2p256v1";
    private static final String TRANSFORMATION = "SM2";
    private static final int MAXIMUM_CIPHER_OVERHEAD_BYTES = 512;
    private static final int MAXIMUM_CIPHER_BYTES =
            CipherSupport.MAXIMUM_IN_MEMORY_PAYLOAD_BYTES + MAXIMUM_CIPHER_OVERHEAD_BYTES;
    private static final int MAXIMUM_CIPHER_BASE64_CHARACTERS =
            ((MAXIMUM_CIPHER_BYTES + 2) / 3) * 4;

    /** 工具类禁止实例化。 */
    private Sm2Util() {
    }

    /**
     * 生成 SM2 标准曲线密钥对。
     *
     * @return Base64 编码的 X.509 公钥和 PKCS#8 私钥
     */
    public static Sm2KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(
                    "EC",
                    BouncyCastleSupport.provider());
            generator.initialize(new ECGenParameterSpec(CURVE_NAME));
            KeyPair pair = generator.generateKeyPair();
            return new Sm2KeyPair(
                    Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()),
                    Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
        } catch (GeneralSecurityException exception) {
            throw CipherException.operationFailed("SM2 密钥生成", exception);
        }
    }

    /**
     * 使用 SM2 公钥加密 UTF-8 短数据。
     *
     * @param plainText 明文，允许为空字符串
     * @param base64PublicKey Base64 编码的 X.509 SM2 公钥
     * @return Base64 编码的 SM2 密文
     */
    public static String encrypt(String plainText, String base64PublicKey) {
        CipherSupport.requireNonNull(plainText, "SM2 明文");
        PublicKey publicKey = publicKey(base64PublicKey);
        byte[] plainBytes = CipherSupport.requireInMemoryPayload(
                plainText.getBytes(StandardCharsets.UTF_8),
                "SM2 明文");
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION, BouncyCastleSupport.provider());
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            return Base64.getEncoder().encodeToString(cipher.doFinal(plainBytes));
        } catch (GeneralSecurityException exception) {
            throw CipherException.encryptionFailed("SM2", exception);
        }
    }

    /**
     * 使用 SM2 私钥解密。
     *
     * @param cipherText Base64 编码的 SM2 密文
     * @param base64PrivateKey Base64 编码的 PKCS#8 SM2 私钥
     * @return UTF-8 明文
     */
    public static String decrypt(String cipherText, String base64PrivateKey) {
        PrivateKey privateKey = privateKey(base64PrivateKey);
        byte[] cipherBytes = decodeCipherText(cipherText);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION, BouncyCastleSupport.provider());
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw CipherException.decryptionFailed("SM2", exception);
        }
    }

    /**
     * 解析并校验 SM2 公钥。
     *
     * @param base64PublicKey Base64 编码的 X.509 公钥
     * @return SM2 公钥
     */
    private static PublicKey publicKey(String base64PublicKey) {
        byte[] keyBytes = CipherSupport.decodeKey(base64PublicKey, "SM2 公钥");
        try {
            PublicKey publicKey = KeyFactory.getInstance("EC", BouncyCastleSupport.provider())
                    .generatePublic(new X509EncodedKeySpec(keyBytes));
            requireSm2Curve(publicKey, "SM2 公钥");
            return publicKey;
        } catch (CipherException exception) {
            throw exception;
        } catch (GeneralSecurityException exception) {
            throw CipherException.invalidKey("SM2 公钥");
        }
    }

    /**
     * 解析并校验 SM2 私钥。
     *
     * @param base64PrivateKey Base64 编码的 PKCS#8 私钥
     * @return SM2 私钥
     */
    private static PrivateKey privateKey(String base64PrivateKey) {
        byte[] keyBytes = CipherSupport.decodeKey(base64PrivateKey, "SM2 私钥");
        try {
            PrivateKey privateKey = KeyFactory.getInstance("EC", BouncyCastleSupport.provider())
                    .generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            requireSm2Curve(privateKey, "SM2 私钥");
            return privateKey;
        } catch (CipherException exception) {
            throw exception;
        } catch (GeneralSecurityException exception) {
            throw CipherException.invalidKey("SM2 私钥");
        }
    }

    /**
     * 校验 EC 密钥参数与 SM2 标准曲线一致。
     *
     * @param key EC 密钥
     * @param keyType 密钥类型说明
     */
    private static void requireSm2Curve(Key key, String keyType) {
        if (!(key instanceof ECKey ecKey)) {
            throw CipherException.invalidKey(keyType);
        }
        ECParameterSpec actual = ecKey.getParameters();
        ECParameterSpec expected = ECNamedCurveTable.getParameterSpec(CURVE_NAME);
        if (actual == null
                || !expected.getCurve().equals(actual.getCurve())
                || !expected.getG().equals(actual.getG())
                || !expected.getN().equals(actual.getN())
                || !expected.getH().equals(actual.getH())) {
            throw CipherException.invalidKey(keyType + "必须使用 sm2p256v1 曲线");
        }
    }

    /**
     * 解码 SM2 密文且不在异常中暴露原值。
     *
     * @param cipherText Base64 编码的 SM2 密文
     * @return 密文字节
     */
    private static byte[] decodeCipherText(String cipherText) {
        if (cipherText == null || cipherText.isBlank()) {
            throw CipherException.invalidEnvelope("SM2 密文不能为空");
        }
        if (cipherText.length() > MAXIMUM_CIPHER_BASE64_CHARACTERS) {
            throw CipherException.invalidEnvelope("SM2 密文超过内存处理上限");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(cipherText);
            if (decoded.length > MAXIMUM_CIPHER_BYTES) {
                throw CipherException.invalidEnvelope("SM2 密文超过内存处理上限");
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw CipherException.invalidEnvelope("SM2 密文 Base64 编码不正确");
        }
    }

    /**
     * Base64 编码的 SM2 公私钥对。
     */
    public static final class Sm2KeyPair {

        private final String publicKey;
        private final String privateKey;

        /**
         * 创建 SM2 密钥对值对象。
         *
         * @param publicKey Base64 编码的 X.509 公钥
         * @param privateKey Base64 编码的 PKCS#8 私钥
         */
        public Sm2KeyPair(String publicKey, String privateKey) {
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
