package com.github.leyland.letool.datastructure.exception;

import com.github.leyland.letool.exception.core.SystemException;

import java.io.Serial;

/**
 * 数据结构构建、策略注册和决策执行不符合稳定契约时抛出的统一异常。
 */
public final class DataStructureException extends SystemException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建数据结构统一异常。
     *
     * @param errorCode 数据结构稳定错误码
     * @param subject 可安全公开的参数或能力名称
     * @param cause 底层异常；没有底层异常时可传 {@code null}
     */
    private DataStructureException(
            DataStructureErrorCode errorCode,
            String subject,
            Throwable cause) {
        super(errorCode, new Object[]{safe(subject)}, null, cause);
    }

    /**
     * 创建参数不符合契约异常。
     *
     * @param parameterName 安全的参数名称
     * @return 参数异常
     */
    public static DataStructureException invalidArgument(String parameterName) {
        String safeName = safe(parameterName);
        return new DataStructureException(
                DataStructureErrorCode.INVALID_ARGUMENT,
                safeName,
                new IllegalArgumentException("Invalid data structure argument: " + safeName)
        );
    }

    /**
     * 创建策略键重复注册异常。
     *
     * @param parameterName 安全的策略键参数名称
     * @return 重复注册异常
     */
    public static DataStructureException duplicateStrategyKey(String parameterName) {
        return new DataStructureException(
                DataStructureErrorCode.DUPLICATE_STRATEGY_KEY,
                parameterName,
                null
        );
    }

    /**
     * 创建必需策略不存在异常。
     *
     * @param parameterName 安全的策略键参数名称
     * @return 策略不存在异常
     */
    public static DataStructureException strategyNotFound(String parameterName) {
        return new DataStructureException(
                DataStructureErrorCode.STRATEGY_NOT_FOUND,
                parameterName,
                null
        );
    }

    /**
     * 创建决策链未命中异常。
     *
     * @return 不携带业务上下文内容的决策未命中异常
     */
    public static DataStructureException decisionNotMatched() {
        return new DataStructureException(
                DataStructureErrorCode.DECISION_NOT_MATCHED,
                "decisionChain",
                null
        );
    }

    /**
     * 规范化公开消息中的安全名称。
     *
     * @param value 待规范化名称
     * @return 非空且非空白的安全名称
     */
    private static String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
