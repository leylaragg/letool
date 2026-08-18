package io.github.leylaragg.letool.print.service;

import java.util.Objects;

/**
 * 一次同步打印完成后的不可变安全快照。
 *
 * <p>这里只保存运行指标，不持有请求、上下文、模板正文、路径或异常。</p>
 *
 * @author leyland
 */
public final class PrintExecutionSnapshot {

    /** 是否成功生成产物。 */
    private final boolean success;

    /** 输出格式的小写稳定标识。 */
    private final String outputFormat;

    /** 固定失败分类，成功时为 {@link PrintFailureCategory#NONE}。 */
    private final PrintFailureCategory failure;

    /** 完整同步调用耗时。 */
    private final long durationNanos;

    /** 成功产物页数，无法安全解析时为零。 */
    private final int pageCount;

    /** 成功产物实际字节数。 */
    private final long outputBytes;

    /**
     * 保存已经归一化的执行结果。
     *
     * @param success 是否成功
     * @param outputFormat 输出格式标识
     * @param failure 固定失败分类
     * @param durationNanos 调用耗时
     * @param pageCount 产物页数
     * @param outputBytes 产物字节数
     */
    private PrintExecutionSnapshot(boolean success, String outputFormat, PrintFailureCategory failure,
                                   long durationNanos, int pageCount, long outputBytes) {
        this.success = success;
        this.outputFormat = requireOutputFormat(outputFormat);
        this.failure = Objects.requireNonNull(failure, "failure 不能为空");
        if (durationNanos < 0 || pageCount < 0 || outputBytes < 0) {
            throw new IllegalArgumentException("打印观测数值不能为负数");
        }
        if (success != (failure == PrintFailureCategory.NONE)) {
            throw new IllegalArgumentException("打印结果与失败分类不一致");
        }
        this.durationNanos = durationNanos;
        this.pageCount = pageCount;
        this.outputBytes = outputBytes;
    }

    /**
     * 创建成功快照。
     *
     * @param outputFormat 输出格式标识
     * @param durationNanos 完整调用耗时
     * @param pageCount 产物页数
     * @param outputBytes 产物字节数
     * @return 成功执行快照
     */
    public static PrintExecutionSnapshot success(
            String outputFormat, long durationNanos, int pageCount, long outputBytes) {
        return new PrintExecutionSnapshot(true, outputFormat, PrintFailureCategory.NONE,
                durationNanos, pageCount, outputBytes);
    }

    /**
     * 创建失败快照。
     *
     * @param outputFormat 目标输出格式标识
     * @param failure 固定失败分类
     * @param durationNanos 失败前已经消耗的时间
     * @return 不携带异常对象或消息的失败快照
     */
    public static PrintExecutionSnapshot failure(
            String outputFormat, PrintFailureCategory failure, long durationNanos) {
        if (failure == PrintFailureCategory.NONE) {
            throw new IllegalArgumentException("失败快照必须提供失败分类");
        }
        return new PrintExecutionSnapshot(false, outputFormat, failure, durationNanos, 0, 0);
    }

    /** @return 是否成功 */
    public boolean success() {
        return success;
    }

    /** @return 输出格式的小写标识 */
    public String outputFormat() {
        return outputFormat;
    }

    /** @return 固定失败分类 */
    public PrintFailureCategory failure() {
        return failure;
    }

    /** @return 完整调用耗时，单位纳秒 */
    public long durationNanos() {
        return durationNanos;
    }

    /** @return 产物页数 */
    public int pageCount() {
        return pageCount;
    }

    /** @return 产物字节数 */
    public long outputBytes() {
        return outputBytes;
    }

    /** @return 只包含安全观测字段的文本表示 */
    @Override
    public String toString() {
        return "PrintExecutionSnapshot[success=" + success
                + ", outputFormat=" + outputFormat
                + ", failure=" + failure.value()
                + ", durationNanos=" + durationNanos
                + ", pageCount=" + pageCount
                + ", outputBytes=" + outputBytes + "]";
    }

    /**
     * 校验输出格式只保留规范化标识。
     *
     * @param outputFormat 待校验的格式标识
     * @return 可安全用作指标标签的标识
     */
    private static String requireOutputFormat(String outputFormat) {
        if (outputFormat == null || !outputFormat.matches("[a-z][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("outputFormat 格式不合法");
        }
        return outputFormat;
    }
}
