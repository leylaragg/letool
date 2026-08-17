package io.github.leylaragg.letool.file.range;

import org.springframework.http.HttpRange;

import java.util.List;

/**
 * 已根据完整资源长度解析并校验的单字节区间。
 *
 * @param start 起始位置，包含该字节
 * @param end 结束位置，包含该字节
 * @param length 区间字节数
 */
public record ByteRange(long start, long end, long length) {

    /**
     * 校验区间边界关系。
     *
     * @param start 起始位置
     * @param end 结束位置
     * @param length 区间字节数
     */
    public ByteRange {
        if (start < 0 || end < start || length <= 0 || length != end - start + 1) {
            throw new IllegalArgumentException("字节区间不合法");
        }
    }

    /**
     * 使用 Spring Range 解析器解析单区间请求。
     *
     * @param rangeHeader HTTP Range 请求头
     * @param resourceLength 完整资源长度
     * @return 已校验单区间
     * @throws IllegalArgumentException 请求头为空、包含多个区间或越界时抛出
     */
    public static ByteRange parse(String rangeHeader, long resourceLength) {
        if (rangeHeader == null || rangeHeader.isBlank() || resourceLength <= 0) {
            throw new IllegalArgumentException("Range 请求不合法");
        }
        List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
        if (ranges.size() != 1) {
            throw new IllegalArgumentException("只支持单区间 Range");
        }
        HttpRange range = ranges.get(0);
        long start = range.getRangeStart(resourceLength);
        long end = range.getRangeEnd(resourceLength);
        if (start < 0 || start >= resourceLength || end < start || end >= resourceLength) {
            throw new IllegalArgumentException("Range 超出资源边界");
        }
        return new ByteRange(start, end, end - start + 1);
    }
}
