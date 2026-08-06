package com.github.leyland.letool.file.storage;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * 只向调用方暴露指定剩余字节数的输入流。
 */
final class RangeInputStream extends FilterInputStream {

    private long remaining;

    /**
     * 创建有界区间流。
     *
     * @param inputStream 已定位到区间起点的输入流
     * @param length 可读取字节数
     */
    RangeInputStream(InputStream inputStream, long length) {
        super(inputStream);
        if (length <= 0) {
            throw new IllegalArgumentException("length 必须大于 0");
        }
        this.remaining = length;
    }

    @Override
    public int read() throws IOException {
        if (remaining == 0) {
            return -1;
        }
        int value = super.read();
        if (value >= 0) {
            remaining--;
        }
        return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, buffer.length);
        if (length == 0) {
            return 0;
        }
        if (remaining == 0) {
            return -1;
        }
        int boundedLength = (int) Math.min(length, remaining);
        int read = super.read(buffer, offset, boundedLength);
        if (read > 0) {
            remaining -= read;
        }
        return read;
    }

    /**
     * 跳过区间内的有限字节，不能越过区间终点。
     *
     * @param count 希望跳过的字节数
     * @return 实际跳过字节数
     * @throws IOException 底层流跳过失败时抛出
     */
    @Override
    public long skip(long count) throws IOException {
        if (count <= 0 || remaining == 0) {
            return 0;
        }
        long skipped = super.skip(Math.min(count, remaining));
        remaining -= skipped;
        return skipped;
    }

    /**
     * 返回当前无需阻塞可读取且仍位于区间内的字节数。
     *
     * @return 可立即读取字节数
     * @throws IOException 查询底层流失败时抛出
     */
    @Override
    public int available() throws IOException {
        return (int) Math.min(super.available(), Math.min(remaining, Integer.MAX_VALUE));
    }

    /**
     * 区间流不支持标记，避免重置后破坏剩余字节边界。
     *
     * @return 固定返回 {@code false}
     */
    @Override
    public boolean markSupported() {
        return false;
    }

    /**
     * 明确拒绝重置区间流。
     *
     * @throws IOException 始终抛出不支持重置异常
     */
    @Override
    public void reset() throws IOException {
        throw new IOException("区间输入流不支持重置");
    }
}
