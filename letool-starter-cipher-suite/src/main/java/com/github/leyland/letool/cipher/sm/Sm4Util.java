package com.github.leyland.letool.cipher.sm;

import com.github.leyland.letool.cipher.exception.CipherException;
import com.github.leyland.letool.tool.util.Base64Util;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.Security;

/**
 * 国密 SM4 对称加密 —— 类似 AES-128，CBC 模式.
 */
public final class Sm4Util {

    private static final String SM4_CBC = "SM4/CBC/PKCS7Padding";
    private static final int IV_LENGTH = 16;
    private static final int KEY_SIZE = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private Sm4Util() {}

    /**
     * 生成 SM4 密钥（Base64 编码，128 位）.
     */
    public static String generateKey() {
        try {
            KeyGenerator kg = KeyGenerator.getInstance("SM4", BouncyCastleProvider.PROVIDER_NAME);
            kg.init(KEY_SIZE, SECURE_RANDOM);
            return Base64Util.encode(kg.generateKey().getEncoded());
        } catch (Exception e) {
            throw new CipherException("Failed to generate SM4 key", e);
        }
    }

    /**
     * SM4 加密.
     *
     * @param plainText 明文
     * @param base64Key Base64 编码的 SM4 密钥
     * @return Base64 编码的密文（包含 IV）
     */
    public static String encrypt(String plainText, String base64Key) {
        if (plainText == null) return null;
        try {
            byte[] keyBytes = Base64Util.decodeToBytes(base64Key);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "SM4");

            byte[] iv = new byte[IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            Cipher cipher = Cipher.getInstance(SM4_CBC, BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64Util.encode(combined);
        } catch (CipherException e) {
            throw e;
        } catch (Exception e) {
            throw new CipherException("SM4 encrypt failed", e);
        }
    }

    /**
     * SM4 解密.
     *
     * @param cipherText Base64 编码的密文（包含 IV）
     * @param base64Key  Base64 编码的 SM4 密钥
     * @return 明文
     */
    public static String decrypt(String cipherText, String base64Key) {
        if (cipherText == null) return null;
        try {
            byte[] keyBytes = Base64Util.decodeToBytes(base64Key);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "SM4");
            byte[] combined = Base64Util.decodeToBytes(cipherText);

            IvParameterSpec ivSpec = new IvParameterSpec(combined, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance(SM4_CBC, BouncyCastleProvider.PROVIDER_NAME);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] plainBytes = cipher.doFinal(combined, IV_LENGTH, combined.length - IV_LENGTH);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (CipherException e) {
            throw e;
        } catch (Exception e) {
            throw new CipherException("SM4 decrypt failed", e);
        }
    }
}
