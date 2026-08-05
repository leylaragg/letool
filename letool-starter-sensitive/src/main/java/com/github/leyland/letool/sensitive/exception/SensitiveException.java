package com.github.leyland.letool.sensitive.exception;

import com.github.leyland.letool.exception.core.SystemException;
import com.github.leyland.letool.sensitive.core.SensitiveType;

import java.io.Serial;

/**
 * 脱敏模块统一系统异常。
 *
 * <p>异常只记录脱敏类型和安全原因，不会把待脱敏明文写入消息。</p>
 */
public final class SensitiveException extends SystemException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建脱敏模块异常。
     *
     * @param errorCode 稳定错误码
     * @param messageArgs 消息模板参数
     * @param cause 底层原因，无法取得时可为 {@code null}
     */
    private SensitiveException(SensitiveErrorCode errorCode, Object[] messageArgs, Throwable cause) {
        super(errorCode, messageArgs, null, cause);
    }

    /**
     * 创建配置无效异常。
     *
     * @param reason 不含敏感明文的错误原因
     * @return 配置无效异常
     */
    public static SensitiveException configurationInvalid(String reason) {
        return new SensitiveException(SensitiveErrorCode.CONFIGURATION_INVALID, new Object[]{reason}, null);
    }

    /**
     * 创建策略不存在异常。
     *
     * @param type 未注册的脱敏类型
     * @return 策略不存在异常
     */
    public static SensitiveException strategyNotFound(SensitiveType type) {
        return new SensitiveException(SensitiveErrorCode.STRATEGY_NOT_FOUND, new Object[]{type}, null);
    }

    /**
     * 创建脱敏执行失败异常。
     *
     * @param type 执行失败的脱敏类型
     * @param cause 底层策略异常
     * @return 脱敏执行失败异常
     */
    public static SensitiveException maskFailed(SensitiveType type, Throwable cause) {
        if (cause == null) {
            throw configurationInvalid("底层异常不能为空");
        }
        return new SensitiveException(SensitiveErrorCode.MASK_FAILED, new Object[]{type}, cause);
    }
}
