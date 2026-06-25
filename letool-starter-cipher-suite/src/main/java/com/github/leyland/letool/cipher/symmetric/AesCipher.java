package com.github.leyland.letool.cipher.symmetric;

import com.github.leyland.letool.cipher.exception.CipherException;
import com.github.leyland.letool.cipher.model.CipherMode;
import com.github.leyland.letool.tool.util.Base64Util;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;

/**
 * AES 加密 —— 支持 GCM（默认，认证加密）和 CBC 模式.
 *
 * <p>GCM 模式输出格式：Base64(IV[12字节] + 密文)，CBC 模式输出格式：Base64(IV[16字节] + 密文).</p>
 */
public final class AesCipher {

    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final String AES_CBC = "AES/CBC/PKCS5Padding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int CBC_IV_LENGTH = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private AesCipher() {}

    /**
     * 生成 AES 密钥（Base64 编码）.
     *
     * @param keySize 密钥大小（128 / 192 / 256）
     * @return Base64 编码的密钥字符串
     */
    public static String generateKey(int keySize) {
        try {
            KeyGenerator kg = KeyGenerator.getInstance("AES");
            kg.init(keySize, SECURE_RANDOM);
            return Base64Util.encode(kg.generateKey().getEncoded());
        } catch (Exception e) {
            throw new CipherException("Failed to generate AES key", e);
        }
    }

    /**
     * AES 加密（默认 GCM 模式）.
     *
     * @param plainText 明文
     * @param base64Key Base64 编码的 AES 密钥
     * @return Base64 编码的密文（包含 IV）
     */
    public static String encrypt(String plainText, String base64Key) {
        return encrypt(plainText, base64Key, CipherMode.GCM);
    }

    /**
     * AES 加密（指定模式）.
     *
     * @param plainText 明文
     * @param base64Key Base64 编码的 AES 密钥
     * @param mode      加密模式
     * @return Base64 编码的密文（包含 IV）
     */
    public static String encrypt(String plainText, String base64Key, CipherMode mode) {
        if (plainText == null) return null;
        try {
            byte[] keyBytes = Base64Util.decodeToBytes(base64Key);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

            byte[] iv;
            AlgorithmParameterSpec paramSpec;

            if (mode == CipherMode.GCM) {
                iv = new byte[GCM_IV_LENGTH];
                SECURE_RANDOM.nextBytes(iv);
                paramSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            } else {
                iv = new byte[CBC_IV_LENGTH];
                SECURE_RANDOM.nextBytes(iv);
                paramSpec = new IvParameterSpec(iv);
            }

            String algorithm = mode == CipherMode.GCM ? AES_GCM : AES_CBC;
            Cipher cipher = Cipher.getInstance(algorithm);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, paramSpec);
            byte[] cipherText = cipher.doFinal(plainText.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // 拼接 IV + 密文
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            return Base64Util.encode(combined);
        } catch (CipherException e) {
            throw e;
        } catch (Exception e) {
            throw new CipherException("AES encrypt failed", e);
        }
    }

    /**
     * AES 解密（自动检测 GCM/CBC 模式，从密文中提取 IV）.
     *
     * @param cipherText Base64 编码的密文（包含 IV）
     * @param base64Key  Base64 编码的 AES 密钥
     * @return 明文
     */
    public static String decrypt(String cipherText, String base64Key) {
        if (cipherText == null) return null;
        try {
            byte[] keyBytes = Base64Util.decodeToBytes(base64Key);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            byte[] combined = Base64Util.decodeToBytes(cipherText);

            // GCM: IV=12字节，CBC: IV=16字节。根据密文长度和常见模式优先尝试
            // 这里默认按 GCM 处理（12 字节 IV），如果失败再试 CBC
            try {
                return doDecrypt(combined, keySpec, AES_GCM, GCM_IV_LENGTH,
                        new GCMParameterSpec(GCM_TAG_LENGTH, combined, 0, GCM_IV_LENGTH));
            } catch (Exception gcmError) {
                try {
                    return doDecrypt(combined, keySpec, AES_CBC, CBC_IV_LENGTH,
                            new IvParameterSpec(combined, 0, CBC_IV_LENGTH));
                } catch (Exception cbcError) {
                    throw new CipherException("AES decrypt failed (tried GCM and CBC)", gcmError);
                }
            }
        } catch (CipherException e) {
            throw e;
        } catch (Exception e) {
            throw new CipherException("AES decrypt failed", e);
        }
    }

    private static String doDecrypt(byte[] combined, SecretKeySpec keySpec,
                                    String algorithm, int ivLength, AlgorithmParameterSpec paramSpec) throws Exception {
        Cipher cipher = Cipher.getInstance(algorithm);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, paramSpec);
        byte[] plainBytes = cipher.doFinal(combined, ivLength, combined.length - ivLength);
        return new String(plainBytes, java.nio.charset.StandardCharsets.UTF_8);
    }
}
