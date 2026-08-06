package com.github.leyland.letool.cipher.hash;

import com.github.leyland.letool.cipher.exception.CipherException;
import com.github.leyland.letool.cipher.support.CipherSupport;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * MD5 遗留校验和工具。
 *
 * <p>MD5 已不具备抗碰撞安全性，不能用于密码存储、数字签名、安全完整性校验或新协议设计。</p>
 *
 * @deprecated 仅用于必须兼容 MD5 的遗留非安全协议
 */
@Deprecated(forRemoval = false)
public final class Md5Util {

    /** 工具类禁止实例化。 */
    private Md5Util() {
    }

    /**
     * 计算 UTF-8 字符串的 MD5 遗留校验和。
     *
     * @param input 输入字符串
     * @return 小写十六进制校验和
     */
    public static String md5(String input) {
        CipherSupport.requireNonNull(input, "MD5 输入");
        return md5(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算字节数组的 MD5 遗留校验和。
     *
     * @param data 输入字节
     * @return 小写十六进制校验和
     */
    public static String md5(byte[] data) {
        CipherSupport.requireNonNull(data, "MD5 输入");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(data));
        } catch (GeneralSecurityException exception) {
            throw CipherException.operationFailed("MD5 遗留校验和", exception);
        }
    }
}
