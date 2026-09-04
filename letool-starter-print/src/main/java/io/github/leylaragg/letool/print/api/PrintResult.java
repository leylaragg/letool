package io.github.leylaragg.letool.print.api;

import java.util.Map;
import java.util.Objects;

/**
 * 流式打印完成后返回的不可变结果。
 *
 * <p>结果只保存安全元数据，不持有已经写给调用方的产物内容。</p>
 *
 * @author leyland
 */
public final class PrintResult {

    /** 实际写出的产物格式。 */
    private final OutputFormat outputFormat;

    /** 实际写出的字节数。 */
    private final long contentLength;

    /** 实际内容的小写十六进制 SHA-256。 */
    private final String sha256;

    /** 渲染器返回的安全元数据。 */
    private final Map<String, String> metadata;

    /**
     * 保存由当前输出流计算并校验的结果。
     *
     * @param outputFormat 实际产物格式
     * @param contentLength 实际写出字节数
     * @param sha256 实际内容摘要
     * @param metadata 已完成防御性复制的安全元数据
     */
    PrintResult(OutputFormat outputFormat, long contentLength, String sha256,
                Map<String, String> metadata) {
        this.outputFormat = Objects.requireNonNull(outputFormat, "outputFormat 不能为空");
        if (contentLength < 1) {
            throw new IllegalArgumentException("contentLength 必须大于零");
        }
        if (sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 格式不合法");
        }
        this.contentLength = contentLength;
        this.sha256 = sha256;
        this.metadata = Objects.requireNonNull(metadata, "metadata 不能为空");
    }

    /** @return 实际产物格式 */
    public OutputFormat outputFormat() {
        return outputFormat;
    }

    /** @return 实际写出字节数 */
    public long contentLength() {
        return contentLength;
    }

    /** @return 实际内容的小写十六进制 SHA-256 */
    public String sha256() {
        return sha256;
    }

    /** @return 不可修改的安全元数据 */
    public Map<String, String> metadata() {
        return metadata;
    }

}
