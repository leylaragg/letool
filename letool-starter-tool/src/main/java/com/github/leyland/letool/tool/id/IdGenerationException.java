package com.github.leyland.letool.tool.id;

import com.github.leyland.letool.exception.core.SystemException;

import java.io.Serial;

/**
 * ID 参数、Snowflake 节点配置或时间状态不符合生成契约时抛出的统一异常。
 *
 * <p>公开消息不会包含 PID、MAC 地址、系统属性值和实际时间差；
 * 底层原因保留在异常链中供受控诊断。</p>
 */
public final class IdGenerationException extends SystemException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建 ID 生成统一异常。
     *
     * @param errorCode ID 生成稳定错误码
     * @param safeSubject 安全的参数或配置名称
     * @param cause 底层失败原因
     */
    private IdGenerationException(
            IdErrorCode errorCode,
            String safeSubject,
            Throwable cause) {
        super(errorCode, new Object[]{safe(safeSubject)}, null, cause);
    }

    /**
     * 创建 ID 参数无效异常。
     *
     * @param parameterName 安全的参数名称
     * @return ID 参数异常
     */
    public static IdGenerationException invalidArgument(String parameterName) {
        String safeName = safe(parameterName);
        return new IdGenerationException(
                IdErrorCode.INVALID_ARGUMENT,
                safeName,
                new IllegalArgumentException("Invalid ID argument: " + safeName)
        );
    }

    /**
     * 创建 Snowflake 节点配置无效异常。
     *
     * @param configurationName 安全的配置名称
     * @return 节点配置异常
     */
    public static IdGenerationException invalidNodeConfiguration(String configurationName) {
        return invalidNodeConfiguration(
                configurationName,
                new IllegalArgumentException("Invalid Snowflake node configuration")
        );
    }

    /**
     * 创建带底层原因的 Snowflake 节点配置无效异常。
     *
     * @param configurationName 安全的配置名称
     * @param cause 底层解析或校验异常
     * @return 节点配置异常
     */
    public static IdGenerationException invalidNodeConfiguration(
            String configurationName,
            Throwable cause) {
        return new IdGenerationException(
                IdErrorCode.NODE_CONFIGURATION_FAILED,
                configurationName,
                requireCause(cause)
        );
    }

    /**
     * 创建时钟回拨超过容忍范围异常。
     *
     * @param cause 底层时钟状态异常
     * @return 时钟回拨异常
     */
    public static IdGenerationException clockRollback(Throwable cause) {
        return new IdGenerationException(
                IdErrorCode.CLOCK_ROLLBACK,
                "clock",
                requireCause(cause)
        );
    }

    /**
     * 创建时间戳超出 Snowflake 可用范围异常。
     *
     * @param cause 底层时间范围异常
     * @return 时间戳范围异常
     */
    public static IdGenerationException timestampOutOfRange(Throwable cause) {
        return new IdGenerationException(
                IdErrorCode.TIMESTAMP_OUT_OF_RANGE,
                "timestamp",
                requireCause(cause)
        );
    }

    /**
     * 创建 ID 生成等待被中断异常。
     *
     * @param cause 线程中断异常
     * @return 生成中断异常
     */
    public static IdGenerationException generationInterrupted(Throwable cause) {
        return new IdGenerationException(
                IdErrorCode.GENERATION_INTERRUPTED,
                "thread",
                requireCause(cause)
        );
    }

    /**
     * 校验必须保留的底层异常原因。
     *
     * @param cause 底层异常
     * @return 校验通过的异常
     */
    private static Throwable requireCause(Throwable cause) {
        if (cause == null) {
            throw new IllegalArgumentException("cause must not be null");
        }
        return cause;
    }

    /**
     * 规范化公开消息中的安全标识。
     *
     * @param value 待规范化标识
     * @return 非空且非空白的安全标识
     */
    private static String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
