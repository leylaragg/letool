package com.github.leyland.letool.file.exception;

import com.github.leyland.letool.exception.core.SystemException;

import java.io.Serial;

/**
 * 文件配置、存储、传输和归档操作的统一系统异常。
 *
 * <p>异常只保留稳定错误码、安全参数和底层原因链，不会在对外消息中暴露
 * 本地绝对路径、远程凭据、文件内容或服务器原始响应。</p>
 */
public final class FileException extends SystemException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建文件模块统一异常。
     *
     * @param errorCode 文件错误码
     * @param messageArgs 安全的消息模板参数
     * @param cause 底层异常；没有底层异常时允许为 {@code null}
     */
    private FileException(FileErrorCode errorCode, Object[] messageArgs, Throwable cause) {
        super(errorCode, messageArgs, null, cause);
    }

    /**
     * 创建不包含底层原因的文件异常。
     *
     * @param errorCode 文件错误码
     * @param messageArgs 安全的消息模板参数
     * @return 结构化文件异常
     */
    public static FileException of(FileErrorCode errorCode, Object... messageArgs) {
        return new FileException(requireErrorCode(errorCode), messageArgs, null);
    }

    /**
     * 创建保留底层原因链的文件异常。
     *
     * @param errorCode 文件错误码
     * @param cause 非空底层异常
     * @param messageArgs 安全的消息模板参数
     * @return 保留原因链的结构化文件异常
     */
    public static FileException causedBy(
            FileErrorCode errorCode,
            Throwable cause,
            Object... messageArgs) {
        if (cause == null) {
            throw new IllegalArgumentException("cause 不能为空");
        }
        return new FileException(requireErrorCode(errorCode), messageArgs, cause);
    }

    /**
     * 校验错误码。
     *
     * @param errorCode 待校验错误码
     * @return 已校验错误码
     */
    private static FileErrorCode requireErrorCode(FileErrorCode errorCode) {
        if (errorCode == null) {
            throw new IllegalArgumentException("errorCode 不能为空");
        }
        return errorCode;
    }
}
