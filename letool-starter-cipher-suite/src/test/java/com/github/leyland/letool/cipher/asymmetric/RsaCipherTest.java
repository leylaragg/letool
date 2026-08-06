package com.github.leyland.letool.cipher.asymmetric;

import com.github.leyland.letool.cipher.exception.CipherException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * RSA-OAEP 安全契约测试。
 */
class RsaCipherTest {

    private static RsaCipher.RsaKeyPair keyPair;

    /**
     * 生成测试使用的 2048 位 RSA 密钥对。
     */
    @BeforeAll
    static void setUpKeyPair() {
        keyPair = RsaCipher.generateKeyPair(2048);
    }

    /**
     * 验证 OAEP-SHA256 可以处理 UTF-8 小数据往返。
     */
    @Test
    void shouldRoundTripWithOaepSha256() {
        String encrypted = RsaCipher.encrypt("需要封装的会话密钥", keyPair.getPublicKey());

        assertEquals("需要封装的会话密钥", RsaCipher.decrypt(encrypted, keyPair.getPrivateKey()));
    }

    /**
     * 验证生产 API 拒绝不足 2048 位的 RSA 密钥。
     */
    @Test
    void shouldRejectWeakKeySize() {
        CipherException exception = assertThrows(
                CipherException.class,
                () -> RsaCipher.generateKeyPair(1024));

        assertEquals("CIPHER_001", exception.getCode());
    }

    /**
     * 验证 2048 位 OAEP-SHA256 单块明文上限为 190 字节。
     */
    @Test
    void shouldRejectPlaintextBeyondSingleBlockLimit() {
        String oversized = "a".repeat(191);

        CipherException exception = assertThrows(
                CipherException.class,
                () -> RsaCipher.encrypt(oversized, keyPair.getPublicKey()));

        assertEquals("CIPHER_001", exception.getCode());
    }

    /**
     * 验证非法公钥不会泄露底层解析细节。
     */
    @Test
    void shouldRejectInvalidPublicKey() {
        CipherException exception = assertThrows(
                CipherException.class,
                () -> RsaCipher.encrypt("payload", "not-a-key"));

        assertEquals("CIPHER_002", exception.getCode());
    }

    /**
     * 验证模块生成的密文可由显式指定 MGF1-SHA256 的独立 JCA 实现解密。
     *
     * @throws Exception JCA 互操作初始化失败时由测试框架报告
     */
    @Test
    void shouldInteroperateWithExplicitJcaOaepParameters() throws Exception {
        String encrypted = RsaCipher.encrypt("session-key", keyPair.getPublicKey());
        OAEPParameterSpec parameters = new OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA256,
                PSource.PSpecified.DEFAULT);
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
        cipher.init(
                Cipher.DECRYPT_MODE,
                KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(
                        Base64.getDecoder().decode(keyPair.getPrivateKey()))),
                parameters);

        assertEquals("session-key", new String(
                cipher.doFinal(Base64.getDecoder().decode(encrypted)),
                StandardCharsets.UTF_8));
    }
}
