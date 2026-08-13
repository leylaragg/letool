package com.github.leyland.letool.tool.util;

import com.github.leyland.letool.tool.encoding.EncodingOperationException;
import com.github.leyland.letool.tool.model.DigestCopyResult;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * SHA-256 摘要工具，支持字节、文本、流和文件路径。
 *
 * <p>流相关方法不会关闭调用方传入的流。SHA-256 是 Java 运行环境必须支持的算法，
 * 因此算法不可用会被视为运行环境异常。</p>
 */
public final class DigestUtil {

    /** SHA-256 算法名称。 */
    private static final String SHA_256 = "SHA-256";

    /** 默认摘要缓冲区大小。 */
    private static final int BUFFER_SIZE = 16 * 1024;

    /** 禁止实例化工具类。 */
    private DigestUtil() {
    }

    /**
     * 计算 UTF-8 文本的 SHA-256。
     *
     * @param text 输入文本
     * @return 小写十六进制摘要
     */
    public static String sha256(String text) {
        Objects.requireNonNull(text, "text 不能为空");
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算字节数组的 SHA-256。
     *
     * @param data 输入字节
     * @return 小写十六进制摘要
     */
    public static String sha256(byte[] data) {
        Objects.requireNonNull(data, "data 不能为空");
        return HexUtil.encodeHex(newSha256().digest(data));
    }

    /**
     * 流式计算输入流的 SHA-256。
     *
     * @param input 输入流，由调用方关闭
     * @return 小写十六进制摘要
     * @throws IOException 读取失败时抛出
     */
    public static String sha256(InputStream input) throws IOException {
        Objects.requireNonNull(input, "input 不能为空");
        MessageDigest digest = newSha256();
        updateDigest(input, digest);
        return HexUtil.encodeHex(digest.digest());
    }

    /**
     * 计算文件内容的 SHA-256。
     *
     * @param path 文件路径
     * @return 小写十六进制摘要
     * @throws IOException 打开或读取文件失败时抛出
     */
    public static String sha256(Path path) throws IOException {
        Objects.requireNonNull(path, "path 不能为空");
        try (InputStream input = Files.newInputStream(path)) {
            return sha256(input);
        }
    }

    /**
     * 在一次读取中复制内容并计算 SHA-256。
     *
     * @param input  输入流，由调用方关闭
     * @param output 输出流，由调用方关闭
     * @return 复制字节数与摘要
     * @throws IOException 读取或写入失败时抛出
     */
    public static DigestCopyResult copyAndSha256(InputStream input, OutputStream output)
            throws IOException {
        Objects.requireNonNull(input, "input 不能为空");
        Objects.requireNonNull(output, "output 不能为空");
        MessageDigest digest = newSha256();
        byte[] buffer = new byte[BUFFER_SIZE];
        long copied = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (read > 0) {
                output.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                copied += read;
            }
        }
        return new DigestCopyResult(copied, HexUtil.encodeHex(digest.digest()));
    }

    /**
     * 以常量时间比较两个 SHA-256 十六进制摘要。
     *
     * @param expected 期望摘要
     * @param actual   实际摘要
     * @return 两个合法摘要是否相等
     */
    public static boolean matchesSha256(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        try {
            byte[] expectedBytes = HexUtil.decodeHex(expected);
            byte[] actualBytes = HexUtil.decodeHex(actual);
            return expectedBytes.length == 32
                    && actualBytes.length == 32
                    && MessageDigest.isEqual(expectedBytes, actualBytes);
        } catch (EncodingOperationException exception) {
            return false;
        }
    }

    /**
     * 从输入流更新摘要状态。
     *
     * @param input  输入流
     * @param digest 摘要器
     * @throws IOException 读取失败时抛出
     */
    private static void updateDigest(InputStream input, MessageDigest digest) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (read > 0) {
                digest.update(buffer, 0, read);
            }
        }
    }

    /**
     * 创建 SHA-256 摘要器。
     *
     * @return 新摘要器
     */
    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance(SHA_256);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 Java 运行环境不支持 SHA-256", exception);
        }
    }
}
