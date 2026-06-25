package com.github.leyland.letool.cipher.util;

import com.github.leyland.letool.cipher.asymmetric.RsaCipher;
import com.github.leyland.letool.cipher.hash.Md5Util;
import com.github.leyland.letool.cipher.hash.ShaUtil;
import com.github.leyland.letool.cipher.hmac.HmacUtil;
import com.github.leyland.letool.cipher.key.KeyGenerator;
import com.github.leyland.letool.cipher.model.CipherMode;
import com.github.leyland.letool.cipher.signature.SignUtil;
import com.github.leyland.letool.cipher.signature.VerifyUtil;
import com.github.leyland.letool.cipher.sm.Sm2Util;
import com.github.leyland.letool.cipher.sm.Sm3Util;
import com.github.leyland.letool.cipher.sm.Sm4Util;
import com.github.leyland.letool.cipher.symmetric.AesCipher;

/**
 * 加密工具统一入口 —— 所有加解密操作的静态方法入口.
 *
 * <pre>{@code
 * // AES 对称加密
 * String key = CipherUtil.generateAesKey(256);
 * String enc = CipherUtil.aesEncrypt("Hello", key);
 * String dec = CipherUtil.aesDecrypt(enc, key);
 *
 * // RSA 非对称加密
 * RsaCipher.RsaKeyPair pair = CipherUtil.generateRsaKeyPair(2048);
 * String rsaEnc = CipherUtil.rsaEncrypt("Hello", pair.getPublicKey());
 * String rsaDec = CipherUtil.rsaDecrypt(rsaEnc, pair.getPrivateKey());
 *
 * // 哈希
 * String md5 = CipherUtil.md5("hello");
 * String sha256 = CipherUtil.sha256("hello");
 *
 * // HMAC
 * String hmac = CipherUtil.hmacSha256("data", "secret-key");
 *
 * // 数字签名
 * String sig = CipherUtil.sign("data", privateKey);
 * boolean valid = CipherUtil.verify("data", sig, publicKey);
 *
 * // 国密
 * String sm3 = CipherUtil.sm3("hello");
 * String sm4key = CipherUtil.generateSm4Key();
 * String sm4Enc = CipherUtil.sm4Encrypt("Hello", sm4key);
 * Sm2Util.Sm2KeyPair sm2Pair = CipherUtil.generateSm2KeyPair();
 * }</pre>
 */
public final class CipherUtil {

    private CipherUtil() {}

    // ======================== AES ========================

    /** AES 加密（默认 GCM 模式） */
    public static String aesEncrypt(String plainText, String base64Key) {
        return AesCipher.encrypt(plainText, base64Key);
    }

    /** AES 加密（指定模式） */
    public static String aesEncrypt(String plainText, String base64Key, CipherMode mode) {
        return AesCipher.encrypt(plainText, base64Key, mode);
    }

    /** AES 解密 */
    public static String aesDecrypt(String cipherText, String base64Key) {
        return AesCipher.decrypt(cipherText, base64Key);
    }

    // ======================== RSA ========================

    /** RSA 公钥加密 */
    public static String rsaEncrypt(String plainText, String base64PublicKey) {
        return RsaCipher.encrypt(plainText, base64PublicKey);
    }

    /** RSA 私钥解密 */
    public static String rsaDecrypt(String cipherText, String base64PrivateKey) {
        return RsaCipher.decrypt(cipherText, base64PrivateKey);
    }

    // ======================== 哈希 ========================

    /** MD5 哈希 */
    public static String md5(String input) {
        return Md5Util.md5(input);
    }

    /** SHA-256 哈希 */
    public static String sha256(String input) {
        return ShaUtil.sha256(input);
    }

    /** SHA-512 哈希 */
    public static String sha512(String input) {
        return ShaUtil.sha512(input);
    }

    // ======================== HMAC ========================

    /** HMAC-SHA256（返回十六进制） */
    public static String hmacSha256(String data, String key) {
        return HmacUtil.hmacSha256(data, key);
    }

    /** HMAC-SHA256（返回 Base64） */
    public static String hmacSha256Base64(String data, String key) {
        return HmacUtil.hmacSha256Base64(data, key);
    }

    /** HMAC-SHA512（返回十六进制） */
    public static String hmacSha512(String data, String key) {
        return HmacUtil.hmacSha512(data, key);
    }

    // ======================== 数字签名 ========================

    /** 使用 RSA 私钥签名 */
    public static String sign(String data, String base64PrivateKey) {
        return SignUtil.sign(data, base64PrivateKey);
    }

    /** 使用私钥签名（指定算法） */
    public static String sign(String data, String base64PrivateKey, String algorithm) {
        return SignUtil.sign(data, base64PrivateKey, algorithm);
    }

    /** 使用 RSA 公钥验签 */
    public static boolean verify(String data, String base64Signature, String base64PublicKey) {
        return VerifyUtil.verify(data, base64Signature, base64PublicKey);
    }

    /** 使用公钥验签（指定算法） */
    public static boolean verify(String data, String base64Signature, String base64PublicKey, String algorithm) {
        return VerifyUtil.verify(data, base64Signature, base64PublicKey, algorithm);
    }

    // ======================== 密钥生成 ========================

    /** 生成 AES 密钥 */
    public static String generateAesKey(int keySize) {
        return KeyGenerator.generateAesKey(keySize);
    }

    /** 生成 RSA 密钥对 */
    public static RsaCipher.RsaKeyPair generateRsaKeyPair(int keySize) {
        return KeyGenerator.generateRsaKeyPair(keySize);
    }

    /** 生成 HMAC 密钥 */
    public static String generateHmacKey() {
        return KeyGenerator.generateHmacKey();
    }

    // ======================== 国密算法 ========================

    /** SM3 哈希 */
    public static String sm3(String input) {
        return Sm3Util.sm3(input);
    }

    /** 生成 SM4 密钥 */
    public static String generateSm4Key() {
        return Sm4Util.generateKey();
    }

    /** SM4 加密 */
    public static String sm4Encrypt(String plainText, String base64Key) {
        return Sm4Util.encrypt(plainText, base64Key);
    }

    /** SM4 解密 */
    public static String sm4Decrypt(String cipherText, String base64Key) {
        return Sm4Util.decrypt(cipherText, base64Key);
    }

    /** 生成 SM2 密钥对 */
    public static Sm2Util.Sm2KeyPair generateSm2KeyPair() {
        return Sm2Util.generateKeyPair();
    }

    /** SM2 公钥加密 */
    public static String sm2Encrypt(String plainText, String base64PublicKey) {
        return Sm2Util.encrypt(plainText, base64PublicKey);
    }

    /** SM2 私钥解密 */
    public static String sm2Decrypt(String cipherText, String base64PrivateKey) {
        return Sm2Util.decrypt(cipherText, base64PrivateKey);
    }
}
