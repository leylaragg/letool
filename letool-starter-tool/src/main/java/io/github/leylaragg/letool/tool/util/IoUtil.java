package io.github.leylaragg.letool.tool.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Objects;

/**
 * 输入输出流工具，提供流复制和有界读取能力。
 *
 * <p>所有方法都不会关闭调用方传入的流，资源生命周期始终由调用方管理。</p>
 */
public final class IoUtil {

    /** 默认复制缓冲区大小。 */
    private static final int BUFFER_SIZE = 16 * 1024;

    /** 禁止实例化工具类。 */
    private IoUtil() {
    }

    /**
     * 将输入流复制到输出流。
     *
     * @param input  输入流
     * @param output 输出流
     * @return 实际复制的字节数
     * @throws IOException 读取或写入失败时抛出
     */
    public static long copy(InputStream input, OutputStream output) throws IOException {
        Objects.requireNonNull(input, "input 不能为空");
        Objects.requireNonNull(output, "output 不能为空");
        byte[] buffer = new byte[BUFFER_SIZE];
        long copied = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (read > 0) {
                output.write(buffer, 0, read);
                copied += read;
            }
        }
        return copied;
    }

    /**
     * 在最大字节数约束下复制输入流。
     *
     * <p>恰好等于限制时允许完成；发现第一个超额字节时抛出异常，且不会把超额内容写入输出流。</p>
     *
     * @param input    输入流
     * @param output   输出流
     * @param maxBytes 最大允许复制的字节数，允许为零
     * @return 实际复制的字节数
     * @throws IOException 内容超过限制或读写失败时抛出
     */
    public static long copy(InputStream input, OutputStream output, long maxBytes)
            throws IOException {
        Objects.requireNonNull(input, "input 不能为空");
        Objects.requireNonNull(output, "output 不能为空");
        requireNonNegative(maxBytes);
        byte[] buffer = new byte[BUFFER_SIZE];
        long copied = 0;
        int read;
        while ((read = input.read(buffer, 0, limitedReadLength(maxBytes, copied))) != -1) {
            if (read <= 0) {
                continue;
            }
            long remaining = maxBytes - copied;
            if (read > remaining) {
                if (remaining > 0) {
                    output.write(buffer, 0, (int) remaining);
                    copied += remaining;
                }
                throw new IOException("输入内容超过最大允许字节数：" + maxBytes);
            }
            output.write(buffer, 0, read);
            copied += read;
        }
        return copied;
    }

    /**
     * 在最大字节数约束下读取输入流。
     *
     * @param input    输入流
     * @param maxBytes 最大允许字节数，允许为零
     * @return 已读取字节
     * @throws IOException 内容超过限制或读取失败时抛出
     */
    public static byte[] readBytes(InputStream input, long maxBytes) throws IOException {
        Objects.requireNonNull(input, "input 不能为空");
        requireNonNegative(maxBytes);
        ByteArrayOutputStream output = new ByteArrayOutputStream(initialCapacity(maxBytes));
        byte[] buffer = new byte[BUFFER_SIZE];
        long total = 0;
        int read;
        while ((read = input.read(buffer, 0, limitedReadLength(maxBytes, total))) != -1) {
            if (read <= 0) {
                continue;
            }
            total += read;
            if (total > maxBytes) {
                throw new IOException("输入内容超过最大允许字节数：" + maxBytes);
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    /**
     * 在最大字节数约束下读取文本。
     *
     * @param input    输入流
     * @param charset  文本字符集
     * @param maxBytes 最大允许字节数，允许为零
     * @return 解码后的文本
     * @throws IOException 内容超过限制或读取失败时抛出
     */
    public static String readString(InputStream input, Charset charset, long maxBytes)
            throws IOException {
        Objects.requireNonNull(charset, "charset 不能为空");
        return new String(readBytes(input, maxBytes), charset);
    }

    /**
     * 校验字节数限制。
     *
     * @param maxBytes 最大允许字节数
     */
    private static void requireNonNegative(long maxBytes) {
        if (maxBytes < 0) {
            throw new IllegalArgumentException("maxBytes 不能为负数");
        }
    }

    /**
     * 计算有界字节数组输出流的初始容量。
     *
     * @param maxBytes 最大允许字节数
     * @return 不超过默认缓冲区和整数上限的容量
     */
    private static int initialCapacity(long maxBytes) {
        return (int) Math.min(Math.min(maxBytes, BUFFER_SIZE), Integer.MAX_VALUE);
    }

    /**
     * 计算有界操作下一次允许请求的读取长度。
     *
     * <p>请求剩余额度之外的一个字节用于判断超限，但不会提前消费更多上游内容。</p>
     *
     * @param maxBytes 最大允许字节数
     * @param consumed 已消费字节数
     * @return 下一次读取长度
     */
    private static int limitedReadLength(long maxBytes, long consumed) {
        long remaining = maxBytes - consumed;
        return remaining >= BUFFER_SIZE ? BUFFER_SIZE : (int) remaining + 1;
    }
}
