package com.github.leyland.letool.ruleengine.exception;

import com.github.leyland.letool.exception.core.SystemException;

import java.io.Serial;

/**
 * 规则引擎核心统一结构化异常。
 *
 * <p>公开工厂方法只使用稳定错误码和安全参数构建对外消息。
 * 底层异常会通过原因链保留，但其消息不会拼接到当前异常消息中。</p>
 */
public final class RuleEngineException extends SystemException {

    /** 序列化协议版本。 */
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建规则引擎结构化异常。
     *
     * @param errorCode 规则引擎稳定错误码
     * @param messageArgs 仅包含安全值的默认消息参数
     * @param cause 底层原因；没有原因时可为 {@code null}
     */
    private RuleEngineException(
            RuleEngineErrorCode errorCode,
            Object[] messageArgs,
            Throwable cause) {
        super(errorCode, messageArgs, null, cause);
    }

    /**
     * 创建规则源文本超限异常。
     *
     * @return 带稳定限制错误码的异常
     */
    public static RuleEngineException sourceLimitExceeded() {
        return of(RuleEngineErrorCode.SOURCE_LIMIT_EXCEEDED);
    }

    /**
     * 创建词法单元数量超限异常。
     *
     * @return 带稳定限制错误码的异常
     */
    public static RuleEngineException tokenLimitExceeded() {
        return of(RuleEngineErrorCode.TOKEN_LIMIT_EXCEEDED);
    }

    /**
     * 创建抽象语法树深度超限异常。
     *
     * @return 带稳定限制错误码的异常
     */
    public static RuleEngineException astDepthExceeded() {
        return of(RuleEngineErrorCode.AST_DEPTH_EXCEEDED);
    }

    /**
     * 创建函数调用次数超限异常。
     *
     * @return 带稳定限制错误码的异常
     */
    public static RuleEngineException functionCallLimitExceeded() {
        return of(RuleEngineErrorCode.FUNCTION_CALL_LIMIT_EXCEEDED);
    }

    /**
     * 创建组件注册冲突异常。
     *
     * @return 不暴露注册实例细节的异常
     */
    public static RuleEngineException registrationConflict() {
        return of(RuleEngineErrorCode.REGISTRATION_CONFLICT);
    }

    /**
     * 创建调用参数非法异常。
     *
     * <p>该工厂不接收调用方文本，避免把事实值、规则源码或其他敏感信息
     * 拼接到公开异常消息中。</p>
     *
     * @return 不回显调用方文本的结构化异常
     */
    public static RuleEngineException invalidArgument() {
        return of(RuleEngineErrorCode.INVALID_ARGUMENT);
    }

    /**
     * 创建编译失败异常。
     *
     * @param cause 需要保留的非空底层原因
     * @return 保留原因链且不泄漏底层消息的异常
     */
    public static RuleEngineException compilationFailed(Throwable cause) {
        if (cause == null) {
            throw invalidArgument();
        }
        return new RuleEngineException(
                RuleEngineErrorCode.COMPILATION_FAILED,
                null,
                cause);
    }

    /**
     * 创建求值失败异常。
     *
     * @param cause 需要保留的非空底层原因
     * @return 保留原因链且不泄漏底层消息的异常
     */
    public static RuleEngineException evaluationFailed(Throwable cause) {
        if (cause == null) {
            throw invalidArgument();
        }
        return new RuleEngineException(
                RuleEngineErrorCode.EVALUATION_FAILED,
                null,
                cause);
    }

    /**
     * 创建不带底层原因的结构化异常。
     *
     * @param errorCode 规则引擎稳定错误码
     * @return 不带底层原因的异常
     */
    private static RuleEngineException of(RuleEngineErrorCode errorCode) {
        return new RuleEngineException(errorCode, null, null);
    }
}
