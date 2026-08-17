package io.github.leylaragg.letool.cipher.sm;

import io.github.leylaragg.letool.cipher.exception.CipherException;
import io.github.leylaragg.letool.cipher.support.BouncyCastleSupport;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.junit.jupiter.api.Test;

import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * SM2 命名曲线和加解密关键契约测试。
 */
class Sm2UtilTest {

    /**
     * 验证生成的公钥明确使用国家标准 SM2 曲线。
     *
     * @throws Exception 公钥解析失败时由测试框架报告
     */
    @Test
    void shouldGenerateSm2P256V1KeyPair() throws Exception {
        Sm2Util.Sm2KeyPair keyPair = Sm2Util.generateKeyPair();
        ECPublicKey publicKey = (ECPublicKey) KeyFactory
                .getInstance("EC", BouncyCastleSupport.provider())
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(keyPair.getPublicKey())));

        assertEquals(
                ECNamedCurveTable.getParameterSpec("sm2p256v1").getCurve(),
                publicKey.getParameters().getCurve());
    }

    /**
     * 验证 SM2 公钥加密和私钥解密支持 UTF-8 数据。
     */
    @Test
    void shouldRoundTripUtf8Text() {
        Sm2Util.Sm2KeyPair keyPair = Sm2Util.generateKeyPair();

        String encrypted = Sm2Util.encrypt("国密短数据", keyPair.getPublicKey());

        assertEquals("国密短数据", Sm2Util.decrypt(encrypted, keyPair.getPrivateKey()));
    }

    /**
     * 验证非法 SM2 公钥使用稳定密钥错误码。
     */
    @Test
    void shouldRejectInvalidPublicKey() {
        CipherException exception = assertThrows(
                CipherException.class,
                () -> Sm2Util.encrypt("payload", "invalid-key"));

        assertEquals("CIPHER_002", exception.getCode());
    }
}
