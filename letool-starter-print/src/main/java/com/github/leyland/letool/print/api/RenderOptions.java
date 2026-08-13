package com.github.leyland.letool.print.api;

/**
 * 与具体输出实现无关的渲染限制。
 *
 * @author leyland
 */
public final class RenderOptions {

    /** 最大允许页数。 */
    private final int maxPages;

    /** 最大允许产物字节数。 */
    private final long maxOutputBytes;

    /** 是否输出文档元数据。 */
    private final boolean includeDocumentMetadata;

    /** 默认最大页数。 */
    public static final int DEFAULT_MAX_PAGES = 2_500;

    /** 默认最大产物大小：100 MiB。 */
    public static final long DEFAULT_MAX_OUTPUT_BYTES = 100L * 1024 * 1024;

    /** 最小产物大小：1 MiB。 */
    private static final long MIN_OUTPUT_BYTES = 1024L * 1024;

    /** 最大产物大小：2 GiB。 */
    private static final long MAX_OUTPUT_BYTES = 2L * 1024 * 1024 * 1024;

    /**
     * 创建渲染限制。
     *
     * @param maxPages 允许范围为 1 至 20,000
     * @param maxOutputBytes 允许范围为 1 MiB 至 2 GiB
     * @param includeDocumentMetadata 是否输出文档元数据
     * @throws IllegalArgumentException 页数或字节限制越界时抛出
     */
    public RenderOptions(int maxPages, long maxOutputBytes, boolean includeDocumentMetadata) {
        if (maxPages < 1 || maxPages > 20_000) {
            throw new IllegalArgumentException("maxPages 必须在 1 到 20000 之间");
        }
        if (maxOutputBytes < MIN_OUTPUT_BYTES || maxOutputBytes > MAX_OUTPUT_BYTES) {
            throw new IllegalArgumentException("maxOutputBytes 必须在 1 MiB 到 2 GiB 之间");
        }
        this.maxPages = maxPages;
        this.maxOutputBytes = maxOutputBytes;
        this.includeDocumentMetadata = includeDocumentMetadata;
    }

    /** @return 最大允许页数 */
    public int maxPages() {
        return maxPages;
    }

    /** @return 最大允许产物字节数 */
    public long maxOutputBytes() {
        return maxOutputBytes;
    }

    /** @return 是否输出文档元数据 */
    public boolean includeDocumentMetadata() {
        return includeDocumentMetadata;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof RenderOptions that)) {
            return false;
        }
        return maxPages == that.maxPages
                && maxOutputBytes == that.maxOutputBytes
                && includeDocumentMetadata == that.includeDocumentMetadata;
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(maxPages);
        result = 31 * result + Long.hashCode(maxOutputBytes);
        result = 31 * result + Boolean.hashCode(includeDocumentMetadata);
        return result;
    }

    @Override
    public String toString() {
        return "RenderOptions[maxPages=" + maxPages
                + ", maxOutputBytes=" + maxOutputBytes
                + ", includeDocumentMetadata=" + includeDocumentMetadata + "]";
    }

    /**
     * 返回适合普通同步打印的默认限制。
     *
     * @return 默认渲染选项
     */
    public static RenderOptions defaults() {
        return new RenderOptions(DEFAULT_MAX_PAGES, DEFAULT_MAX_OUTPUT_BYTES, true);
    }
}
