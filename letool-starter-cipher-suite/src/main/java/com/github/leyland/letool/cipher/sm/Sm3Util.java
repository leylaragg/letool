package com.github.leyland.letool.cipher.sm;

import com.github.leyland.letool.cipher.exception.CipherException;
import com.github.leyland.letool.cipher.support.BouncyCastleSupport;
import com.github.leyland.letool.cipher.support.CipherSupport;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * SM3 摘要工具。
 */
public final class Sm3Util {

    /** 工具类禁止实例化。 */
    private Sm3Util() {
    }

    /**
     * 计算 UTF-8 字符串的 SM3 摘要。
     *
     * @param input 输入字符串
     * @return 小写十六进制摘要
     */
    public static String sm3(String input) {
        CipherSupport.requireNonNull(input, "SM3 输入");
        return sm3(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算字节数组的 SM3 摘要。
     *
     * @param data 输入字节
     * @return 小写十六进制摘要
     */
    public static String sm3(byte[] data) {
        CipherSupport.requireNonNull(data, "SM3 输入");
        try {
            MessageDigest digest = MessageDigest.getInstance(
                    "SM3",
                    BouncyCastleSupport.provider());
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (GeneralSecurityException exception) {
            throw CipherException.operationFailed("SM3 摘要", exception);
        }
    }
}
