package io.github.leylaragg.letool.rule.exception;

import io.github.leylaragg.letool.exception.core.SystemException;

import java.io.Serial;

/**
 * 规则链执行过程中的统一系统异常。
 *
 * <p>异常只暴露 Letool 稳定错误码、规则链标识和底层原因，不向调用方透传
 * LiteFlow 响应中的内部错误消息。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public final class RuleException extends SystemException {

    /** Java 序列化版本标识。 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 发生执行失败的规则链标识；参数校验异常没有规则链标识。 */
    private final String chainId;

    /**
     * 创建规则模块统一异常。
     *
     * @param errorCode 规则模块错误码
     * @param messageArgs 用于格式化默认消息和延迟国际化的参数
     * @param chainId 发生执行失败的规则链标识；参数校验异常可为 {@code null}
     * @param cause LiteFlow 返回或抛出的底层原因，可以为 {@code null}
     */
    private RuleException(
            RuleErrorCode errorCode,
            Object[] messageArgs,
            String chainId,
            Throwable cause) {
        super(errorCode, messageArgs, null, cause);
        this.chainId = chainId;
    }

    /**
     * 创建规则链标识无效异常。
     *
     * @return 不携带规则链标识和底层原因的参数校验异常
     */
    public static RuleException invalidChainId() {
        return new RuleException(
                RuleErrorCode.CHAIN_ID_INVALID,
                new Object[0],
                null,
                null
        );
    }

    /**
     * 创建规则链执行失败异常。
     *
     * @param chainId 非空白规则链标识
     * @param cause LiteFlow 返回或抛出的底层原因，可以为 {@code null}
     * @return 保留规则链标识和底层原因的执行失败异常
     * @throws IllegalArgumentException 当规则链标识为 {@code null} 或空白时抛出
     */
    public static RuleException executionFailed(String chainId, Throwable cause) {
        if (chainId == null || chainId.isBlank()) {
            throw new IllegalArgumentException("chainId must not be blank");
        }
        return new RuleException(
                RuleErrorCode.EXECUTION_FAILED,
                new Object[]{chainId},
                chainId,
                cause
        );
    }

    /**
     * 获取发生执行失败的规则链标识。
     *
     * @return 规则链标识；参数校验异常返回 {@code null}
     */
    public String getChainId() {
        return chainId;
    }
}
