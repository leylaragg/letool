package io.github.leylaragg.letool.thread.exception;

import io.github.leylaragg.letool.exception.core.SystemException;

import java.io.Serial;

/**
 * 线程池配置、注册和生命周期操作失败时抛出的统一异常。
 *
 * <p>异常消息只包含稳定错误码和安全配置字段名，不会暴露业务线程池名称、
 * 任务内容或底层运行环境信息。</p>
 */
public final class ThreadException extends SystemException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建线程模块统一异常。
     *
     * @param errorCode 线程模块稳定错误码
     * @param messageArgs 安全的消息模板参数
     */
    private ThreadException(ThreadErrorCode errorCode, Object[] messageArgs) {
        super(errorCode, messageArgs, null, null);
    }

    /**
     * 创建线程池配置错误。
     *
     * @param field 不合法的安全配置字段名
     * @return 带配置错误码的异常
     * @throws IllegalArgumentException 当字段名为空白时抛出
     */
    public static ThreadException configurationInvalid(String field) {
        return new ThreadException(
                ThreadErrorCode.CONFIGURATION_INVALID,
                new Object[]{requireField(field)}
        );
    }

    /**
     * 创建线程池重复注册错误。
     *
     * @return 不包含业务线程池名称的异常
     */
    public static ThreadException poolAlreadyExists() {
        return new ThreadException(ThreadErrorCode.POOL_ALREADY_EXISTS, null);
    }

    /**
     * 创建线程池不存在错误。
     *
     * @return 不包含业务线程池名称的异常
     */
    public static ThreadException poolNotFound() {
        return new ThreadException(ThreadErrorCode.POOL_NOT_FOUND, null);
    }

    /**
     * 校验可以安全公开的配置字段名。
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
}
