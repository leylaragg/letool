package io.github.leylaragg.letool.print.api;

import io.github.leylaragg.letool.print.exception.PrintOutputException;
import io.github.leylaragg.letool.print.exception.PrintRenderingException;

import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

/**
 * 为单次打印限制容量并计算摘要的调用方输出包装器。
 *
 * <p>框架拥有包装器状态，底层流始终由调用方管理。实例只能完成一次，也不能在失败后继续写入。</p>
 *
 * @author leyland
 */
public final class PrintOutput extends OutputStream {

    /** 调用方提供的实际输出目标。 */
    private final OutputStream target;

    /** 本次最终产物允许的最大字节数。 */
    private final long maxBytes;

    /** 随成功写入持续更新的摘要计算器。 */
    private final MessageDigest digest;

    /** 已经成功写给调用方的字节数。 */
    private long size;

    /** 写入或容量故障后阻止继续使用。 */
    private boolean failed;

    /** 完成后冻结的唯一结果。 */
    private PrintResult completed;

    /**
     * 创建受最终产物容量约束的输出包装器。
     *
     * @param target 调用方拥有的输出流
     * @param maxBytes 允许写出的最大字节数
     * @throws IllegalArgumentException 最大字节数不是正数时抛出
     * @throws NullPointerException 输出流为空时抛出
     */
    public PrintOutput(OutputStream target, long maxBytes) {
        this.target = Objects.requireNonNull(target, "target 不能为空");
        if (maxBytes < 1) {
            throw new IllegalArgumentException("maxBytes 必须大于零");
        }
        this.maxBytes = maxBytes;
        this.digest = sha256Digest();
    }

    /**
     * 写入一个字节，并在调用底层流前检查容量。
     *
     * @param value 待写入字节的低八位
     */
    @Override
    public void write(int value) {
        requireWritable();
        reserve(1);
        try {
            target.write(value);
            digest.update((byte) value);
            size++;
        } catch (IOException exception) {
            fail();
            throw PrintOutputException.writeFailed(exception);
        }
    }

    /**
     * 写入完整字节数组，调用方无需处理已经转换过的 IO 异常。
     *
     * @param source 待写入内容
     */
    @Override
    public void write(byte[] source) {
        Objects.requireNonNull(source, "source 不能为空");
        write(source, 0, source.length);
    }

    /**
     * 写入完整字节片段，容量越界时不会向调用方写入本批内容。
     *
     * @param source 待写入内容
     * @param offset 起始偏移
     * @param length 写入长度
     */
    @Override
    public void write(byte[] source, int offset, int length) {
        Objects.checkFromIndexSize(offset, length, source.length);
        requireWritable();
        reserve(length);
        try {
            target.write(source, offset, length);
            digest.update(source, offset, length);
            size += length;
        } catch (IOException exception) {
            fail();
            throw PrintOutputException.writeFailed(exception);
        }
    }

    /**
     * 把已经写出的内容冻结为结果。
     *
     * @param outputFormat 实际产物格式
     * @param metadata 不包含业务正文的安全元数据
     * @return 当前输出生成的唯一结果
     */
    public PrintResult complete(OutputFormat outputFormat, Map<String, String> metadata) {
        requireWritable();
        Objects.requireNonNull(outputFormat, "outputFormat 不能为空");
        Map<String, String> safeMetadata = PrintMetadata.copyOf(metadata);
        if (size == 0) {
            throw new IllegalStateException("打印输出不能为空");
        }
        flushTarget();
        completed = new PrintResult(outputFormat, size,
                HexFormat.of().formatHex(digest.digest()), safeMetadata);
        return completed;
    }

    /**
     * 判断结果是否确实由当前输出完成，供打印引擎拦截伪造或串用结果。
     *
     * @param result 管线返回的结果
     * @return 是否为当前输出生成的同一结果
     */
    public boolean completedWith(PrintResult result) {
        return completed != null && completed == result;
    }

    /**
     * 刷新调用方流，但不改变当前输出是否已经完成。
     */
    @Override
    public void flush() {
        requireWritable();
        flushTarget();
    }

    /**
     * 包装器关闭时不关闭调用方流，资源生命周期仍由调用方决定。
     */
    @Override
    public void close() {
        // 这里只结束包装器的使用位置，底层流可能还承载调用方的其他内容。
    }

    /** 在底层状态改变前预留本次写入容量。 */
    private void reserve(int length) {
        if (length > maxBytes - size) {
            fail();
            throw PrintRenderingException.outputLimitExceeded(
                    maxBytes, new IOException("打印输出容量越界"));
        }
    }

    /** 把刷新故障转换为稳定的输出异常。 */
    private void flushTarget() {
        try {
            target.flush();
        } catch (IOException exception) {
            fail();
            throw PrintOutputException.writeFailed(exception);
        }
    }

    /** 拒绝完成后或失败后的后续操作。 */
    private void requireWritable() {
        if (completed != null) {
            throw new IllegalStateException("打印输出已经完成");
        }
        if (failed) {
            throw new IllegalStateException("打印输出已经失败");
        }
    }

    /** 标记当前输出已经不能安全继续。 */
    private void fail() {
        failed = true;
    }

    /** 创建 JDK 必须提供的 SHA-256 计算器。 */
    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }
}
