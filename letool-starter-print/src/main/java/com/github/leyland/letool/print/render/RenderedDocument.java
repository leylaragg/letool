package com.github.leyland.letool.print.render;

import com.github.leyland.letool.print.api.OutputFormat;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 文档渲染器返回给 XML 管线的不可变中间输出。
 *
 * @author leyland
 */
public final class RenderedDocument {

    /** 渲染器声明的输出格式。 */
    private final OutputFormat outputFormat;

    /** 渲染输出字节的内部副本。 */
    private final byte[] content;

    /** 渲染阶段产生的安全元数据。 */
    private final Map<String, String> metadata;

    /**
     * 创建渲染器输出。
     *
     * @param outputFormat 输出格式
     * @param content 非空内容
     * @param metadata 安全元数据
     */
    public RenderedDocument(
            OutputFormat outputFormat,
            byte[] content,
            Map<String, String> metadata) {
        this.outputFormat = Objects.requireNonNull(outputFormat, "outputFormat 不能为空");
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("content 不能为空");
        }
        this.content = Arrays.copyOf(content, content.length);
        this.metadata = copyMetadata(metadata);
    }

    /** @return 输出格式 */
    public OutputFormat outputFormat() {
        return outputFormat;
    }

    /** @return 输出内容副本 */
    public byte[] content() {
        return Arrays.copyOf(content, content.length);
    }

    /** @return 不可修改的安全元数据 */
    public Map<String, String> metadata() {
        return metadata;
    }

    /** 复制安全元数据。 */
    private static Map<String, String> copyMetadata(Map<String, String> metadata) {
        Objects.requireNonNull(metadata, "metadata 不能为空");
        Map<String, String> copy = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null) {
                throw new IllegalArgumentException("metadata 不允许空键或空值");
            }
            copy.put(key, value);
        });
        return Map.copyOf(copy);
    }
}
