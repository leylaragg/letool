package com.github.leyland.letool.cipher.support;

import com.github.leyland.letool.cipher.exception.CipherException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 对称密文版本化封装支持。
 *
 * <p>稳定格式为 {@code LT1.算法.Base64URL随机数.Base64URL密文及认证标签}。</p>
 */
public final class CipherEnvelope {

    /** 首个稳定密文封装版本。 */
    public static final String VERSION = "LT1";

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final int SEGMENT_COUNT = 4;

    /** 工具类禁止实例化。 */
    private CipherEnvelope() {
    }

    /**
     * 将随机数和认证密文编码为稳定字符串。
     *
     * @param algorithm 算法标识
     * @param nonce 随机数
     * @param cipherText 包含认证标签的密文
     * @return 版本化密文封装
     */
    public static String encode(String algorithm, byte[] nonce, byte[] cipherText) {
        requireAlgorithm(algorithm);
        CipherSupport.requireNonNull(nonce, "随机数");
        CipherSupport.requireNonNull(cipherText, "认证密文");
        return VERSION + "." + algorithm + "."
                + ENCODER.encodeToString(nonce) + "."
                + ENCODER.encodeToString(cipherText);
    }

    /**
     * 严格解析并校验密文封装。
     *
     * @param envelope 密文封装
     * @param expectedAlgorithm 预期算法标识
     * @param expectedNonceLength 预期随机数字节长度
     * @param minimumCipherLength 密文最小字节长度
     * @param maximumCipherLength 密文最大字节长度
     * @return 防御性复制后的封装内容
     * @throws CipherException 封装结构、版本、算法或长度无效时抛出
     */
    public static Parsed parse(
            String envelope,
            String expectedAlgorithm,
            int expectedNonceLength,
            int minimumCipherLength,
            int maximumCipherLength) {
        if (envelope == null || envelope.isBlank()) {
            throw CipherException.invalidEnvelope("密文封装不能为空");
        }
        requireAlgorithm(expectedAlgorithm);
        requireLengthBounds(expectedNonceLength, minimumCipherLength, maximumCipherLength);
        int maximumEnvelopeLength = VERSION.length()
                + 1
                + expectedAlgorithm.length()
                + 1
                + base64UrlLength(expectedNonceLength)
                + 1
                + base64UrlLength(maximumCipherLength);
        if (envelope.length() > maximumEnvelopeLength) {
            throw CipherException.invalidEnvelope("密文封装超过内存处理上限");
        }
        String[] segments = envelope.split("\\.", -1);
        if (segments.length != SEGMENT_COUNT) {
            throw CipherException.invalidEnvelope("封装段数不正确");
        }
        if (!VERSION.equals(segments[0])) {
            throw CipherException.invalidEnvelope("不支持的封装版本");
        }
        if (!expectedAlgorithm.equals(segments[1])) {
            throw CipherException.invalidEnvelope("算法标识不匹配");
        }
        if (segments[2].length() != base64UrlLength(expectedNonceLength)
                || segments[3].length() > base64UrlLength(maximumCipherLength)) {
            throw CipherException.invalidEnvelope("密文封装编码长度不正确");
        }
        try {
            byte[] nonce = DECODER.decode(segments[2]);
            byte[] cipherText = DECODER.decode(segments[3]);
            if (nonce.length != expectedNonceLength) {
                throw CipherException.invalidEnvelope("随机数长度不正确");
            }
            if (cipherText.length < minimumCipherLength) {
                throw CipherException.invalidEnvelope("认证密文长度不足");
            }
            if (cipherText.length > maximumCipherLength) {
                throw CipherException.invalidEnvelope("认证密文超过内存处理上限");
            }
            return new Parsed(nonce, cipherText);
        } catch (IllegalArgumentException exception) {
            throw CipherException.invalidEnvelope("Base64URL 编码不正确");
        }
    }

    /**
     * 构造参与 GCM 认证的头部和调用者附加数据。
     *
     * @param algorithm 算法标识
     * @param additionalData 调用者附加认证数据，可为 {@code null}
     * @return UTF-8 编码的认证数据
     */
    public static byte[] authenticatedData(String algorithm, String additionalData) {
        requireAlgorithm(algorithm);
        String value = VERSION + "." + algorithm + "\u0000"
                + (additionalData == null ? "" : additionalData);
        return value.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 校验算法标识仅由大写字母、数字和下划线组成。
     *
     * @param algorithm 算法标识
     */
    private static void requireAlgorithm(String algorithm) {
        if (algorithm == null || !algorithm.matches("[A-Z0-9_]+")) {
            throw CipherException.invalidParameter("算法标识不合法");
        }
    }

    /**
     * 校验封装长度边界。
     *
     * @param expectedNonceLength 预期随机数字节长度
     * @param minimumCipherLength 密文最小字节长度
     * @param maximumCipherLength 密文最大字节长度
     */
    private static void requireLengthBounds(
            int expectedNonceLength,
            int minimumCipherLength,
            int maximumCipherLength) {
        if (expectedNonceLength <= 0
                || minimumCipherLength <= 0
                || maximumCipherLength < minimumCipherLength) {
            throw CipherException.invalidParameter("密文封装长度边界不合法");
        }
    }

    /**
     * 计算无填充 Base64URL 编码的最大字符数。
     *
     * @param byteLength 原始字节长度
     * @return 编码字符数
     */
    private static int base64UrlLength(int byteLength) {
        long encodedLength = ((long) byteLength * 4L + 2L) / 3L;
        if (encodedLength > Integer.MAX_VALUE) {
            throw CipherException.invalidParameter("密文封装长度超过支持范围");
        }
        return (int) encodedLength;
    }

    /**
     * 已解析的密文封装内容。
     *
     * @param nonce 随机数
     * @param cipherText 包含认证标签的密文
     */
    public record Parsed(byte[] nonce, byte[] cipherText) {

        /**
         * 创建防御性复制后的封装内容。
         *
         * @param nonce 随机数
         * @param cipherText 包含认证标签的密文
         */
        public Parsed {
            nonce = nonce.clone();
            cipherText = cipherText.clone();
        }

        /**
         * 获取随机数副本。
         *
         * @return 随机数副本
         */
        @Override
        public byte[] nonce() {
            return nonce.clone();
        }

        /**
         * 获取认证密文副本。
         *
         * @return 认证密文副本
         */
        @Override
        public byte[] cipherText() {
            return cipherText.clone();
        }
    }
}
