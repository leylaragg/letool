package com.github.leyland.letool.cipher.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 加密模块配置属性 —— 对应 application.yml 中 {@code letool.cipher} 前缀.
 */
@ConfigurationProperties(prefix = "letool.cipher")
public class CipherProperties {

    /** 总开关 */
    private boolean enabled = true;

    /** AES 默认密钥大小（位） */
    private int aesDefaultKeySize = 256;

    /** RSA 默认密钥大小（位） */
    private int rsaDefaultKeySize = 2048;

    /** 是否启���国密算法（SM2/SM3/SM4） */
    private boolean smEnabled = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getAesDefaultKeySize() { return aesDefaultKeySize; }
    public void setAesDefaultKeySize(int aesDefaultKeySize) { this.aesDefaultKeySize = aesDefaultKeySize; }
    public int getRsaDefaultKeySize() { return rsaDefaultKeySize; }
    public void setRsaDefaultKeySize(int rsaDefaultKeySize) { this.rsaDefaultKeySize = rsaDefaultKeySize; }
    public boolean isSmEnabled() { return smEnabled; }
    public void setSmEnabled(boolean smEnabled) { this.smEnabled = smEnabled; }
}
