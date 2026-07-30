package com.github.leyland.letool.mail.exception;

import com.github.leyland.letool.exception.core.SystemException;

import java.io.Serial;

/**
 * 邮件配置、请求和投递发生故障时抛出的统一异常。
 *
 * <p>异常仅保留稳定错误码、安全字段名和底层原因链，不会把邮件主题、
 * 收件人、SMTP 地址、凭据或底层实现消息拼接到对外消息中。</p>
 */
public final class MailException extends SystemException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建邮件模块统一异常。
     *
     * @param errorCode 邮件稳定错误码
     * @param messageArgs 安全的消息模板参数
     * @param cause 底层异常；没有底层异常时允许为 {@code null}
     */
    private MailException(
            MailErrorCode errorCode,
            Object[] messageArgs,
            Throwable cause) {
        super(errorCode, messageArgs, null, cause);
    }

    /**
     * 创建邮件配置错误。
     *
     * @param field 不合法的安全配置字段名
     * @return 带配置错误码的异常
     * @throws IllegalArgumentException 当字段名为空白时抛出
     */
    public static MailException configurationInvalid(String field) {
        return new MailException(
                MailErrorCode.CONFIGURATION_INVALID,
                new Object[]{requireField(field)},
                null
        );
    }

    /**
     * 创建邮件请求错误。
     *
     * @param field 不合法的安全请求字段名
     * @return 带请求错误码的异常
     * @throws IllegalArgumentException 当字段名为空白时抛出
     */
    public static MailException requestInvalid(String field) {
        return new MailException(
                MailErrorCode.REQUEST_INVALID,
                new Object[]{requireField(field)},
                null
        );
    }

    /**
     * 创建邮件投递失败异常。
     *
     * @param cause 底层构造、连接或投递异常
     * @return 带投递错误码和原始原因链的异常
     * @throws IllegalArgumentException 当原因为 {@code null} 时抛出
     */
    public static MailException deliveryFailed(Throwable cause) {
        return new MailException(
                MailErrorCode.DELIVERY_FAILED,
                null,
                requireCause(cause)
        );
    }

    /**
     * 创建异步执行器不可用异常。
     *
     * @param cause 底层任务拒绝异常
     * @return 带异步错误码和原始原因链的异常
     * @throws IllegalArgumentException 当原因为 {@code null} 时抛出
     */
    public static MailException asyncUnavailable(Throwable cause) {
        return new MailException(
                MailErrorCode.ASYNC_UNAVAILABLE,
                null,
                requireCause(cause)
        );
    }

    /**
     * 校验可以安全公开的字段名。
     *
     * @param field 待校验字段名
     * @return 已校验字段名
     */
    private static String requireField(String field) {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("field must not be blank");
        }
        return field;
    }

    /**
     * 校验需要保留的底层异常。
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
