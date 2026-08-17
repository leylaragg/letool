package io.github.leylaragg.letool.file.compress;

/**
 * ZIP 解压使用的不可变安全限制。
 *
 * @param maxEntries 最大条目数量
 * @param maxEntrySize 单条目最大实际解压字节数
 * @param maxTotalSize 全部条目最大实际解压字节数
 */
public record ZipLimits(int maxEntries, long maxEntrySize, long maxTotalSize) {

    /**
     * 校验所有安全限制均为正数，且总量不小于单条目上限。
     *
     * @param maxEntries 最大条目数量
     * @param maxEntrySize 单条目最大实际解压字节数
     * @param maxTotalSize 全部条目最大实际解压字节数
     */
    public ZipLimits {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries 必须大于 0");
        }
        if (maxEntrySize <= 0) {
            throw new IllegalArgumentException("maxEntrySize 必须大于 0");
        }
        if (maxTotalSize < maxEntrySize) {
            throw new IllegalArgumentException("maxTotalSize 不能小于 maxEntrySize");
        }
    }

    /**
     * 获取适合普通业务文件的安全默认值。
     *
     * @return 一万条目、单条目 100 MiB、总量 1 GiB 的限制
     */
    public static ZipLimits defaults() {
        return new ZipLimits(10_000, 100L * 1024 * 1024, 1024L * 1024 * 1024);
    }
}
