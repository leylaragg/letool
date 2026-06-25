package com.github.leyland.letool.cipher.model;

/**
 * 加密模式枚举.
 */
public enum CipherMode {
    /** AES-GCM：认证加密（推荐，防篡改） */
    GCM,
    /** AES-CBC：需要 IV */
    CBC,
    /** RSA-ECB：默认 PKCS1Padding */
    ECB
}
