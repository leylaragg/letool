package io.github.leylaragg.letool.print.render;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 写入前检查总量的分段渲染输出缓冲区。
 *
 * <p>缓冲区按固定分段保存内容，避免扩容时反复复制已经生成的产物。</p>
 *
 * @author leyland
 */
public final class BoundedRenderOutput extends OutputStream {

    /** 单个内存分段大小。 */
    private static final int SEGMENT_SIZE = 16 * 1024;

    /** Java 数组可以安全分配的最大长度。 */
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    /** 本次渲染允许的最大输出字节数。 */
    private final long maxBytes;

    /** 按写入顺序保存的内存分段。 */
    private final List<byte[]> segments = new ArrayList<>();

    /** 当前分段中的下一个写入位置。 */
    private int segmentOffset;

    /** 已经接收的总字节数。 */
    private long size;

    /**
     * 创建受容量约束的渲染输出。
     *
     * @param maxBytes 本次渲染允许的最大输出字节数
     */
    public BoundedRenderOutput(long maxBytes) {
        if (maxBytes < 1) {
            throw new IllegalArgumentException("maxBytes 必须大于零");
        }
        this.maxBytes = maxBytes;
    }

    /** 写入一个字节，并在分配新分段前检查上限。 */
    @Override
    public void write(int value) throws IOException {
        reserve(1);
        writableSegment()[segmentOffset++] = (byte) value;
        size++;
    }

    /** 写入一段字节，并保证越界失败不会留下部分内容。 */
    @Override
    public void write(byte[] source, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, source.length);
        reserve(length);
        int remaining = length;
        int sourceOffset = offset;
        while (remaining > 0) {
            byte[] segment = writableSegment();
            int writable = Math.min(remaining, segment.length - segmentOffset);
            System.arraycopy(source, sourceOffset, segment, segmentOffset, writable);
            segmentOffset += writable;
            sourceOffset += writable;
            remaining -= writable;
            size += writable;
        }
    }

    /** @return 当前已经写入的字节数 */
    public long size() {
        return size;
    }

    /** @return 当前分段实际分配的总字节数 */
    long allocatedBytes() {
        return segments.stream().mapToLong(segment -> segment.length).sum();
    }

    /**
     * 合并所有分段并返回新的字节数组。
     *
     * @return 渲染产物内容副本
     * @throws OutputLimitExceededException 内容无法装入 Java 字节数组时抛出
     */
    public byte[] toByteArray() throws OutputLimitExceededException {
        if (size > MAX_ARRAY_SIZE) {
            throw new OutputLimitExceededException();
        }
        byte[] content = new byte[(int) size];
        int targetOffset = 0;
        long remaining = size;
        for (byte[] segment : segments) {
            int copied = (int) Math.min(segment.length, remaining);
            System.arraycopy(segment, 0, content, targetOffset, copied);
            targetOffset += copied;
            remaining -= copied;
            if (remaining == 0) {
                break;
            }
        }
        return content;
    }

    /** 在修改缓冲区前验证本次内容可以完整写入。 */
    private void reserve(int length) throws OutputLimitExceededException {
        if (length > maxBytes - size) {
            throw new OutputLimitExceededException();
        }
    }

    /** 返回有剩余空间的分段，必要时按剩余容量创建新分段。 */
    private byte[] writableSegment() {
        if (segments.isEmpty() || segmentOffset == segments.get(segments.size() - 1).length) {
            int capacity = (int) Math.min(SEGMENT_SIZE, maxBytes - size);
            segments.add(new byte[capacity]);
            segmentOffset = 0;
        }
        return segments.get(segments.size() - 1);
    }

    /** 渲染输出超过容量限制时使用的稳定内部信号。 */
    public static final class OutputLimitExceededException extends IOException {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 创建不携带输出内容的容量异常。 */
        private OutputLimitExceededException() {
            super("渲染输出超过容量限制");
        }
    }
}
