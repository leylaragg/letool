package io.github.leylaragg.letool.print.api;

/**
 * 单次渲染使用的不可变额度与输出选项。
 *
 * @author leyland
 */
public final class RenderOptions {

    /** 能确定最终分页的输出格式所允许的最大页数。 */
    private final int maxPages;

    /** 最大允许产物字节数。 */
    private final long maxOutputBytes;

    /** 单次渲染允许占用的最大临时空间。 */
    private final long maxTemporaryBytes;

    /** 是否输出文档元数据。 */
    private final boolean includeDocumentMetadata;

    /** 整份 PDF 允许的文本批注物理总数，0 表示禁止文本批注。 */
    private final int maxAnnotations;

    /** 单次渲染保留的字体二进制缓存上限，0 表示不缓存。 */
    private final long maxFontCacheBytes;

    /** 默认最大页数。 */
    public static final int DEFAULT_MAX_PAGES = 2_500;

    /** 默认最大产物大小：100 MiB。 */
    public static final long DEFAULT_MAX_OUTPUT_BYTES = 100L * 1024 * 1024;

    /** 默认最大临时空间：300 MiB。 */
    public static final long DEFAULT_MAX_TEMPORARY_BYTES = 300L * 1024 * 1024;

    /** 默认最大文本批注数量。 */
    public static final int DEFAULT_MAX_ANNOTATIONS = 1_000;

    /** 默认字体二进制缓存预算：64 MiB。 */
    public static final long DEFAULT_MAX_FONT_CACHE_BYTES = 64L * 1024 * 1024;

    /** 最小产物大小：1 MiB。 */
    private static final long MIN_OUTPUT_BYTES = 1024L * 1024;

    /** 最大产物大小：2 GiB。 */
    private static final long MAX_OUTPUT_BYTES = 2L * 1024 * 1024 * 1024;

    /** 最大临时空间：8 GiB。 */
    private static final long MAX_TEMPORARY_BYTES = 8L * 1024 * 1024 * 1024;

    /**
     * 创建渲染限制。
     *
     * @param maxPages 允许范围为 1 至 20,000
     * @param maxOutputBytes 允许范围为 1 MiB 至 2 GiB
     * @param maxTemporaryBytes 临时空间上限，不得小于产物上限且最多为 8 GiB
     * @param includeDocumentMetadata 是否输出文档元数据
     * @throws IllegalArgumentException 页数、产物或临时空间限制不合理时抛出
     */
    public RenderOptions(int maxPages, long maxOutputBytes, long maxTemporaryBytes,
            boolean includeDocumentMetadata) {
        this(maxPages, maxOutputBytes, maxTemporaryBytes, includeDocumentMetadata, DEFAULT_MAX_ANNOTATIONS);
    }

    /**
     * 创建带文本批注预算的渲染限制。
     *
     * @param maxPages 允许范围为 1 至 20,000
     * @param maxOutputBytes 允许范围为 1 MiB 至 2 GiB
     * @param maxTemporaryBytes 临时空间上限，不得小于产物上限且最多为 8 GiB
     * @param includeDocumentMetadata 是否输出文档元数据
     * @param maxAnnotations 最大文本批注数量，0 表示禁止，不能为负
     * @throws IllegalArgumentException 任一渲染限制不合理时抛出
     */
    public RenderOptions(int maxPages, long maxOutputBytes, long maxTemporaryBytes,
            boolean includeDocumentMetadata, int maxAnnotations) {
        this(maxPages, maxOutputBytes, maxTemporaryBytes, includeDocumentMetadata,
                maxAnnotations, DEFAULT_MAX_FONT_CACHE_BYTES);
    }

