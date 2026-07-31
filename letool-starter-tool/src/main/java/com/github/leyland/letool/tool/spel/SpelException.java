package com.github.leyland.letool.tool.spel;

import com.github.leyland.letool.exception.core.SystemException;

import java.io.Serial;

/**
 * SpEL 表达式解析、求值和类型转换失败时抛出的统一异常。
 *
 * <p>异常消息不会拼接表达式正文或上下文数据，避免表达式中引用的业务字段、
 * 参数值或敏感配置进入对外响应。原始异常保留在原因链中，供受控日志诊断。</p>
 */
public final class SpelException extends SystemException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建 SpEL 工具统一异常。
     *
     * @param errorCode SpEL 稳定错误码
     * @param cause 底层解析或求值异常
     */
    private SpelException(SpelErrorCode errorCode, Throwable cause) {
        super(errorCode, null, null, requireCause(cause));
    }

    /**
     * 创建表达式解析失败异常。
     *
     * @param cause Spring 表达式解析器抛出的原始异常
     * @return 带解析失败错误码的异常
     */
    public static SpelException parseFailed(Throwable cause) {
        return new SpelException(SpelErrorCode.PARSE_FAILED, cause);
    }

    /**
     * 创建表达式求值失败异常。
     *
     * @param cause Spring 表达式求值或类型转换抛出的原始异常
     * @return 带求值失败错误码的异常
     */
    public static SpelException evaluationFailed(Throwable cause) {
        return new SpelException(SpelErrorCode.EVALUATION_FAILED, cause);
    }

    /**
     * 校验必须保留的底层异常。
     *
     * @param cause 底层异常
     * @return 已校验异常
     */
    private static Throwable requireCause(Throwable cause) {
        if (cause == null) {
            throw new IllegalArgumentException("cause must not be null");
        }
        return cause;
    }
}
