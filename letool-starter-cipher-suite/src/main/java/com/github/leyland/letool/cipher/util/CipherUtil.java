package com.github.leyland.letool.cipher.util;

import com.github.leyland.letool.cipher.asymmetric.RsaCipher;
import com.github.leyland.letool.cipher.hash.Md5Util;
import com.github.leyland.letool.cipher.hash.ShaUtil;
import com.github.leyland.letool.cipher.hmac.HmacUtil;
import com.github.leyland.letool.cipher.key.KeyGenerator;
import com.github.leyland.letool.cipher.signature.SignUtil;
import com.github.leyland.letool.cipher.signature.VerifyUtil;
import com.github.leyland.letool.cipher.sm.Sm2Util;
import com.github.leyland.letool.cipher.sm.Sm3Util;
import com.github.leyland.letool.cipher.sm.Sm4Util;
import com.github.leyland.letool.cipher.symmetric.AesCipher;

/**
 * 加密套件统一静态门面。
 *
 * <p>门面只暴露经过约束的安全默认值：AES-GCM、SM4-GCM、RSA-OAEP-SHA256、
 * RSA-PSS-SHA256、HMAC-SHA2、SHA-2、SM2 和 SM3。密钥托管、密码存储和证书
 * 生命周期不属于本工具职责。</p>
 */
public final class CipherUtil {

    /** 工具类禁止实例化。 */
    private CipherUtil() {
    }

    /**
     * 使用 AES-GCM 加密文本。
     *
     * @param plainText 明文
     * @param base64Key Base64 编码的 AES 密钥
     * @return 版本化认证密文
     */
    public static String aesEncrypt(String plainText, String base64Key) {
        return AesCipher.encrypt(plainText, base64Key);
    }

    /**
     * 使用 AES-GCM 和附加认证数据加密文本。
     *
     * @param plainText 明文
     * @param base64Key Base64 编码的 AES 密钥
     * @param additionalData 附加认证数据，可为 {@code null}
     * @return 版本化认证密文
     */
    public static String aesEncrypt(String plainText, String base64Key, String additionalData) {
        return AesCipher.encrypt(plainText, base64Key, additionalData);
    }

    /**
     * 解密 AES-GCM 密文。
     *
     * @param cipherText 版本化认证密文
     * @param base64Key Base64 编码的 AES 密钥
     * @return 明文
     */
    public static String aesDecrypt(String cipherText, String base64Key) {
        return AesCipher.decrypt(cipherText, base64Key);
    }

    /**
     * 使用相同附加认证数据解密 AES-GCM 密文。
     *
     * @param cipherText 版本化认证密文
     * @param base64Key Base64 编码的 AES 密钥
     * @param additionalData 加密时使用的附加认证数据，可为 {@code null}
     * @return 明文
     */
    public static String aesDecrypt(String cipherText, String base64Key, String additionalData) {
        return AesCipher.decrypt(cipherText, base64Key, additionalData);
    }

    /**
     * 使用 RSA-OAEP-SHA256 加密短文本。
     *
     * @param plainText 明文
     * @param base64PublicKey Base64 编码的 RSA 公钥
     * @return Base64 编码的 RSA 密文
     */
    public static String rsaEncrypt(String plainText, String base64PublicKey) {
        return RsaCipher.encrypt(plainText, base64PublicKey);
    }

    /**
     * 使用 RSA-OAEP-SHA256 解密短文本。
     *
     * @param cipherText Base64 编码的 RSA 密文
     * @param base64PrivateKey Base64 编码的 RSA 私钥
     * @return 明文
     */
    public static String rsaDecrypt(String cipherText, String base64PrivateKey) {
        return RsaCipher.decrypt(cipherText, base64PrivateKey);
    }

    /**
     * 计算 MD5 遗留校验和。
     *
     * @param input 输入文本
     * @return 小写十六进制校验和
     * @deprecated MD5 不适合安全完整性、签名或密码存储，仅保留给遗留非安全协议
     */
    @Deprecated(forRemoval = false)
    public static String md5(String input) {
        return Md5Util.md5(input);
    }

    /**
     * 计算 SHA-256 摘要。
     *
     * @param input 输入文本
     * @return 小写十六进制摘要
     */
    public static String sha256(String input) {
        return ShaUtil.sha256(input);
    }

    /**
     * 计算 SHA-512 摘要。
     *
     * @param input 输入文本
     * @return 小写十六进制摘要
     */
    public static String sha512(String input) {
        return ShaUtil.sha512(input);
    }

    /**
     * 计算十六进制 HMAC-SHA256。
     *
     * @param data 待认证数据
     * @param base64Key Base64 编码的 HMAC 密钥
     * @return 小写十六进制消息认证码
     */
    public static String hmacSha256(String data, String base64Key) {
        return HmacUtil.hmacSha256(data, base64Key);
    }

    /**
     * 计算 Base64 HMAC-SHA256。
     *
     * @param data 待认证数据
     * @param base64Key Base64 编码的 HMAC 密钥
     * @return Base64 消息认证码
     */
    public static String hmacSha256Base64(String data, String base64Key) {
        return HmacUtil.hmacSha256Base64(data, base64Key);
    }

    /**
     * 计算十六进制 HMAC-SHA512。
     *
     * @param data 待认证数据
     * @param base64Key Base64 编码的 HMAC 密钥
     * @return 小写十六进制消息认证码
     */
    public static String hmacSha512(String data, String base64Key) {
        return HmacUtil.hmacSha512(data, base64Key);
    }

    /**
     * 计算 Base64 HMAC-SHA512。
     *
     * @param data 待认证数据
     * @param base64Key Base64 编码的 HMAC 密钥
     * @return Base64 消息认证码
     */
    public static String hmacSha512Base64(String data, String base64Key) {
        return HmacUtil.hmacSha512Base64(data, base64Key);
    }

