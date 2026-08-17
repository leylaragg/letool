package io.github.leylaragg.letool.cipher.symmetric;

import io.github.leylaragg.letool.cipher.exception.CipherException;
import io.github.leylaragg.letool.cipher.support.CipherEnvelope;
import io.github.leylaragg.letool.cipher.support.CipherSupport;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/**
 * AES-GCM 认证加密工具。
 *
 * <p>密文包含稳定版本和算法标识，解密不会猜测或降级到未认证模式。</p>
 */
public final class AesCipher {

    private static final String ALGORITHM_ID = "AES_GCM";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int TAG_LENGTH_BYTES = TAG_LENGTH_BITS / Byte.SIZE;

    /** 工具类禁止实例化。 */
    private AesCipher() {
    }

    /**
     * 生成 AES 密钥。
     *
     * @param keySize 密钥位数，只允许 128、192 或 256
     * @return Base64 编码的密钥
     */
    public static String generateKey(int keySize) {
        if (keySize != 128 && keySize != 192 && keySize != 256) {
            throw CipherException.invalidParameter("AES 密钥位数只允许 128、192 或 256");
        }
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(keySize);
            return Base64.getEncoder().encodeToString(keyGenerator.generateKey().getEncoded());
        } catch (GeneralSecurityException exception) {
            throw CipherException.operationFailed("AES 密钥生成", exception);
        }
    }

    /**
     * 使用 AES-GCM 加密 UTF-8 文本。
     *
     * @param plainText 明文，允许为空字符串
     * @param base64Key Base64 编码的 AES 密钥
     * @return 版本化认证密文
     */
    public static String encrypt(String plainText, String base64Key) {
        return encrypt(plainText, base64Key, (String) null);
    }

    /**
     * 使用 AES-GCM 和附加认证数据加密 UTF-8 文本。
     *
     * @param plainText 明文，允许为空字符串
     * @param base64Key Base64 编码的 AES 密钥
     * @param additionalData 附加认证数据，可为 {@code null}
     * @return 版本化认证密文
     */
    public static String encrypt(String plainText, String base64Key, String additionalData) {
        CipherSupport.requireNonNull(plainText, "AES 明文");
        byte[] keyBytes = CipherSupport.requireAesKey(CipherSupport.decodeKey(base64Key, "AES"));
        byte[] plainBytes = CipherSupport.requireInMemoryPayload(
                plainText.getBytes(StandardCharsets.UTF_8),
                "AES 明文");
        byte[] nonce = CipherSupport.randomBytes(NONCE_LENGTH);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(keyBytes, "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            cipher.updateAAD(CipherEnvelope.authenticatedData(ALGORITHM_ID, additionalData));
            byte[] cipherText = cipher.doFinal(plainBytes);
            return CipherEnvelope.encode(ALGORITHM_ID, nonce, cipherText);
        } catch (GeneralSecurityException exception) {
            throw CipherException.encryptionFailed("AES-GCM", exception);
        }
    }

    /**
     * 解密 AES-GCM 密文。
     *
     * @param envelope 版本化认证密文
     * @param base64Key Base64 编码的 AES 密钥
     * @return UTF-8 明文
     */
    public static String decrypt(String envelope, String base64Key) {
        return decrypt(envelope, base64Key, null);
    }

    /**
     * 使用相同附加认证数据解密 AES-GCM 密文。
     *
     * @param envelope 版本化认证密文
     * @param base64Key Base64 编码的 AES 密钥
     * @param additionalData 加密时使用的附加认证数据，可为 {@code null}
     * @return UTF-8 明文
     */
    public static String decrypt(String envelope, String base64Key, String additionalData) {
        byte[] keyBytes = CipherSupport.requireAesKey(CipherSupport.decodeKey(base64Key, "AES"));
        CipherEnvelope.Parsed parsed = CipherEnvelope.parse(
                envelope,
                ALGORITHM_ID,
                NONCE_LENGTH,
                TAG_LENGTH_BYTES,
                CipherSupport.MAXIMUM_IN_MEMORY_PAYLOAD_BYTES + TAG_LENGTH_BYTES);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(keyBytes, "AES"),
                    new GCMParameterSpec(TAG_LENGTH_BITS, parsed.nonce()));
            cipher.updateAAD(CipherEnvelope.authenticatedData(ALGORITHM_ID, additionalData));
            return new String(cipher.doFinal(parsed.cipherText()), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw CipherException.decryptionFailed("AES-GCM 认证失败", exception);
        }
    }
}
