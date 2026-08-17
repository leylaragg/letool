package io.github.leylaragg.letool.cipher.signature;

import io.github.leylaragg.letool.cipher.exception.CipherException;
import io.github.leylaragg.letool.cipher.support.CipherSupport;
import io.github.leylaragg.letool.cipher.support.RsaKeySupport;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Base64;

/**
 * RSA-PSS-SHA256 数字签名工具。
 */
public final class SignUtil {

    private static final PSSParameterSpec PSS_PARAMETERS = new PSSParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            32,
            1);

    /** 工具类禁止实例化。 */
    private SignUtil() {
    }

    /**
     * 使用 RSA-PSS-SHA256 对 UTF-8 数据签名。
     *
     * @param data 待签名数据
     * @param base64PrivateKey Base64 编码的 PKCS#8 RSA 私钥
     * @return Base64 编码的签名
     */
    public static String sign(String data, String base64PrivateKey) {
        CipherSupport.requireNonNull(data, "待签名数据");
        try {
            Signature signature = Signature.getInstance("RSASSA-PSS");
            signature.setParameter(PSS_PARAMETERS);
            signature.initSign(RsaKeySupport.privateKey(base64PrivateKey));
            signature.update(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (CipherException exception) {
            throw exception;
        } catch (GeneralSecurityException exception) {
            throw CipherException.operationFailed("RSA-PSS-SHA256 签名", exception);
        }
    }

}