    /**
     * 使用常量时间比较验证十六进制 HMAC-SHA256。
     *
     * @param data 原始数据
     * @param expectedHex 预期十六进制消息认证码
     * @param base64Key Base64 编码的 HMAC 密钥
     * @return 消息认证码匹配时返回 {@code true}
     */
    public static boolean verifyHmacSha256(String data, String expectedHex, String base64Key) {
        return HmacUtil.verifySha256(data, expectedHex, base64Key);
    }

    /**
     * 使用常量时间比较验证 Base64 HMAC-SHA256。
     *
     * @param data 原始数据
     * @param expectedBase64 预期 Base64 消息认证码
     * @param base64Key Base64 编码的 HMAC 密钥
     * @return 消息认证码匹配时返回 {@code true}
     */
    public static boolean verifyHmacSha256Base64(
            String data,
            String expectedBase64,
            String base64Key) {
        return HmacUtil.verifySha256Base64(data, expectedBase64, base64Key);
    }

    /**
     * 使用 RSA-PSS-SHA256 签名。
     *
     * @param data 待签名数据
     * @param base64PrivateKey Base64 编码的 RSA 私钥
     * @return Base64 编码的签名
     */
    public static String sign(String data, String base64PrivateKey) {
        return SignUtil.sign(data, base64PrivateKey);
    }

    /**
     * 使用 RSA-PSS-SHA256 验签。
     *
     * @param data 原始数据
     * @param base64Signature Base64 编码的签名
     * @param base64PublicKey Base64 编码的 RSA 公钥
     * @return 签名有效时返回 {@code true}
     */
    public static boolean verify(String data, String base64Signature, String base64PublicKey) {
        return VerifyUtil.verify(data, base64Signature, base64PublicKey);
    }

    /**
     * 生成 AES 密钥。
     *
     * @param keySize 密钥位数，只允许 128、192 或 256
     * @return Base64 编码的 AES 密钥
     */
    public static String generateAesKey(int keySize) {
        return KeyGenerator.generateAesKey(keySize);
    }

    /**
     * 生成 RSA 密钥对。
     *
     * @param keySize 密钥位数，只允许 2048、3072 或 4096
     * @return Base64 编码的 RSA 密钥对
     */
    public static RsaCipher.RsaKeyPair generateRsaKeyPair(int keySize) {
        return KeyGenerator.generateRsaKeyPair(keySize);
    }

    /**
     * 生成 256 位 HMAC 密钥。
     *
     * @return 可直接传给 HMAC 字符串 API 的 Base64 密钥
     */
    public static String generateHmacKey() {
        return KeyGenerator.generateHmacKey();
    }

    /**
     * 计算 SM3 摘要。
     *
     * @param input 输入文本
     * @return 小写十六进制摘要
     */
    public static String sm3(String input) {
        return Sm3Util.sm3(input);
    }

    /**
     * 生成 128 位 SM4 密钥。
     *
     * @return Base64 编码的 SM4 密钥
     */
    public static String generateSm4Key() {
        return Sm4Util.generateKey();
    }

    /**
     * 使用 SM4-GCM 加密文本。
     *
     * @param plainText 明文
     * @param base64Key Base64 编码的 SM4 密钥
     * @return 版本化认证密文
     */
    public static String sm4Encrypt(String plainText, String base64Key) {
        return Sm4Util.encrypt(plainText, base64Key);
    }

    /**
     * 使用 SM4-GCM 和附加认证数据加密文本。
     *
     * @param plainText 明文
     * @param base64Key Base64 编码的 SM4 密钥
     * @param additionalData 附加认证数据，可为 {@code null}
     * @return 版本化认证密文
     */
    public static String sm4Encrypt(String plainText, String base64Key, String additionalData) {
        return Sm4Util.encrypt(plainText, base64Key, additionalData);
    }

    /**
     * 解密 SM4-GCM 密文。
     *
     * @param cipherText 版本化认证密文
     * @param base64Key Base64 编码的 SM4 密钥
     * @return 明文
     */
    public static String sm4Decrypt(String cipherText, String base64Key) {
        return Sm4Util.decrypt(cipherText, base64Key);
    }

    /**
     * 使用相同附加认证数据解密 SM4-GCM 密文。
     *
     * @param cipherText 版本化认证密文
     * @param base64Key Base64 编码的 SM4 密钥
     * @param additionalData 加密时使用的附加认证数据，可为 {@code null}
     * @return 明文
     */
    public static String sm4Decrypt(String cipherText, String base64Key, String additionalData) {
        return Sm4Util.decrypt(cipherText, base64Key, additionalData);
    }

    /**
     * 生成 SM2 标准曲线密钥对。
     *
     * @return Base64 编码的 SM2 密钥对
     */
    public static Sm2Util.Sm2KeyPair generateSm2KeyPair() {
        return Sm2Util.generateKeyPair();
    }

    /**
     * 使用 SM2 公钥加密短文本。
     *
     * @param plainText 明文
     * @param base64PublicKey Base64 编码的 SM2 公钥
     * @return Base64 编码的 SM2 密文
     */
    public static String sm2Encrypt(String plainText, String base64PublicKey) {
        return Sm2Util.encrypt(plainText, base64PublicKey);
    }

    /**
     * 使用 SM2 私钥解密短文本。
     *
     * @param cipherText Base64 编码的 SM2 密文
     * @param base64PrivateKey Base64 编码的 SM2 私钥
     * @return 明文
     */
    public static String sm2Decrypt(String cipherText, String base64PrivateKey) {
        return Sm2Util.decrypt(cipherText, base64PrivateKey);
    }
}
