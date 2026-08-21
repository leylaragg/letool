package io.github.leylaragg.letool.print.exception;

import io.github.leylaragg.letool.exception.core.SystemException;

import java.io.Serial;
import java.util.Objects;

/**
 * 字体等受控打印资源不能继续使用时抛出的系统异常。
 *
 * <p>公开消息只包含稳定资源类别，底层位置和解析信息留在原因链中。</p>
 *
 * @author leyland
 */
public final class PrintResourceException extends SystemException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 使用安全资源类别和底层原因创建异常。 */
    private PrintResourceException(String resourceType, Throwable cause) {
        super(PrintErrorCode.RESOURCE_UNAVAILABLE,
                new Object[]{requireResourceType(resourceType)}, null,
                Objects.requireNonNull(cause, "cause 不能为空"));
    }

    /**
     * 创建资源不可用异常。
     *
     * @param resourceType 不含路径或业务内容的资源类别
     * @param cause 资源读取或解析失败的底层原因
     * @return 保留安全消息和原因链的资源异常
     */
    public static PrintResourceException unavailable(String resourceType, Throwable cause) {
        return new PrintResourceException(resourceType, cause);
    }

    /** 资源类别会进入公开消息，空文本不能提供可诊断的信息。 */
    private static String requireResourceType(String resourceType) {
        if (resourceType == null || resourceType.isBlank()) {
            throw new IllegalArgumentException("resourceType 不能为空");
        }
        return resourceType;
    }
}
