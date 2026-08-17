package io.github.leylaragg.letool.cipher.support;

import io.github.leylaragg.letool.cipher.exception.CipherException;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * RSA 密钥解析和强度校验支持。
 *
 * <p>该类公开仅用于模块内部跨包复用，不属于稳定业务 API。</p>
 */
public final class RsaKeySupport {

    /** 生产 API 接受的最小 RSA 密钥位数。 */
    public static final int MINIMUM_KEY_SIZE = 2048;

    /** 工具类禁止实例化。 */
    private RsaKeySupport() {
    }

    /**
     * 解析并校验 RSA 公钥。
     *
     * @param base64PublicKey Base64 编码的 X.509 公钥
     * @return 不小于 2048 位的 RSA 公钥
     */
    public static RSAPublicKey publicKey(String base64PublicKey) {
        byte[] keyBytes = CipherSupport.decodeKey(base64PublicKey, "RSA 公钥");
        try {
            RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(keyBytes));
            requireStrongKey(publicKey.getModulus().bitLength());
            return publicKey;
        } catch (CipherException exception) {
            throw exception;
        } catch (GeneralSecurityException | ClassCastException exception) {
            throw CipherException.invalidKey("RSA 公钥");
        }
    }

    /**
     * 解析并校验 RSA 私钥。
     *
     * @param base64PrivateKey Base64 编码的 PKCS#8 私钥
     * @return 不小于 2048 位的 RSA 私钥
     */
    public static RSAPrivateKey privateKey(String base64PrivateKey) {
        byte[] keyBytes = CipherSupport.decodeKey(base64PrivateKey, "RSA 私钥");
        try {
            RSAPrivateKey privateKey = (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            requireStrongKey(privateKey.getModulus().bitLength());
            return privateKey;
        } catch (CipherException exception) {
            throw exception;
        } catch (GeneralSecurityException | ClassCastException exception) {
            throw CipherException.invalidKey("RSA 私钥");
        }
    }

    /**
     * 校验 RSA 模数位数。
     *
     * @param keySize RSA 模数位数
     */
    private static void requireStrongKey(int keySize) {
        if (keySize < MINIMUM_KEY_SIZE) {
            throw CipherException.invalidKey("RSA 密钥不得小于 2048 位");
        }
    }
}
