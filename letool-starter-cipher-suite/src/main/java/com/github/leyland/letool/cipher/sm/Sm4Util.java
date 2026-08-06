package com.github.leyland.letool.cipher.sm;

import com.github.leyland.letool.cipher.exception.CipherException;
import com.github.leyland.letool.cipher.support.BouncyCastleSupport;
import com.github.leyland.letool.cipher.support.CipherEnvelope;
import com.github.leyland.letool.cipher.support.CipherSupport;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/**
 * SM4-GCM 认证加密工具。
 */
public final class Sm4Util {

    private static final String ALGORITHM_ID = "SM4_GCM";
    private static final String TRANSFORMATION = "SM4/GCM/NoPadding";
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int TAG_LENGTH_BYTES = TAG_LENGTH_BITS / Byte.SIZE;

    /** 工具类禁止实例化。 */
    private Sm4Util() {
    }

    /**
     * 生成 128 位 SM4 密钥。
     *
     * @return Base64 编码的随机密钥
     */
    public static String generateKey() {
        return Base64.getEncoder().encodeToString(CipherSupport.randomBytes(16));
    }

    /**
     * 使用 SM4-GCM 加密 UTF-8 文本。
     *
     * @param plainText 明文，允许为空字符串
     * @param base64Key Base64 编码的 SM4 密钥
     * @return 版本化认证密文
     */
    public static String encrypt(String plainText, String base64Key) {
        return encrypt(plainText, base64Key, null);
    }

    /**
     * 使用 SM4-GCM 和附加认证数据加密 UTF-8 文本。
     *
     * @param plainText 明文，允许为空字符串
     * @param base64Key Base64 编码的 SM4 密钥
     * @param additionalData 附加认证数据，可为 {@code null}
     * @return 版本化认证密文
     */
    public static String encrypt(String plainText, String base64Key, String additionalData) {
        CipherSupport.requireNonNull(plainText, "SM4 明文");
        byte[] keyBytes = CipherSupport.requireSm4Key(CipherSupport.decodeKey(base64Key, "SM4"));
        byte[] plainBytes = CipherSupport.requireInMemoryPayload(
                plainText.getBytes(StandardCharsets.UTF_8),
                "SM4 明文");
        byte[] nonce = CipherSupport.randomBytes(NONCE_LENGTH);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION, BouncyCastleSupport.provider());
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(keyBytes, "SM4"),
                    new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            cipher.updateAAD(CipherEnvelope.authenticatedData(ALGORITHM_ID, additionalData));
            byte[] cipherText = cipher.doFinal(plainBytes);
            return CipherEnvelope.encode(ALGORITHM_ID, nonce, cipherText);
        } catch (GeneralSecurityException exception) {
            throw CipherException.encryptionFailed("SM4-GCM", exception);
        }
    }

    /**
     * 解密 SM4-GCM 密文。
     *
     * @param envelope 版本化认证密文
     * @param base64Key Base64 编码的 SM4 密钥
     * @return UTF-8 明文
     */
    public static String decrypt(String envelope, String base64Key) {
        return decrypt(envelope, base64Key, null);
    }

    /**
     * 使用相同附加认证数据解密 SM4-GCM 密文。
     *
     * @param envelope 版本化认证密文
     * @param base64Key Base64 编码的 SM4 密钥
     * @param additionalData 加密时使用的附加认证数据，可为 {@code null}
     * @return UTF-8 明文
     */
    public static String decrypt(String envelope, String base64Key, String additionalData) {
        byte[] keyBytes = CipherSupport.requireSm4Key(CipherSupport.decodeKey(base64Key, "SM4"));
        CipherEnvelope.Parsed parsed = CipherEnvelope.parse(
                envelope,
                ALGORITHM_ID,
                NONCE_LENGTH,
                TAG_LENGTH_BYTES,
                CipherSupport.MAXIMUM_IN_MEMORY_PAYLOAD_BYTES + TAG_LENGTH_BYTES);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION, BouncyCastleSupport.provider());
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(keyBytes, "SM4"),
                    new GCMParameterSpec(TAG_LENGTH_BITS, parsed.nonce()));
            cipher.updateAAD(CipherEnvelope.authenticatedData(ALGORITHM_ID, additionalData));
            return new String(cipher.doFinal(parsed.cipherText()), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw CipherException.decryptionFailed("SM4-GCM 认证失败", exception);
        }
    }
}