    /**
     * 创建带文本批注和字体二进制缓存预算的渲染限制。
     *
     * @param maxPages 允许范围为 1 至 20,000
     * @param maxOutputBytes 允许范围为 1 MiB 至 2 GiB
     * @param maxTemporaryBytes 临时空间上限，不得小于产物上限且最多为 8 GiB
     * @param includeDocumentMetadata 是否输出文档元数据
     * @param maxAnnotations 最大文本批注数量，0 表示禁止，不能为负
     * @param maxFontCacheBytes 保留字体字节的总预算，范围为 0 至 8 GiB，0 表示禁用字节缓存
     * @throws IllegalArgumentException 任一渲染限制不合理时抛出
     */
    public RenderOptions(int maxPages, long maxOutputBytes, long maxTemporaryBytes,
            boolean includeDocumentMetadata, int maxAnnotations, long maxFontCacheBytes) {
        if (maxPages < 1 || maxPages > 20_000) {
            throw new IllegalArgumentException("maxPages 必须在 1 到 20000 之间");
        }
        if (maxOutputBytes < MIN_OUTPUT_BYTES || maxOutputBytes > MAX_OUTPUT_BYTES) {
            throw new IllegalArgumentException("maxOutputBytes 必须在 1 MiB 到 2 GiB 之间");
        }
        if (maxTemporaryBytes < maxOutputBytes || maxTemporaryBytes > MAX_TEMPORARY_BYTES) {
            throw new IllegalArgumentException("maxTemporaryBytes 必须不少于 maxOutputBytes 且不超过 8 GiB");
        }
        if (maxAnnotations < 0) {
            throw new IllegalArgumentException("maxAnnotations 不能为负数，0 表示禁止文本批注");
        }
        if (maxFontCacheBytes < 0 || maxFontCacheBytes > MAX_TEMPORARY_BYTES) {
            throw new IllegalArgumentException("maxFontCacheBytes 必须在 0 到 8 GiB 之间，0 表示禁用字体二进制缓存");
        }
        this.maxPages = maxPages;
        this.maxOutputBytes = maxOutputBytes;
        this.maxTemporaryBytes = maxTemporaryBytes;
        this.includeDocumentMetadata = includeDocumentMetadata;
        this.maxAnnotations = maxAnnotations;
        this.maxFontCacheBytes = maxFontCacheBytes;
    }

    /** @return 能确定最终分页的输出格式所允许的最大页数 */
    public int maxPages() {
        return maxPages;
    }

    /** @return 最大允许产物字节数 */
    public long maxOutputBytes() {
        return maxOutputBytes;
    }

    /** @return 单次渲染允许占用的最大临时空间 */
    public long maxTemporaryBytes() {
        return maxTemporaryBytes;
    }

    /** @return 是否输出文档元数据 */
    public boolean includeDocumentMetadata() {
        return includeDocumentMetadata;
    }

    /** @return 最大文本批注数量，0 表示禁止文本批注 */
    public int maxAnnotations() {
        return maxAnnotations;
    }

    /**
     * 该额度只计入缓存保留的字体源字节。
     *
     * @return 单次渲染的字体字节缓存预算，0 表示禁用字节缓存
     */
    public long maxFontCacheBytes() {
        return maxFontCacheBytes;
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
                && maxTemporaryBytes == that.maxTemporaryBytes
                && includeDocumentMetadata == that.includeDocumentMetadata
                && maxAnnotations == that.maxAnnotations
                && maxFontCacheBytes == that.maxFontCacheBytes;
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(maxPages);
        result = 31 * result + Long.hashCode(maxOutputBytes);
        result = 31 * result + Long.hashCode(maxTemporaryBytes);
        result = 31 * result + Boolean.hashCode(includeDocumentMetadata);
        result = 31 * result + Integer.hashCode(maxAnnotations);
        result = 31 * result + Long.hashCode(maxFontCacheBytes);
        return result;
    }

    @Override
    public String toString() {
        return "RenderOptions[maxPages=" + maxPages
                + ", maxOutputBytes=" + maxOutputBytes
                + ", maxTemporaryBytes=" + maxTemporaryBytes
                + ", includeDocumentMetadata=" + includeDocumentMetadata
                + ", maxAnnotations=" + maxAnnotations
                + ", maxFontCacheBytes=" + maxFontCacheBytes + "]";
    }

    /**
     * 返回适合普通同步打印的默认限制。
     *
     * @return 默认渲染选项
     */
    public static RenderOptions defaults() {
        return new RenderOptions(DEFAULT_MAX_PAGES, DEFAULT_MAX_OUTPUT_BYTES,
                DEFAULT_MAX_TEMPORARY_BYTES, true);
    }
}
