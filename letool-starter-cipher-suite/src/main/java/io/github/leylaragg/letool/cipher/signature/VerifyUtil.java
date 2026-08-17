package io.github.leylaragg.letool.cipher.signature;

import io.github.leylaragg.letool.cipher.exception.CipherException;
import io.github.leylaragg.letool.cipher.support.CipherSupport;
import io.github.leylaragg.letool.cipher.support.RsaKeySupport;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Base64;

/**
 * RSA-PSS-SHA256 签名验证工具。
 */
public final class VerifyUtil {

    private static final PSSParameterSpec PSS_PARAMETERS = new PSSParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            32,
            1);

    /** 工具类禁止实例化。 */
    private VerifyUtil() {
    }

    /**
     * 使用 RSA-PSS-SHA256 验证 UTF-8 数据签名。
     *
     * @param data 原始数据
     * @param base64Signature Base64 编码的签名
     * @param base64PublicKey Base64 编码的 X.509 RSA 公钥
     * @return 签名匹配时返回 {@code true}，数据或签名被修改时返回 {@code false}
     */
    public static boolean verify(String data, String base64Signature, String base64PublicKey) {
        CipherSupport.requireNonNull(data, "验签数据");
        RSAPublicKey publicKey = RsaKeySupport.publicKey(base64PublicKey);
        int expectedLength = (publicKey.getModulus().bitLength() + 7) / Byte.SIZE;
        byte[] signatureBytes = decodeSignature(base64Signature, expectedLength);
        try {
            Signature signature = Signature.getInstance("RSASSA-PSS");
            signature.setParameter(PSS_PARAMETERS);
            signature.initVerify(publicKey);
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            return signature.verify(signatureBytes);
        } catch (CipherException exception) {
            throw exception;
        } catch (GeneralSecurityException exception) {
            throw CipherException.operationFailed("RSA-PSS-SHA256 验签", exception);
        }
    }

    /**
     * 解码签名且不在异常中暴露原值。
     *
     * @param base64Signature Base64 编码的签名
     * @param expectedLength 当前 RSA 模数对应的签名字节长度
     * @return 固定长度的签名字节
     */
    private static byte[] decodeSignature(String base64Signature, int expectedLength) {
        if (base64Signature == null || base64Signature.isBlank()) {
            throw CipherException.invalidParameter("签名不能为空");
        }
        int maximumEncodedLength = ((expectedLength + 2) / 3) * 4;
        if (base64Signature.length() > maximumEncodedLength) {
            throw CipherException.invalidParameter("签名长度与 RSA 密钥不匹配");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Signature);
            if (decoded.length != expectedLength) {
                throw CipherException.invalidParameter("签名长度与 RSA 密钥不匹配");
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw CipherException.invalidParameter("签名 Base64 编码不正确");
        }
    }
}
